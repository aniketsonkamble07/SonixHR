package com.sonixhr.bootstrap;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.enums.TenantPermissionEnum;
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
        Optional<TenantRole> existingRole = roleRepository.findByTenantIdAndName(tenantId, "Super Admin");
        if (existingRole.isPresent()) {
            log.debug("Super Admin role already exists for tenant: {}", tenantId);
            return;
        }

        TenantRole superAdminRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Super Admin")
                .description("Super Administrator with full access to all tenant features")
                .isDefault(false)
                .active(true)
                .priority(100)
                .category("ADMINISTRATION")
                .permissions(new HashSet<>(allPermissions))
                .build();

        roleRepository.save(superAdminRole);
        log.info("✅ Created Super Admin role for tenant {} with {} permissions", tenantId, allPermissions.size());
    }

    private void createAdminRole(Long tenantId, List<TenantPermission> allPermissions) {
        Optional<TenantRole> existingRole = roleRepository.findByTenantIdAndName(tenantId, "Admin");
        if (existingRole.isPresent()) {
            log.debug("Admin role already exists for tenant: {}", tenantId);
            return;
        }

        // Define admin permissions as Strings (enum names)
        Set<String> adminPermissionNames = Set.of(
                TenantPermissionEnum.EMPLOYEE_VIEW_SELF.name(),
                TenantPermissionEnum.EMPLOYEE_VIEW_TEAM.name(),
                TenantPermissionEnum.EMPLOYEE_VIEW_ALL.name(),
                TenantPermissionEnum.EMPLOYEE_CREATE.name(),
                TenantPermissionEnum.EMPLOYEE_EDIT.name(),
                TenantPermissionEnum.LEAVE_REQUEST.name(),
                TenantPermissionEnum.LEAVE_VIEW_OWN.name(),
                TenantPermissionEnum.LEAVE_VIEW_TEAM.name(),
                TenantPermissionEnum.LEAVE_APPROVE_DEPARTMENT.name(),
                TenantPermissionEnum.ATTENDANCE_MARK_SELF.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_OWN.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_TEAM.name(),
                TenantPermissionEnum.DEPARTMENT_VIEW.name(),
                TenantPermissionEnum.DEPARTMENT_CREATE.name(),
                TenantPermissionEnum.DEPARTMENT_EDIT.name(),
                TenantPermissionEnum.ROLE_VIEW.name(),
                TenantPermissionEnum.REPORT_VIEW_DEPARTMENT.name(),
                TenantPermissionEnum.REPORT_EXPORT.name(),
                TenantPermissionEnum.SETTINGS_VIEW.name(),
                TenantPermissionEnum.VIEW_BILLING.name()
        );

        Set<TenantPermission> adminPermissions = allPermissions.stream()
                .filter(p -> adminPermissionNames.contains(p.getPermissionName()))
                .collect(Collectors.toSet());

        TenantRole adminRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Admin")
                .description("Administrator with limited management access")
                .isDefault(false)
                .active(true)
                .priority(80)
                .category("ADMINISTRATION")
                .permissions(adminPermissions)
                .build();

        roleRepository.save(adminRole);
        log.info("✅ Created Admin role for tenant {} with {} permissions", tenantId, adminPermissions.size());
    }

    private void createEmployeeRole(Long tenantId, List<TenantPermission> allPermissions) {
        Optional<TenantRole> existingRole = roleRepository.findByTenantIdAndName(tenantId, "Employee");
        if (existingRole.isPresent()) {
            log.debug("Employee role already exists for tenant: {}", tenantId);
            return;
        }

        // Define employee permissions as Strings (enum names)
        Set<String> employeePermissionNames = Set.of(
                TenantPermissionEnum.EMPLOYEE_VIEW_SELF.name(),
                TenantPermissionEnum.LEAVE_REQUEST.name(),
                TenantPermissionEnum.LEAVE_VIEW_OWN.name(),
                TenantPermissionEnum.LEAVE_CANCEL_OWN.name(),
                TenantPermissionEnum.ATTENDANCE_MARK_SELF.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_OWN.name()
        );

        Set<TenantPermission> employeePermissions = allPermissions.stream()
                .filter(p -> employeePermissionNames.contains(p.getPermissionName()))
                .collect(Collectors.toSet());

        TenantRole employeeRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Employee")
                .description("Basic employee access - default role for new employees")
                .isDefault(true)
                .active(true)
                .priority(40)
                .category("EMPLOYMENT")
                .permissions(employeePermissions)
                .build();

        roleRepository.save(employeeRole);
        log.info("✅ Created Employee role for tenant {} with {} permissions", tenantId, employeePermissions.size());
    }

    private void createManagerRole(Long tenantId, List<TenantPermission> allPermissions) {
        Optional<TenantRole> existingRole = roleRepository.findByTenantIdAndName(tenantId, "Manager");
        if (existingRole.isPresent()) {
            log.debug("Manager role already exists for tenant: {}", tenantId);
            return;
        }

        // Define manager permissions as Strings (enum names)
        Set<String> managerPermissionNames = Set.of(
                TenantPermissionEnum.EMPLOYEE_VIEW_SELF.name(),
                TenantPermissionEnum.EMPLOYEE_VIEW_TEAM.name(),
                TenantPermissionEnum.LEAVE_REQUEST.name(),
                TenantPermissionEnum.LEAVE_VIEW_OWN.name(),
                TenantPermissionEnum.LEAVE_VIEW_TEAM.name(),
                TenantPermissionEnum.LEAVE_APPROVE_DEPARTMENT.name(),
                TenantPermissionEnum.LEAVE_CANCEL_OWN.name(),
                TenantPermissionEnum.ATTENDANCE_MARK_SELF.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_OWN.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_TEAM.name(),
                TenantPermissionEnum.DEPARTMENT_VIEW.name(),
                TenantPermissionEnum.REPORT_VIEW_DEPARTMENT.name()
        );

        Set<TenantPermission> managerPermissions = allPermissions.stream()
                .filter(p -> managerPermissionNames.contains(p.getPermissionName()))
                .collect(Collectors.toSet());

        TenantRole managerRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Manager")
                .description("Team manager with people management access")
                .isDefault(false)
                .active(true)
                .priority(60)
                .category("MANAGEMENT")
                .permissions(managerPermissions)
                .build();

        roleRepository.save(managerRole);
        log.info("✅ Created Manager role for tenant {} with {} permissions", tenantId, managerPermissions.size());
    }
}