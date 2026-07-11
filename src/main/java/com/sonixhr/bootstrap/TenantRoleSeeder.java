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

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
@SuppressWarnings({ "null", "unused" })
public class TenantRoleSeeder implements ApplicationRunner {

    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;
    private final TenantRepository tenantRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Distributed lock: prevents duplicate role creation when multiple instances
        // start simultaneously
        final String LOCK_KEY = "bootstrap:tenant-role-seeder:lock";
        Boolean lockAcquired = null;
        try {
            lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "running", 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis unavailable for TenantRoleSeeder lock — proceeding without lock: {}", e.getMessage());
        }

        if (Boolean.FALSE.equals(lockAcquired)) {
            log.info("TenantRoleSeeder lock held by another instance. Skipping to avoid duplicate seeding.");
            return;
        }

        try {
            log.info("=========================================");
            log.info("Tenant Role Seeder Started");
            log.info("=========================================");

            List<TenantPermission> allPermissions = permissionRepository.findAll();
            log.info("Found {} permissions", allPermissions.size());

            if (allPermissions.isEmpty()) {
                log.warn("No permissions found! Please ensure TenantPermissionSeeder runs first.");
                return;
            }

            // Process tenants in pages to avoid loading the entire table into memory
            final int PAGE_SIZE = 100;
            int page = 0;
            List<Tenant> tenants;
            int totalProcessed = 0;

            do {
                tenants = tenantRepository.findAll(PageRequest.of(page++, PAGE_SIZE)).getContent();
                for (Tenant tenant : tenants) {
                    log.debug("Creating roles for tenant: {} (ID: {})", tenant.getCompanyName(), tenant.getId());
                    createRolesForTenant(tenant.getId(), allPermissions);
                }
                totalProcessed += tenants.size();
            } while (tenants.size() == PAGE_SIZE);

            if (totalProcessed == 0) {
                log.warn("No tenants found — skipping role seeding.");
            }

            log.info("=========================================");
            log.info("Tenant Role Seeder Completed");
            log.info("=========================================");
        } finally {
            if (Boolean.TRUE.equals(lockAcquired)) {
                try {
                    redisTemplate.delete(LOCK_KEY);
                } catch (Exception e) {
                    log.warn("Could not release TenantRoleSeeder lock: {}", e.getMessage());
                }
            }
        }
    }

    private void createRolesForTenant(Long tenantId, List<TenantPermission> allPermissions) {
        createAdminRole(tenantId, allPermissions);
        createManagerRole(tenantId, allPermissions);
        createEmployeeRole(tenantId, allPermissions);
    }

    private void createAdminRole(Long tenantId, List<TenantPermission> allPermissions) {
        Optional<TenantRole> existingRole = roleRepository.findByTenantIdAndName(tenantId, "Admin");
        if (existingRole.isPresent()) {
            log.debug("Admin role already exists for tenant: {}", tenantId);
            return;
        }

        TenantRole adminRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Admin")
                .description("Administrator with full access to all tenant features")
                .isDefault(true)
                .active(true)
                .priority(100)
                .category("ADMINISTRATION")
                .permissions(new HashSet<>(allPermissions))
                .build();

        roleRepository.save(adminRole);
        log.info("Created Admin role for tenant {} with {} permissions", tenantId, allPermissions.size());
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
                TenantPermissionEnum.ATTENDANCE_VIEW_OWN.name());

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
        log.info("Created Employee role for tenant {} with {} permissions", tenantId, employeePermissions.size());
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
                TenantPermissionEnum.REPORT_VIEW_DEPARTMENT.name());

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
        log.info(" Created Manager role for tenant {} with {} permissions", tenantId, managerPermissions.size());
    }
}