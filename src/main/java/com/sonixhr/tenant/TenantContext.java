package com.sonixhr.tenant;

import com.sonixhr.dto.ApiResponse;
import com.sonixhr.entity.Permission;
import com.sonixhr.entity.Role;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.User;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.exceptions.CompanyNameExistsException;
import com.sonixhr.exceptions.SubdomainExistsException;
import com.sonixhr.repository.PermissionRepository;
import com.sonixhr.repository.RoleRepository;
import com.sonixhr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public static class TenantService {

        private final TenantRepository tenantRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JdbcTemplate jdbcTemplate;          // ✅ for RLS
        private final RoleRepository roleRepository;      // ✅ for default roles
        private final PermissionRepository permissionRepository; // ✅ for permissions

        // ========== RLS helper methods (used by JwtAuthFilter) ==========
        @Transactional
        public void setCurrentTenantInDB(UUID tenantId) {
            if (tenantId == null) {
                jdbcTemplate.execute("SELECT set_current_tenant(NULL)");
                log.debug("Cleared tenant context in PostgreSQL");
            } else {
                jdbcTemplate.update("SELECT set_current_tenant(?)", tenantId);
                log.debug("Set tenant context in PostgreSQL to: {}", tenantId);
            }
        }

        @Transactional
        public void clearCurrentTenantInDB() {
            jdbcTemplate.execute("SELECT set_current_tenant(NULL)");
            log.debug("Cleared tenant context in PostgreSQL");
        }

        // ========== Tenant creation with default roles and user ==========
        @Transactional
        public ApiResponse<String> createTenant(TenantCreateRequest request) {
            log.info("Creating tenant for company: {}", request.getCompanyName());

            // 1. Validations
            if (tenantRepository.existsByCompanyName(request.getCompanyName())) {
                throw new CompanyNameExistsException("Company name already exists");
            }
            if (tenantRepository.existsBySubdomain(request.getSubdomain())) {
                throw new SubdomainExistsException("Subdomain already exists");
            }
            if (userRepository.existsByEmail(request.getAdminEmail())) {   // ✅ fixed method name
                throw new RuntimeException("Admin email already exists");
            }

            // 2. Create Tenant
            Tenant tenant = Tenant.builder()
                    .companyName(request.getCompanyName())
                    .subdomain(request.getSubdomain())
                    .planType(request.getPlanType())
                    .maxEmployees(request.getPlanType().getMaxEmployees())
                    .maxStorageMb(request.getPlanType().getMaxStorageMb())
                    .adminName(request.getAdminName())
                    .adminEmail(request.getAdminEmail())
                    .planStatus(PlanStatus.NOT_ACTIVATED)
                    .build();
            tenant = tenantRepository.save(tenant);

            // 3. Create default roles for this tenant (Employee, Manager, HR Manager, Tenant Admin)
            createDefaultRolesForTenant(tenant.getId());

            // 4. Create User (INACTIVE, dummy password)
            User adminUser = User.builder()
                    .tenantId(tenant.getId())
                    .email(request.getAdminEmail())
                    .passwordHash(passwordEncoder.encode("TEMPORARY_DISABLED"))
                    .isActive(false)
                    .build();
            adminUser = userRepository.save(adminUser);

            // 5. Assign the "Tenant Admin" role to this user
            Role adminRole = roleRepository.findByTenantIdAndName(tenant.getId(), "Tenant Admin")
                    .orElseThrow(() -> new RuntimeException("Default role 'Tenant Admin' not found"));
            adminUser.getRoles().add(adminRole);
            userRepository.save(adminUser);

            // 6. Generate secure setup token and send email (omitted for brevity - implement as before)
            // sendInvitationEmail(adminUser);

            log.info("Tenant created successfully with subdomain: {}", tenant.getSubdomain());

            return ApiResponse.<String>builder()
                    .success(true)
                    .message("Tenant created successfully. Setup email will be sent.")
                    .data(tenant.getSubdomain())
                    .build();
        }

        private void createDefaultRolesForTenant(UUID tenantId) {
            // Fetch all permissions once
            Map<String, Permission> permMap = permissionRepository.findAll().stream()
                    .collect(Collectors.toMap(Permission::getName, p -> p));

            // Employee role
            Role employeeRole = Role.builder()
                    .tenantId(tenantId)
                    .name("Employee")
                    .description("Base role for all employees")
                    .permissions(Set.of(
                            permMap.get("LEAVE_REQUEST"),
                            permMap.get("ATTENDANCE_MARK_SELF"),
                            permMap.get("EMPLOYEE_VIEW_SELF")
                    ))
                    .build();
            roleRepository.save(employeeRole);

            // Manager role
            Role managerRole = Role.builder()
                    .tenantId(tenantId)
                    .name("Manager")
                    .description("Can manage team members")
                    .permissions(Set.of(
                            permMap.get("LEAVE_APPROVE_DEPARTMENT"),
                            permMap.get("EMPLOYEE_VIEW_TEAM"),
                            permMap.get("ATTENDANCE_MARK_TEAM")
                    ))
                    .build();
            roleRepository.save(managerRole);

            // HR Manager role
            Role hrRole = Role.builder()
                    .tenantId(tenantId)
                    .name("HR Manager")
                    .description("Full HR control")
                    .permissions(Set.of(
                            permMap.get("EMPLOYEE_VIEW_ALL"),
                            permMap.get("EMPLOYEE_CREATE"),
                            permMap.get("EMPLOYEE_EDIT"),
                            permMap.get("LEAVE_APPROVE_ANY"),
                            permMap.get("MANAGE_ROLES")
                    ))
                    .build();
            roleRepository.save(hrRole);

            // Tenant Admin role (inherits HR permissions + extra)
            Role adminRole = Role.builder()
                    .tenantId(tenantId)
                    .name("Tenant Admin")
                    .description("Full tenant administration")
                    .permissions(Set.of(
                            permMap.get("EMPLOYEE_VIEW_ALL"),
                            permMap.get("EMPLOYEE_CREATE"),
                            permMap.get("EMPLOYEE_EDIT"),
                            permMap.get("LEAVE_APPROVE_ANY"),
                            permMap.get("MANAGE_ROLES"),
                            permMap.get("VIEW_BILLING")
                    ))
                    .build();
            roleRepository.save(adminRole);
        }
    }
}