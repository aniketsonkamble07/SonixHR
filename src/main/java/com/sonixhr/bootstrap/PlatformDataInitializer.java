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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
@SuppressWarnings("null")
public class PlatformDataInitializer implements ApplicationRunner {

    private final PlatformPermissionRepository permissionRepository;
    private final PlatformRoleRepository roleRepository;
    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    private static final String SUPER_ADMIN_EMAIL = "admin@sonixhr.com";
    private static final String SUPER_ADMIN_PASSWORD = "Admin@123";
    private static final String SUPER_ADMIN_NAME = "Super Administrator";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Platform Data Initializer Started");
        log.info("=========================================");

        // Run DDL update for blood_group column
        try {
            log.info("Altering employees.blood_group column type to VARCHAR(20) if necessary...");
            jdbcTemplate.execute("ALTER TABLE employees ALTER COLUMN blood_group TYPE VARCHAR(20)");
            log.info("Successfully altered employees.blood_group column type.");
        } catch (Exception e) {
            log.warn("Could not alter employees.blood_group column (table might not exist or column already altered): {}", e.getMessage());
        }

        // Drop obsolete enum check constraints on tenants and tenant_subscriptions
        try {
            log.info("Dropping obsolete check constraints for plan_type if they exist...");
            jdbcTemplate.execute("ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_plan_type_check");
            jdbcTemplate.execute("ALTER TABLE tenant_subscriptions DROP CONSTRAINT IF EXISTS tenant_subscriptions_plan_type_check");
            log.info("Successfully dropped obsolete plan_type check constraints.");
        } catch (Exception e) {
            log.warn("Could not drop plan_type check constraints: {}", e.getMessage());
        }

        // Step 1: Create all permissions
        createAllPermissions();

        // Step 2: Create Super Admin role with ALL permissions
        PlatformRole superAdminRole = createSuperAdminRole();

        // Step 3: Create Super Admin user
        createSuperAdminUser(superAdminRole);

        // Step 4: Create other default roles (optional)
        createOtherDefaultRoles();

        // Step 5: Seed default subscription plans
        seedDefaultSubscriptionPlans();
        
        log.info("=========================================");
        log.info("Platform Data Initializer Completed");
        log.info("=========================================");
    }

    private void seedDefaultSubscriptionPlans() {
        if (subscriptionPlanRepository.count() == 0) {
            log.info("Seeding default subscription plans...");

            // 1. TRIAL
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("trial")
                    .name("Trial Plan")
                    .monthlyPrice(0.00)
                    .maxEmployees(10)
                    .maxStorageMb(512)
                    .trialDays(14)
                    .isTrial(true)
                    .isActive(true)
                    .description("Free trial access for 14 days, up to 10 employees")
                    .build());

            // 2. BASIC
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("basic")
                    .name("Basic Plan")
                    .monthlyPrice(49.00)
                    .maxEmployees(100)
                    .maxStorageMb(1024)
                    .trialDays(0)
                    .isTrial(false)
                    .isActive(true)
                    .description("Core HR features, up to 100 employees")
                    .build());

            // 3. MODERATE
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("moderate")
                    .name("Moderate Plan")
                    .monthlyPrice(99.00)
                    .maxEmployees(500)
                    .maxStorageMb(5120)
                    .trialDays(0)
                    .isTrial(false)
                    .isActive(true)
                    .description("Advanced HR features, up to 500 employees")
                    .build());

            // 4. PREMIUM
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("premium")
                    .name("Premium Plan")
                    .monthlyPrice(299.00)
                    .maxEmployees(2000)
                    .maxStorageMb(20480)
                    .trialDays(0)
                    .isTrial(false)
                    .isActive(true)
                    .description("All HR features, up to 2,000 employees")
                    .build());

            // 5. ENTERPRISE
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("enterprise")
                    .name("Enterprise Plan")
                    .monthlyPrice(999.00)
                    .maxEmployees(10000)
                    .maxStorageMb(102400)
                    .trialDays(0)
                    .isTrial(false)
                    .isActive(true)
                    .description("Custom pricing, unlimited employees, dedicated support")
                    .build());

            log.info("✅ Successfully seeded default subscription plans");
        }
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