package com.sonixhr.bootstrap;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.IndianState;
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
import com.sonixhr.entity.payroll.StatutoryRateConfig;
import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.repository.payroll.StatutoryRateConfigRepository;
import com.sonixhr.repository.payroll.StateProfessionalTaxConfigRepository;

import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;
import java.time.LocalDate;

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
    private final StatutoryRateConfigRepository statutoryRateConfigRepo;
    private final StateProfessionalTaxConfigRepository statePtConfigRepo;

    private static final String SUPER_ADMIN_EMAIL = "admin@sonixhr.com";
    private static final String SUPER_ADMIN_NAME = "Super Administrator";


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Platform Data Initializer Started");
        log.info("=========================================");

        // Alter blood_group only if the column type/length does not already match — avoids a table lock on every boot
        try {
            Long mismatch = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'employees' " +
                    "AND column_name = 'blood_group' " +
                    "AND NOT (data_type = 'character varying' AND character_maximum_length = 20)",
                    Long.class);
            if (mismatch != null && mismatch > 0) {
                log.info("Altering employees.blood_group column type to VARCHAR(20)...");
                jdbcTemplate.execute("ALTER TABLE employees ALTER COLUMN blood_group TYPE VARCHAR(20)");
                log.info("Successfully altered employees.blood_group column type.");
            } else {
                log.debug("employees.blood_group is already VARCHAR(20), skipping ALTER.");
            }
        } catch (Exception e) {
            log.warn("Could not check/alter employees.blood_group column: {}", e.getMessage());
        }

        // Drop obsolete enum check constraints on tenants and tenant_subscriptions
        try {
            log.info("Dropping obsolete check constraints for plan_type if they exist...");
            jdbcTemplate.execute("ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_plan_type_check");
            jdbcTemplate.execute("ALTER TABLE tenant_subscriptions DROP CONSTRAINT IF EXISTS tenant_subscriptions_plan_type_check");
            jdbcTemplate.execute("ALTER TABLE tenants DROP COLUMN IF EXISTS subdomain");
            log.info("Successfully dropped obsolete columns and constraints.");
        } catch (Exception e) {
            log.warn("Could not drop columns or check constraints: {}", e.getMessage());
        }

        // Drop obsolete state enum check constraints dynamically if they exist
        try {
            log.info("Dropping obsolete state CHECK constraints dynamically...");
            jdbcTemplate.execute(
                "DO $$\n" +
                "DECLARE\n" +
                "    r RECORD;\n" +
                "BEGIN\n" +
                "    FOR r IN (\n" +
                "        SELECT tc.table_name, tc.constraint_name\n" +
                "        FROM information_schema.table_constraints tc\n" +
                "        JOIN information_schema.constraint_column_usage ccu \n" +
                "            ON tc.constraint_name = ccu.constraint_name \n" +
                "            AND tc.table_schema = ccu.table_schema\n" +
                "        WHERE tc.constraint_type = 'CHECK'\n" +
                "          AND tc.table_schema = 'public'\n" +
                "          AND (\n" +
                "            (tc.table_name = 'employees' AND ccu.column_name = 'state') OR\n" +
                "            (tc.table_name = 'tenants' AND ccu.column_name = 'state') OR\n" +
                "            (tc.table_name = 'tenant_leave_settings' AND ccu.column_name = 'state') OR\n" +
                "            (tc.table_name = 'platform_state_pt_configs' AND ccu.column_name = 'state_code')\n" +
                "          )\n" +
                "    ) LOOP\n" +
                "        EXECUTE 'ALTER TABLE ' || quote_ident(r.table_name) || ' DROP CONSTRAINT IF EXISTS ' || quote_ident(r.constraint_name);\n" +
                "    END LOOP;\n" +
                "END $$;"
            );
            log.info("Successfully dropped obsolete state CHECK constraints.");
        } catch (Exception e) {
            log.warn("Could not drop state CHECK constraints: {}", e.getMessage());
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

        // Step 6: Seed Statutory Rates and PT Configs
        seedStatutoryRatesAndPtConfigs();
        
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
                    .validityMonths(1)
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
                    .validityMonths(1)
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
                    .validityMonths(1)
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
                    .validityMonths(1)
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
                    .validityMonths(12)
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
        Set<PlatformPermission> allPermissions = new HashSet<>(permissionRepository.findAll());

        return roleRepository.findByName("Super Admin")
                .map(existing -> {
                    // Refresh permissions when new ones have been added to the enum
                    if (existing.getPermissions().size() < allPermissions.size()) {
                        existing.setPermissions(allPermissions);
                        PlatformRole updated = roleRepository.save(existing);
                        log.info("✅ Updated Super Admin role: {} permissions", allPermissions.size());
                        return updated;
                    }
                    log.debug("Super Admin role up to date: {} permissions", existing.getPermissions().size());
                    return existing;
                })
                .orElseGet(() -> {
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

        String envPassword = System.getenv("SONIXHR_SUPER_ADMIN_PASSWORD");
        final String password;
        if (envPassword != null && !envPassword.isBlank()) {
            password = envPassword;
        } else {
            password = java.util.UUID.randomUUID().toString();
            log.warn("=========================================");
            log.warn("SONIXHR_SUPER_ADMIN_PASSWORD env var is not set.");
            log.warn("Generated one-time password: {}", password);
            log.warn("Set SONIXHR_SUPER_ADMIN_PASSWORD before deploying to production.");
            log.warn("=========================================");
        }

        PlatformUser superAdmin = PlatformUser.builder()
                .email(SUPER_ADMIN_EMAIL)
                .password(passwordEncoder.encode(password))
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
        log.info("   Name: {}", SUPER_ADMIN_NAME);
        log.info("   Role: Super Admin (ALL permissions)");
        log.info("   Authorities: {}", superAdmin.getAuthorities().size());
        log.info("=========================================");
    }

    private void createOtherDefaultRoles() {
        // No additional platform roles — only Super Admin is seeded.
        log.info("✅ Platform roles: only Super Admin is seeded.");
    }

    private void seedStatutoryRatesAndPtConfigs() {
        if (statutoryRateConfigRepo.count() == 0) {
            log.info("Seeding statutory rate configurations...");
            LocalDate epoch = LocalDate.of(2020, 1, 1);
            
            statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                    .componentCode("EPF_EE")
                    .rate(BigDecimal.valueOf(0.1200))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(15000.00))
                    .capAmount(BigDecimal.valueOf(1800.00))
                    .effectiveFrom(epoch)
                    .build());

            statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                    .componentCode("EPF_ER")
                    .rate(BigDecimal.valueOf(0.1200))
                    .wageBase("WAGES_BASE")
                    .effectiveFrom(epoch)
                    .build());

            statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                    .componentCode("EPS_ER")
                    .rate(BigDecimal.valueOf(0.0833))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(15000.00))
                    .capAmount(BigDecimal.valueOf(1250.00))
                    .effectiveFrom(epoch)
                    .build());

            statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                    .componentCode("EDLI")
                    .rate(BigDecimal.valueOf(0.0050))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(15000.00))
                    .capAmount(BigDecimal.valueOf(75.00))
                    .effectiveFrom(epoch)
                    .build());

            statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                    .componentCode("ESI_EE")
                    .rate(BigDecimal.valueOf(0.0075))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(21000.00))
                    .effectiveFrom(epoch)
                    .build());

            statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                    .componentCode("ESI_ER")
                    .rate(BigDecimal.valueOf(0.0325))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(21000.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ Statutory rate configurations seeded successfully.");
        }

        LocalDate epoch = LocalDate.of(2020, 1, 1);
        if (!statePtConfigRepo.existsByStateCode(IndianState.KARNATAKA)) {
            log.info("Seeding default state Professional Tax configs for KA...");
            
            // Seed PT slabs for Karnataka (KA)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KARNATAKA)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(24999.99))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KARNATAKA)
                    .salaryRangeMin(BigDecimal.valueOf(24999.99))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for KA seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.MAHARASHTRA)) {
            log.info("Seeding default state Professional Tax configs for MH...");
            
            // Seed PT slabs for Maharashtra (MH)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MAHARASHTRA)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(7500.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MAHARASHTRA)
                    .salaryRangeMin(BigDecimal.valueOf(7500.00))
                    .salaryRangeMax(BigDecimal.valueOf(10000.00))
                    .amount(BigDecimal.valueOf(175.00))
                    .effectiveFrom(epoch)
                    .build());

            // February override rule: 300.00
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MAHARASHTRA)
                    .salaryRangeMin(BigDecimal.valueOf(10000.00))
                    .salaryRangeMax(null)
                    .applicableMonth(2)
                    .amount(BigDecimal.valueOf(300.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MAHARASHTRA)
                    .salaryRangeMin(BigDecimal.valueOf(10000.00))
                    .salaryRangeMax(null)
                    .applicableMonth(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for MH seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.GUJARAT)) {
            log.info("Seeding default state Professional Tax configs for GJ...");
            
            // Seed PT slabs for Gujarat (GJ)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.GUJARAT)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(12000.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.GUJARAT)
                    .salaryRangeMin(BigDecimal.valueOf(12000.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for GJ seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.TELANGANA)) {
            log.info("Seeding default state Professional Tax configs for TS...");
            
            // Seed PT slabs for Telangana (TS)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TELANGANA)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(15000.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TELANGANA)
                    .salaryRangeMin(BigDecimal.valueOf(15000.00))
                    .salaryRangeMax(BigDecimal.valueOf(20000.00))
                    .amount(BigDecimal.valueOf(150.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TELANGANA)
                    .salaryRangeMin(BigDecimal.valueOf(20000.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for TS seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.ANDHRA_PRADESH)) {
            log.info("Seeding default state Professional Tax configs for AP...");
            
            // Seed PT slabs for Andhra Pradesh (AP)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.ANDHRA_PRADESH)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(15000.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.ANDHRA_PRADESH)
                    .salaryRangeMin(BigDecimal.valueOf(15000.00))
                    .salaryRangeMax(BigDecimal.valueOf(20000.00))
                    .amount(BigDecimal.valueOf(150.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.ANDHRA_PRADESH)
                    .salaryRangeMin(BigDecimal.valueOf(20000.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for AP seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.WEST_BENGAL)) {
            log.info("Seeding default state Professional Tax configs for WB...");
            
            // Seed PT slabs for West Bengal (WB)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.WEST_BENGAL)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(10000.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.WEST_BENGAL)
                    .salaryRangeMin(BigDecimal.valueOf(10000.00))
                    .salaryRangeMax(BigDecimal.valueOf(15000.00))
                    .amount(BigDecimal.valueOf(110.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.WEST_BENGAL)
                    .salaryRangeMin(BigDecimal.valueOf(15000.00))
                    .salaryRangeMax(BigDecimal.valueOf(25000.00))
                    .amount(BigDecimal.valueOf(130.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.WEST_BENGAL)
                    .salaryRangeMin(BigDecimal.valueOf(25000.00))
                    .salaryRangeMax(BigDecimal.valueOf(40000.00))
                    .amount(BigDecimal.valueOf(150.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.WEST_BENGAL)
                    .salaryRangeMin(BigDecimal.valueOf(40000.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for WB seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.MADHYA_PRADESH)) {
            log.info("Seeding default state Professional Tax configs for MP...");
            
            // Seed PT slabs for Madhya Pradesh (MP)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MADHYA_PRADESH)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(18750.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MADHYA_PRADESH)
                    .salaryRangeMin(BigDecimal.valueOf(18750.00))
                    .salaryRangeMax(BigDecimal.valueOf(25000.00))
                    .amount(BigDecimal.valueOf(125.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MADHYA_PRADESH)
                    .salaryRangeMin(BigDecimal.valueOf(25000.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(208.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for MP seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.TAMIL_NADU)) {
            log.info("Seeding default state Professional Tax configs for TN...");
            
            // Seed PT slabs for Tamil Nadu (TN)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TAMIL_NADU)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(3500.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TAMIL_NADU)
                    .salaryRangeMin(BigDecimal.valueOf(3500.00))
                    .salaryRangeMax(BigDecimal.valueOf(5000.00))
                    .amount(BigDecimal.valueOf(25.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TAMIL_NADU)
                    .salaryRangeMin(BigDecimal.valueOf(5000.00))
                    .salaryRangeMax(BigDecimal.valueOf(7500.00))
                    .amount(BigDecimal.valueOf(60.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TAMIL_NADU)
                    .salaryRangeMin(BigDecimal.valueOf(7500.00))
                    .salaryRangeMax(BigDecimal.valueOf(10000.00))
                    .amount(BigDecimal.valueOf(135.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TAMIL_NADU)
                    .salaryRangeMin(BigDecimal.valueOf(10000.00))
                    .salaryRangeMax(BigDecimal.valueOf(12500.00))
                    .amount(BigDecimal.valueOf(170.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.TAMIL_NADU)
                    .salaryRangeMin(BigDecimal.valueOf(12500.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for TN seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.KERALA)) {
            log.info("Seeding default state Professional Tax configs for KL...");
            
            // Seed PT slabs for Kerala (KL)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(2000.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(2000.00))
                    .salaryRangeMax(BigDecimal.valueOf(3000.00))
                    .amount(BigDecimal.valueOf(20.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(3000.00))
                    .salaryRangeMax(BigDecimal.valueOf(5000.00))
                    .amount(BigDecimal.valueOf(30.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(5000.00))
                    .salaryRangeMax(BigDecimal.valueOf(7500.00))
                    .amount(BigDecimal.valueOf(50.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(7500.00))
                    .salaryRangeMax(BigDecimal.valueOf(10000.00))
                    .amount(BigDecimal.valueOf(75.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(10000.00))
                    .salaryRangeMax(BigDecimal.valueOf(12500.00))
                    .amount(BigDecimal.valueOf(100.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(12500.00))
                    .salaryRangeMax(BigDecimal.valueOf(16666.67))
                    .amount(BigDecimal.valueOf(125.00))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(16666.67))
                    .salaryRangeMax(BigDecimal.valueOf(20833.33))
                    .amount(BigDecimal.valueOf(166.67))
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KERALA)
                    .salaryRangeMin(BigDecimal.valueOf(20833.33))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(208.33))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for KL seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.ODISHA)) {
            log.info("Seeding default state Professional Tax configs for OD...");
            
            // Seed PT slabs for Odisha (OD)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.ODISHA)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(13000.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.ODISHA)
                    .salaryRangeMin(BigDecimal.valueOf(13000.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for OD seeded successfully.");
        }

        if (!statePtConfigRepo.existsByStateCode(IndianState.CHHATTISGARH)) {
            log.info("Seeding default state Professional Tax configs for CG...");
            
            // Seed PT slabs for Chhattisgarh (CG)
            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.CHHATTISGARH)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(20000.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            statePtConfigRepo.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.CHHATTISGARH)
                    .salaryRangeMin(BigDecimal.valueOf(20000.00))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());
            log.info("✅ State Professional Tax configurations for CG seeded successfully.");
        }
    }
}