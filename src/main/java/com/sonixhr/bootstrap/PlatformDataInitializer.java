package com.sonixhr.bootstrap;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;

    private static final String SUPER_ADMIN_EMAIL = "admin@sonixhr.com";
    private static final String SUPER_ADMIN_PASSWORD = "Admin@123";
    private static final String SUPER_ADMIN_NAME = "Super Administrator";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Platform Data Initializer Started");
        log.info("=========================================");

        // Step 1: Create all permissions
        createAllPermissions();

        // Step 2: Create Super Admin role with ALL permissions
        PlatformRole superAdminRole = createSuperAdminRole();

        // Step 3: Create Super Admin user
        createSuperAdminUser(superAdminRole);

        // Step 4: Create other default roles (optional)
        createOtherDefaultRoles();

        log.info("=========================================");
        log.info("Platform Data Initializer Completed");
        log.info("=========================================");
    }

    // ✅ KEEP ONLY ONE of these methods
    private void createAllPermissions() {
        log.info("Creating all permissions...");
        int createdCount = 0;

        for (PlatformPermissionEnum permEnum : PlatformPermissionEnum.values()) {
            // Use .name() to convert enum to String
            if (permissionRepository.findByPermission(permEnum.name()).isEmpty()) {
                PlatformPermission permission = PlatformPermission.builder()
                        .permission(permEnum.name())  // Store enum name as String
                        .description(permEnum.getDescription())
                        .category(permEnum.getCategory())
                        .displayOrder(permEnum.getOrder())
                        .active(true)
                        .build();
                permissionRepository.save(permission);
                createdCount++;
                log.debug("Created permission: {}", permEnum.name());
            }
        }
        log.info("✅ Created {} new permissions. Total: {}", createdCount, permissionRepository.count());
    }


    private PlatformRole createSuperAdminRole() {
        log.info("Creating Super Admin role...");

        return roleRepository.findByName("Super Admin")
                .orElseGet(() -> {
                    Set<PlatformPermission> allPermissions = new HashSet<>(permissionRepository.findAll());

                    PlatformRole role = PlatformRole.builder()
                            .name("Super Admin")
                            .description("Full platform access - has ALL permissions")
                            .systemRole(true)
                            .active(true)
                            .priority(100)
                            .category("SYSTEM_ADMINISTRATION")
                            .permissions(allPermissions)
                            .build();

                    PlatformRole saved = roleRepository.save(role);
                    log.info("✅ Created Super Admin role with {} permissions", allPermissions.size());
                    return saved;
                });
    }

    private void createSuperAdminUser(PlatformRole superAdminRole) {
        log.info("Creating Super Admin user...");

        if (userRepository.findByEmail(SUPER_ADMIN_EMAIL).isPresent()) {
            log.info("Super Admin user already exists: {}", SUPER_ADMIN_EMAIL);
            return;
        }

        PlatformUser superAdmin = PlatformUser.builder()
                .email(SUPER_ADMIN_EMAIL)
                .password(passwordEncoder.encode(SUPER_ADMIN_PASSWORD))
                .fullName(SUPER_ADMIN_NAME)
                .designation("System Administrator")
                .status(UserStatus.ACTIVE)
                .rolesVersion(1)
                .build();

        superAdmin.getRoles().add(superAdminRole);
        userRepository.save(superAdmin);

        log.info("=========================================");
        log.info("✅ SUPER ADMIN CREATED WITH ALL PERMISSIONS!");
        log.info("   Email: {}", SUPER_ADMIN_EMAIL);
        log.info("   Password: {}", SUPER_ADMIN_PASSWORD);
        log.info("   Name: {}", SUPER_ADMIN_NAME);
        log.info("   Role: Super Admin (ALL permissions)");
        log.info("   Authorities: {}", superAdmin.getAuthorities().size());
        log.info("=========================================");
        log.warn("⚠️  PLEASE CHANGE THE DEFAULT PASSWORD AFTER FIRST LOGIN!");
        log.info("=========================================");
    }

    private void createOtherDefaultRoles() {
        log.info("Creating other default roles...");

        // Platform Admin Role
        createRoleIfMissing("Platform Admin",
                "Manages platform operations",
                Set.of(
                        PlatformPermissionEnum.VIEW_TENANTS,
                        PlatformPermissionEnum.VIEW_TENANT_DETAILS,
                        PlatformPermissionEnum.VIEW_PLATFORM_USERS,
                        PlatformPermissionEnum.VIEW_PLATFORM_ROLES,
                        PlatformPermissionEnum.VIEW_SUBSCRIPTIONS,
                        PlatformPermissionEnum.VIEW_ANALYTICS
                ));

        // Support Admin Role
        createRoleIfMissing("Support Admin",
                "Handles support tickets",
                Set.of(
                        PlatformPermissionEnum.VIEW_TENANTS,
                        PlatformPermissionEnum.VIEW_SUPPORT_TICKETS,
                        PlatformPermissionEnum.MANAGE_SUPPORT_TICKETS,
                        PlatformPermissionEnum.RESOLVE_ISSUES
                ));

        // Billing Admin Role
        createRoleIfMissing("Billing Admin",
                "Manages billing and subscriptions",
                Set.of(
                        PlatformPermissionEnum.VIEW_SUBSCRIPTIONS,
                        PlatformPermissionEnum.MANAGE_SUBSCRIPTIONS,
                        PlatformPermissionEnum.VIEW_INVOICES,
                        PlatformPermissionEnum.PROCESS_PAYMENTS,
                        PlatformPermissionEnum.VIEW_BILLING_REPORTS
                ));

        log.info("✅ Default roles created successfully");
    }

    // ✅ KEEP ONLY ONE of these methods - THIS IS THE CORRECT ONE (using .name())
    private void createRoleIfMissing(String roleName, String description, Set<PlatformPermissionEnum> permissionEnums) {
        if (roleRepository.findByName(roleName).isPresent()) {
            log.debug("Role already exists: {}", roleName);
            return;
        }

        Set<PlatformPermission> permissions = new HashSet<>();
        for (PlatformPermissionEnum permEnum : permissionEnums) {
            // Use .name() to convert enum to String
            permissionRepository.findByPermission(permEnum.name()).ifPresent(permissions::add);
        }

        PlatformRole role = PlatformRole.builder()
                .name(roleName)
                .description(description)
                .systemRole(false)
                .active(true)
                .priority(50)
                .category(determineCategory(roleName))
                .permissions(permissions)
                .build();

        roleRepository.save(role);
        log.info("✅ Created role: {} with {} permissions", roleName, permissions.size());
    }

    private String determineCategory(String roleName) {
        if (roleName.contains("Admin")) return "ADMINISTRATION";
        if (roleName.contains("Support")) return "SUPPORT";
        if (roleName.contains("Billing")) return "BILLING";
        return "CUSTOM";
    }
}