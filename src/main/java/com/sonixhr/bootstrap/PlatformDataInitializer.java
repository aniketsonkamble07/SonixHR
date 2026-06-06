package com.sonixhr.bootstrap;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.enums.PlatformUserStatus;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class PlatformDataInitializer implements ApplicationRunner {

    private final PlatformPermissionRepository permissionRepository;
    private final PlatformRoleRepository roleRepository;
    private final PlatformUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String SYSTEM_TENANT_CODE = "SYSTEM";
    private static final String SUPER_ADMIN_EMAIL = "admin@sonixhr.com";
    private static final String SUPER_ADMIN_PASSWORD = "Admin@123";
    private static final String SUPER_ADMIN_NAME = "Super Administrator";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Platform Data Initializer Started");
        log.info("=========================================");

        Tenant systemTenant = getOrCreateSystemTenant();
        Long systemTenantId = systemTenant.getId();

        log.info("System Tenant ID: {} with code: {}", systemTenantId, systemTenant.getTenantCode());

        createPermissionsIfMissing(systemTenantId);
        createSuperAdminRoleIfMissing(systemTenantId);
        createOtherDefaultRolesIfMissing(systemTenantId);
        createSuperAdminUserIfMissing(systemTenantId);

        log.info("=========================================");
        log.info("Platform Data Initializer Completed");
        log.info("=========================================");
    }

    private Tenant getOrCreateSystemTenant() {
        log.info("Getting or creating system tenant with code: {}", SYSTEM_TENANT_CODE);

        return tenantRepository.findByTenantCode(SYSTEM_TENANT_CODE)
                .orElseGet(() -> {
                    log.info("System tenant not found, creating new one...");
                    Tenant systemTenant = Tenant.builder()
                            .tenantCode(SYSTEM_TENANT_CODE)
                            .companyName("SonixHR Platform")
                            .subdomain("system")
                            .status("ACTIVE")
                            .isActive(true)
                            .adminEmail("admin@sonixhr.com")
                            .adminName("System Administrator")
                            .planType("PLATFORM")
                            .maxEmployees(10000)
                            .planStatus("ACTIVE")
                            .build();
                    Tenant saved = tenantRepository.save(systemTenant);
                    log.info("✅ Created system tenant with ID: {} and code: {}", saved.getId(), saved.getTenantCode());
                    return saved;
                });
    }

    private void createPermissionsIfMissing(Long systemTenantId) {
        log.info("Seeding platform permissions for tenant: {}", systemTenantId);
        int createdCount = 0;

        for (PlatformPermissionEnum adminPerm : PlatformPermissionEnum.values()) {
            if (permissionRepository.findByPermission(adminPerm).isEmpty()) {
                PlatformPermission perm = PlatformPermission.builder()
                        .tenantId(systemTenantId)
                        .permission(adminPerm)
                        .description(adminPerm.getDescription())
                        .category(adminPerm.getCategory())
                        .displayOrder(adminPerm.getOrder())
                        .build();
                permissionRepository.save(perm);
                createdCount++;
                log.debug("Created permission: {}", adminPerm.name());
            }
        }
        log.info("Platform permissions seeding completed. Created: {}, Total: {}",
                createdCount, permissionRepository.count());
    }

    private void createSuperAdminRoleIfMissing(Long systemTenantId) {
        Set<PlatformPermission> allPermissions = new HashSet<>(permissionRepository.findAll());

        if (allPermissions.isEmpty()) {
            log.warn("No permissions found – run createPermissionsIfMissing first.");
            return;
        }

        PlatformRole superAdminRole = roleRepository.findByNameAndTenantId("Super Admin", systemTenantId)
                .orElse(null);

        if (superAdminRole == null) {
            superAdminRole = PlatformRole.builder()
                    .tenantId(systemTenantId)
                    .name("Super Admin")
                    .description("Full platform access – manages everything (System Administrator)")
                    .permissions(allPermissions)
                    .isSystemRole(true)
                    .category("SYSTEM")
                    .createdAt(LocalDateTime.now())
                    .build();
            roleRepository.save(superAdminRole);
            log.info("✅ Created Super Admin role with {} permissions.", allPermissions.size());
        } else {
            superAdminRole.setPermissions(allPermissions);
            roleRepository.save(superAdminRole);
            log.info("✅ Updated Super Admin role to have {} permissions.", allPermissions.size());
        }
    }

    private void createOtherDefaultRolesIfMissing(Long systemTenantId) {
        createPlatformRoleIfMissing(systemTenantId, "Platform Admin",
                "Manages platform operations but with limited system access",
                Set.of(
                        PlatformPermissionEnum.VIEW_TENANTS,
                        PlatformPermissionEnum.VIEW_TENANT_DETAILS,
                        PlatformPermissionEnum.VIEW_PLATFORM_ADMINS,
                        PlatformPermissionEnum.VIEW_PLATFORM_ROLES,
                        PlatformPermissionEnum.VIEW_SUBSCRIPTIONS,
                        PlatformPermissionEnum.VIEW_INVOICES,
                        PlatformPermissionEnum.VIEW_SUPPORT_TICKETS,
                        PlatformPermissionEnum.MANAGE_SUPPORT_TICKETS,
                        PlatformPermissionEnum.VIEW_ANALYTICS,
                        PlatformPermissionEnum.VIEW_SYSTEM_HEALTH
                ));

        createPlatformRoleIfMissing(systemTenantId, "Support Admin",
                "Handles support tickets and user queries",
                Set.of(
                        PlatformPermissionEnum.VIEW_TENANTS,
                        PlatformPermissionEnum.VIEW_TENANT_DETAILS,
                        PlatformPermissionEnum.VIEW_SUPPORT_TICKETS,
                        PlatformPermissionEnum.MANAGE_SUPPORT_TICKETS,
                        PlatformPermissionEnum.RESOLVE_ISSUES,
                        PlatformPermissionEnum.VIEW_SUPPORT_METRICS
                ));

        createPlatformRoleIfMissing(systemTenantId, "Billing Admin",
                "Manages billing, subscriptions and invoices",
                Set.of(
                        PlatformPermissionEnum.VIEW_TENANTS,
                        PlatformPermissionEnum.VIEW_TENANT_DETAILS,
                        PlatformPermissionEnum.VIEW_SUBSCRIPTIONS,
                        PlatformPermissionEnum.MANAGE_SUBSCRIPTIONS,
                        PlatformPermissionEnum.VIEW_INVOICES,
                        PlatformPermissionEnum.PROCESS_PAYMENTS,
                        PlatformPermissionEnum.VIEW_BILLING_REPORTS,
                        PlatformPermissionEnum.MANAGE_PRICING_PLANS
                ));

        createPlatformRoleIfMissing(systemTenantId, "Audit Viewer",
                "Read-only access to audit logs and system metrics",
                Set.of(
                        PlatformPermissionEnum.VIEW_SYSTEM_METRICS,
                        PlatformPermissionEnum.VIEW_AUDIT_LOGS,
                        PlatformPermissionEnum.VIEW_ACTIVITY_REPORTS,
                        PlatformPermissionEnum.VIEW_SYSTEM_HEALTH
                ));

        createPlatformRoleIfMissing(systemTenantId, "Tenant Creator",
                "Can create and manage tenants",
                Set.of(
                        PlatformPermissionEnum.CREATE_TENANT,
                        PlatformPermissionEnum.VIEW_TENANTS,
                        PlatformPermissionEnum.VIEW_TENANT_DETAILS
                ));
    }

    private void createPlatformRoleIfMissing(Long tenantId, String roleName, String description,
                                             Set<PlatformPermissionEnum> permissionEnums) {
        if (roleRepository.findByNameAndTenantId(roleName, tenantId).isPresent()) {
            log.debug("Role already exists: {} for tenant: {}", roleName, tenantId);
            return;
        }

        Set<PlatformPermission> permissions = new HashSet<>();
        for (PlatformPermissionEnum permEnum : permissionEnums) {
            permissionRepository.findByPermission(permEnum).ifPresent(permissions::add);
        }

        PlatformRole role = PlatformRole.builder()
                .tenantId(tenantId)
                .name(roleName)
                .description(description)
                .permissions(permissions)
                .isSystemRole(false)
                .category(getRoleCategory(roleName))
                .createdAt(LocalDateTime.now())
                .build();

        roleRepository.save(role);
        log.info("✅ Created platform role: {} with {} permissions for tenant: {}", roleName, permissions.size(), tenantId);
    }

    private void createSuperAdminUserIfMissing(Long systemTenantId) {
        var existingUser = userRepository.findByEmail(SUPER_ADMIN_EMAIL);
        if (existingUser.isPresent()) {
            PlatformUser user = existingUser.get();

            PlatformRole superAdminRole = roleRepository.findByNameAndTenantId("Super Admin", systemTenantId)
                    .orElseThrow(() -> new IllegalStateException("Super Admin role not found"));

            if (!user.getRoles().contains(superAdminRole)) {
                log.info("Adding Super Admin role to existing user");
                user.getRoles().add(superAdminRole);
                userRepository.save(user);
                log.info("✅ Added Super Admin role to existing user");
            }

            log.info("Super Admin user already exists: {}", SUPER_ADMIN_EMAIL);
            log.info("User roles count: {}", user.getRoles().size());
            log.info("User authorities count: {}", user.getAuthorities().size());
            return;
        }

        PlatformRole superAdminRole = roleRepository.findByNameAndTenantId("Super Admin", systemTenantId)
                .orElseThrow(() -> new IllegalStateException("Super Admin role not found – seeding order problem?"));

        PlatformUser superAdmin = PlatformUser.builder()
                .tenantId(systemTenantId)
                .email(SUPER_ADMIN_EMAIL)
                .password(passwordEncoder.encode(SUPER_ADMIN_PASSWORD))
                .fullName(SUPER_ADMIN_NAME)
                .designation("System Administrator")
                .isActive(true)
                .isEnabled(true)
                .status(PlatformUserStatus.ACTIVE)
                .mustChangePassword(true)
                .systemProtected(true)
                .build();

        superAdmin.getRoles().add(superAdminRole);
        superAdmin = userRepository.save(superAdmin);

        log.info("=========================================");
        log.info("✅ DEFAULT SUPER ADMIN CREATED!");
        log.info("   Email: {}", SUPER_ADMIN_EMAIL);
        log.info("   Password: {}", SUPER_ADMIN_PASSWORD);
        log.info("   Tenant: SYSTEM (ID: {})", systemTenantId);
        log.info("   Role: Super Admin");
        log.info("   Roles Count: {}", superAdmin.getRoles().size());
        log.info("   Authorities Count: {}", superAdmin.getAuthorities().size());
        log.info("=========================================");
        log.warn("🔴 PLEASE CHANGE THE DEFAULT PASSWORD AFTER FIRST LOGIN!");
        log.info("=========================================");
    }

    private String getRoleCategory(String roleName) {
        return switch (roleName) {
            case "Platform Admin" -> "ADMIN";
            case "Support Admin" -> "SUPPORT";
            case "Billing Admin" -> "BILLING";
            case "Audit Viewer" -> "AUDIT";
            case "Tenant Creator" -> "TENANT_MANAGEMENT";
            default -> "CUSTOM";
        };
    }
}