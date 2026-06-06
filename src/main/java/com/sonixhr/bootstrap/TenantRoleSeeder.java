package com.sonixhr.bootstrap;

import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class TenantRoleSeeder implements ApplicationRunner {

    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Tenant Role Seeder Started");
        log.info("=========================================");


        List<TenantPermission> allPermissions = permissionRepository.findAll();
        log.info("Found {} global permissions", allPermissions.size());

        if (allPermissions.isEmpty()) {
            log.warn("No permissions found! Please ensure TenantPermissionSeeder runs first.");
            return;
        }

        // Create Super Admin role (required for tenant registration)
        createSuperAdminRole(allPermissions);

        // Create Admin role (optional, for additional admins)
        createAdminRole(allPermissions);

        // Create Employee role (default role for regular employees)
        createEmployeeRole(allPermissions);

        log.info("=========================================");
        log.info("Tenant Role Seeder Completed");
        log.info("=========================================");
    }

    private void createSuperAdminRole(List<TenantPermission> allPermissions) {
        // Check if Super Admin template role already exists
        if (roleRepository.findByNameAndTenantIdIsNull("Super Admin").isPresent()) {
            log.info("Super Admin template role already exists");
            return;
        }

        // Create Super Admin role with ALL permissions
        TenantRole superAdminRole = TenantRole.builder()
                .tenantId(null)  // Template role
                .name("Super Admin")
                .description("Super Administrator with full access to all tenant features")
                .isDefault(false)
                .permissions(new HashSet<>(allPermissions))
                .build();

        roleRepository.save(superAdminRole);
        log.info(" Created Super Admin template role with {} permissions", allPermissions.size());
    }

    private void createAdminRole(List<TenantPermission> allPermissions) {
        // Check if Admin template role already exists
        if (roleRepository.findByNameAndTenantIdIsNull("Admin").isPresent()) {
            log.info("Admin template role already exists");
            return;
        }

        // Define admin permissions (subset of Super Admin)
        Set<String> adminPermissionNames = Set.of(
                "EMPLOYEE_VIEW_SELF", "EMPLOYEE_VIEW_TEAM", "EMPLOYEE_VIEW_ALL",
                "EMPLOYEE_CREATE", "EMPLOYEE_EDIT",
                "LEAVE_REQUEST", "LEAVE_VIEW_OWN", "LEAVE_VIEW_TEAM", "LEAVE_APPROVE_DEPARTMENT",
                "ATTENDANCE_MARK_SELF", "ATTENDANCE_VIEW_OWN", "ATTENDANCE_VIEW_TEAM",
                "DEPARTMENT_VIEW", "DEPARTMENT_CREATE", "DEPARTMENT_EDIT",
                "ROLE_VIEW", "ROLE_VIEW_ASSIGNED",
                "REPORT_VIEW_DEPARTMENT", "REPORT_EXPORT",
                "SETTINGS_VIEW", "VIEW_BILLING"
        );

        Set<TenantPermission> adminPermissions = allPermissions.stream()
                .filter(p -> adminPermissionNames.contains(p.getPermission().name()))
                .collect(Collectors.toSet());

        TenantRole adminRole = TenantRole.builder()
                .tenantId(null)  // Template role
                .name("Admin")
                .description("Administrator with limited management access")
                .isDefault(false)
                .permissions(adminPermissions)
                .build();

        roleRepository.save(adminRole);
        log.info(" Created Admin template role with {} permissions", adminPermissions.size());
    }

    private void createEmployeeRole(List<TenantPermission> allPermissions) {
        // Check if Employee template role already exists
        if (roleRepository.findByNameAndTenantIdIsNull("Employee").isPresent()) {
            log.info("Employee template role already exists");
            return;
        }

        // Define employee permissions (basic access only)
        Set<String> employeePermissionNames = Set.of(
                "EMPLOYEE_VIEW_SELF",
                "LEAVE_REQUEST", "LEAVE_VIEW_OWN", "LEAVE_CANCEL_OWN",
                "ATTENDANCE_MARK_SELF", "ATTENDANCE_VIEW_OWN"
        );

        Set<TenantPermission> employeePermissions = allPermissions.stream()
                .filter(p -> employeePermissionNames.contains(p.getPermission().name()))
                .collect(Collectors.toSet());

        TenantRole employeeRole = TenantRole.builder()
                .tenantId(null)  // Template role
                .name("Employee")
                .description("Basic employee access - default role for new employees")
                .isDefault(true)
                .permissions(employeePermissions)
                .build();

        roleRepository.save(employeeRole);
        log.info(" Created Employee template role with {} permissions", employeePermissions.size());
    }
}