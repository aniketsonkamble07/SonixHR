package com.sonixhr.bootstrap;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class TenantRoleSeeder implements ApplicationRunner {

    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Tenant Role Seeder Started");
        log.info("=========================================");

        // Get all tenants that need roles
        List<Tenant> tenants = tenantRepository.findAll();

        if (tenants.isEmpty()) {
            log.warn("No tenants found! Please ensure TenantSeeder runs first.");
            return;
        }

        List<TenantPermission> allPermissions = permissionRepository.findAll();
        log.info("Found {} permissions", allPermissions.size());

        if (allPermissions.isEmpty()) {
            log.warn("No permissions found! Please ensure TenantPermissionSeeder runs first.");
            return;
        }

        // Create roles for each tenant
        for (Tenant tenant : tenants) {
            log.info("Creating roles for tenant: {} (ID: {})", tenant.getCompanyName(), tenant.getId());
            createRolesForTenant(tenant.getId(), allPermissions);
        }

        log.info("=========================================");
        log.info("Tenant Role Seeder Completed");
        log.info("=========================================");
    }

    private void createRolesForTenant(Long tenantId, List<TenantPermission> allPermissions) {
        // Create Super Admin role for this tenant
        createSuperAdminRole(tenantId, allPermissions);

        // Create Admin role for this tenant
        createAdminRole(tenantId, allPermissions);

        // Create Employee role for this tenant
        createEmployeeRole(tenantId, allPermissions);

        // Create Manager role for this tenant
        createManagerRole(tenantId, allPermissions);
    }

    private void createSuperAdminRole(Long tenantId, List<TenantPermission> allPermissions) {

        Optional<TenantRole> existingRole = roleRepository.findByNameAndTenantId("Super Admin", tenantId);
        if (existingRole.isPresent()) {
            log.info("Super Admin role already exists for tenant: {}", tenantId);
            return;
        }

        // Create Super Admin role with ALL permissions
        TenantRole superAdminRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Super Admin")
                .description("Super Administrator with full access to all tenant features")
                .isDefault(false)
                .permissions(new HashSet<>(allPermissions))
                .build();

        roleRepository.save(superAdminRole);
        log.info("✅ Created Super Admin role for tenant {} with {} permissions", tenantId, allPermissions.size());
    }

    private void createAdminRole(Long tenantId, List<TenantPermission> allPermissions) {
        Optional<TenantRole> existingRole = roleRepository.findByNameAndTenantId("Admin", tenantId);
        if (existingRole.isPresent()) {
            log.info("Admin role already exists for tenant: {}", tenantId);
            return;
        }

        // Admin permissions
        Set<String> adminPermissionNames = Set.of(
                "EMPLOYEE_VIEW_SELF", "EMPLOYEE_VIEW_TEAM", "EMPLOYEE_VIEW_ALL",
                "EMPLOYEE_CREATE", "EMPLOYEE_EDIT",
                "LEAVE_REQUEST", "LEAVE_VIEW_OWN", "LEAVE_VIEW_TEAM", "LEAVE_APPROVE_DEPARTMENT",
                "ATTENDANCE_MARK_SELF", "ATTENDANCE_VIEW_OWN", "ATTENDANCE_VIEW_TEAM",
                "DEPARTMENT_VIEW", "DEPARTMENT_CREATE", "DEPARTMENT_EDIT",
                "ROLE_VIEW",
                "REPORT_VIEW_DEPARTMENT", "REPORT_EXPORT",
                "SETTINGS_VIEW", "VIEW_BILLING"
        );

        Set<TenantPermission> adminPermissions = allPermissions.stream()
                .filter(p -> adminPermissionNames.contains(p.getPermission().name()))
                .collect(Collectors.toSet());

        TenantRole adminRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Admin")
                .description("Administrator with limited management access")
                .isDefault(false)
                .permissions(adminPermissions)
                .build();

        roleRepository.save(adminRole);
        log.info("✅ Created Admin role for tenant {} with {} permissions", tenantId, adminPermissions.size());
    }

    private void createEmployeeRole(Long tenantId, List<TenantPermission> allPermissions) {
        Optional<TenantRole> existingRole = roleRepository.findByNameAndTenantId("Employee", tenantId);
        if (existingRole.isPresent()) {
            log.info("Employee role already exists for tenant: {}", tenantId);
            return;
        }

        // Employee permissions
        Set<String> employeePermissionNames = Set.of(
                "EMPLOYEE_VIEW_SELF",
                "LEAVE_REQUEST", "LEAVE_VIEW_OWN", "LEAVE_CANCEL_OWN",
                "ATTENDANCE_MARK_SELF", "ATTENDANCE_VIEW_OWN"
        );

        Set<TenantPermission> employeePermissions = allPermissions.stream()
                .filter(p -> employeePermissionNames.contains(p.getPermission().name()))
                .collect(Collectors.toSet());

        TenantRole employeeRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Employee")
                .description("Basic employee access - default role for new employees")
                .isDefault(true)
                .permissions(employeePermissions)
                .build();

        roleRepository.save(employeeRole);
        log.info("✅ Created Employee role for tenant {} with {} permissions", tenantId, employeePermissions.size());
    }

    private void createManagerRole(Long tenantId, List<TenantPermission> allPermissions) {
        Optional<TenantRole> existingRole = roleRepository.findByNameAndTenantId("Manager", tenantId);
        if (existingRole.isPresent()) {
            log.info("Manager role already exists for tenant: {}", tenantId);
            return;
        }

        // Manager permissions
        Set<String> managerPermissionNames = Set.of(
                "EMPLOYEE_VIEW_SELF", "EMPLOYEE_VIEW_TEAM",
                "LEAVE_REQUEST", "LEAVE_VIEW_OWN", "LEAVE_VIEW_TEAM",
                "LEAVE_APPROVE_DEPARTMENT", "LEAVE_CANCEL_OWN",
                "ATTENDANCE_MARK_SELF", "ATTENDANCE_VIEW_OWN", "ATTENDANCE_VIEW_TEAM",
                "DEPARTMENT_VIEW",
                "REPORT_VIEW_DEPARTMENT"
        );

        Set<TenantPermission> managerPermissions = allPermissions.stream()
                .filter(p -> managerPermissionNames.contains(p.getPermission().name()))
                .collect(Collectors.toSet());

        TenantRole managerRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Manager")
                .description("Team manager with people management access")
                .isDefault(false)
                .permissions(managerPermissions)
                .build();

        roleRepository.save(managerRole);
        log.info("✅ Created Manager role for tenant {} with {} permissions", tenantId, managerPermissions.size());
    }
}