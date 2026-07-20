package com.sonixhr.bootstrap;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.IndianState;
import com.sonixhr.common.constant.AppConstants;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.platform.PlatformMigrationFlagRepository;
import com.sonixhr.entity.platform.PlatformMigrationFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.sonixhr.entity.payroll.StatutoryRateConfig;
import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.repository.payroll.StatutoryRateConfigRepository;
import com.sonixhr.repository.payroll.StateProfessionalTaxConfigRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.payroll.SalaryComponentDefinitionRepository;
import com.sonixhr.repository.payroll.TenantSalaryStructureRepository;
import com.sonixhr.repository.payroll.TaxRegimeSlabConfigRepository;
import com.sonixhr.repository.payroll.TenantPayrollConfigRepository;
import com.sonixhr.entity.payroll.TaxRegimeSlabConfig;
import com.sonixhr.entity.payroll.TaxSlabRow;
import com.sonixhr.entity.payroll.SurchargeSlab;
import com.sonixhr.enums.payroll.TaxRegime;
import com.sonixhr.entity.payroll.SalaryComponentDefinition;
import com.sonixhr.entity.payroll.TenantSalaryStructure;
import com.sonixhr.entity.payroll.TenantPayrollConfig;
import com.sonixhr.entity.tenant.Tenant;
import java.util.List;

import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

@Slf4j
@Component // Trigger save to resolve temporary IDE syntax parsing cache issues
@RequiredArgsConstructor
@Order(1)
@SuppressWarnings("null")
public class PlatformDataInitializer implements ApplicationRunner {

        private final PlatformPermissionRepository permissionRepository;
        private final PlatformRoleRepository roleRepository;
        private final PlatformUserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JdbcTemplate jdbcTemplate;
        private final StatutoryRateConfigRepository statutoryRateConfigRepo;
        private final StateProfessionalTaxConfigRepository statePtConfigRepo;
        private final TaxRegimeSlabConfigRepository taxSlabConfigRepo;
        private final TenantRepository tenantRepository;
        private final SalaryComponentDefinitionRepository componentDefinitionRepo;
        private final TenantSalaryStructureRepository tenantSalaryStructureRepo;
        private final TenantPayrollConfigRepository tenantPayrollConfigRepo;
        private final PlatformMigrationFlagRepository platformMigrationFlagRepo;

        private static final String SUPER_ADMIN_EMAIL = "admin@sonixhr.com";
        private static final String FY_2025_26 = "2025-26";
        private static final String FY_2026_27 = "2026-27";
        private static final String WAGES_BASE = "WAGES_BASE";

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                log.info(AppConstants.DIVIDER);
                log.info("Platform Data Initializer Started");
                log.info(AppConstants.DIVIDER);
                // Step 1: Create all permissions
                createAllPermissions();

                // Step 2: Create Admin role with ALL permissions
                PlatformRole adminRole = createAdminRole();

                // Step 3: Create Admin user
                createAdminUser(adminRole);

                // Step 4: Create other default roles (optional)
                createOtherDefaultRoles();

                // Step 6: Seed Statutory Rates and PT Configs
                seedStatutoryRatesAndPtConfigs();

                // Step 7: Sync existing Platform Admin & Tenant Admin roles with all Permissions to prevent 403 authorization issues
                try {
                        log.info("Syncing Platform Admin and Tenant Admin roles with all Permissions...");
                        jdbcTemplate.execute("""
                                INSERT INTO platform_role_permissions (role_id, permission_id)
                                SELECT r.id, p.id
                                FROM platform_roles r
                                CROSS JOIN platform_permissions p
                                WHERE r.name = 'Admin'
                                  AND NOT EXISTS (
                                      SELECT 1 FROM platform_role_permissions prp
                                      WHERE prp.role_id = r.id AND prp.permission_id = p.id
                                  );

                                INSERT INTO role_tenant_permissions (role_id, permission_id)
                                SELECT r.id, p.id
                                FROM tenant_roles r
                                CROSS JOIN tenant_permissions p
                                WHERE r.name = 'Admin'
                                  AND NOT EXISTS (
                                      SELECT 1 FROM role_tenant_permissions rtp
                                      WHERE rtp.role_id = r.id AND rtp.permission_id = p.id
                                  );
                                """);
                        log.info("Successfully synced Platform Admin and Tenant Admin roles with all permissions.");
                } catch (Exception e) {
                        log.warn("Failed to sync Platform/Tenant Admin roles: {}", e.getMessage());
                }

                try {
                        log.info("Dropping legacy plan_status check constraints if they exist...");
                        jdbcTemplate.execute("ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_plan_status_check;");
                        jdbcTemplate.execute("ALTER TABLE tenant_subscriptions DROP CONSTRAINT IF EXISTS tenant_subscriptions_plan_status_check;");
                        log.info("Successfully dropped legacy plan_status check constraints.");
                } catch (Exception e) {
                        log.warn("Failed to drop legacy plan_status check constraints: {}", e.getMessage());
                }

                log.info(AppConstants.DIVIDER);
                log.info("Platform Data Initializer Completed");
                log.info(AppConstants.DIVIDER);
        }

        // KEEP ONLY ONE of these methods
        private void createAllPermissions() {
                log.info("Creating all permissions...");
                int createdCount = 0;

                for (PlatformPermissionEnum permEnum : PlatformPermissionEnum.values()) {
                        // Use .name() to convert enum to String
                        if (permissionRepository.findByPermission(permEnum.name()).isEmpty()) {
                                PlatformPermission permission = PlatformPermission.builder()
                                                .permission(permEnum.name()) // Store enum name as String
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
                log.info("Created {} new permissions. Total: {}", createdCount, permissionRepository.count());
        }

        private PlatformRole createAdminRole() {
                log.info("Creating Admin role...");
                Set<PlatformPermission> allPermissions = new HashSet<>(permissionRepository.findAll());

                return roleRepository.findByName("Admin")
                                .map(existing -> {
                                        // Refresh permissions when new ones have been added to the enum
                                        if (existing.getPermissions().size() < allPermissions.size()) {
                                                existing.setPermissions(allPermissions);
                                                PlatformRole updated = roleRepository.save(existing);
                                                log.info(" Updated Admin role: {} permissions",
                                                                allPermissions.size());
                                                return updated;
                                        }
                                        log.debug("Admin role up to date: {} permissions",
                                                        existing.getPermissions().size());
                                        return existing;
                                })
                                .orElseGet(() -> {
                                        PlatformRole role = PlatformRole.builder()
                                                        .name("Admin")
                                                        .description("Full platform access - has ALL permissions")
                                                        .systemRole(true)
                                                        .active(true)
                                                        .permissions(allPermissions)
                                                        .build();
                                        PlatformRole saved = roleRepository.save(role);
                                        log.info(" Created Admin role with {} permissions",
                                                        allPermissions.size());
                                        return saved;
                                });
        }

        private void createAdminUser(PlatformRole adminRole) {
                log.info("Creating Admin user...");

                if (userRepository.findByEmail(SUPER_ADMIN_EMAIL).isPresent()) {
                        log.info("Admin user already exists: {}", SUPER_ADMIN_EMAIL);
                        PlatformUser existing = userRepository.findByEmail(SUPER_ADMIN_EMAIL).get();
                        String envPassword = System.getenv("SONIXHR_SUPER_ADMIN_PASSWORD");
                        String password = (envPassword != null && !envPassword.isBlank()) ? envPassword : "Admin@123";
                        existing.setPassword(passwordEncoder.encode(password));
                        userRepository.save(existing);
                        log.info("Admin password updated to match current configuration.");
                        return;
                }

                String envPassword = System.getenv("SONIXHR_SUPER_ADMIN_PASSWORD");
                final String password;
                if (envPassword != null && !envPassword.isBlank()) {
                        password = envPassword;
                } else {
                        password = "Admin@123";
                        log.warn(AppConstants.DIVIDER);
                        log.warn("SONIXHR_SUPER_ADMIN_PASSWORD env var is not set. Defaulting to Admin@123.");
                        log.warn(AppConstants.DIVIDER);
                }

                PlatformUser adminUser = PlatformUser.builder()
                                .email(SUPER_ADMIN_EMAIL)
                                .password(passwordEncoder.encode(password))
                                .fullName("Administrator")
                                .designation("Administrator")
                                .status(UserStatus.ACTIVE)
                                .rolesVersion(1)
                                .build();

                adminUser.getRoles().add(adminRole);
                userRepository.save(adminUser);

                log.info(AppConstants.DIVIDER);
                log.info(" ADMIN CREATED WITH ALL PERMISSIONS!");
                log.info("   Email: {}", SUPER_ADMIN_EMAIL);
                log.info("   Name: Administrator");
                log.info("   Role: Admin (ALL permissions)");
                log.info("   Authorities: {}", adminUser.getAuthorities().size());
                log.info(AppConstants.DIVIDER);
        }

        private void createOtherDefaultRoles() {
                // No additional platform roles — only Admin is seeded.
                log.info(" Platform roles: only Admin is seeded.");
        }

        private void seedStatutoryRatesAndPtConfigs() {
                if (statutoryRateConfigRepo.count() == 0) {
                        log.info("Seeding statutory rate configurations...");
                        LocalDate epoch = LocalDate.of(2020, Month.JANUARY, 1);

                        statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                                        .componentCode("EPF_EE")
                                        .rate(BigDecimal.valueOf(0.1200))
                                        .wageBase(WAGES_BASE)
                                        .ceilingAmount(BigDecimal.valueOf(15000.00))
                                        .capAmount(BigDecimal.valueOf(1800.00))
                                        .effectiveFrom(epoch)
                                        .build());

                        statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                                        .componentCode("EPF_ER")
                                        .rate(BigDecimal.valueOf(0.1200))
                                        .wageBase(WAGES_BASE)
                                        .effectiveFrom(epoch)
                                        .build());

                        statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                                        .componentCode("EPS_ER")
                                        .rate(BigDecimal.valueOf(0.0833))
                                        .wageBase(WAGES_BASE)
                                        .ceilingAmount(BigDecimal.valueOf(15000.00))
                                        .capAmount(BigDecimal.valueOf(1250.00))
                                        .effectiveFrom(epoch)
                                        .build());

                        statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                                        .componentCode("EDLI")
                                        .rate(BigDecimal.valueOf(0.0050))
                                        .wageBase(WAGES_BASE)
                                        .ceilingAmount(BigDecimal.valueOf(15000.00))
                                        .capAmount(BigDecimal.valueOf(75.00))
                                        .effectiveFrom(epoch)
                                        .build());

                        statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                                        .componentCode("ESI_EE")
                                        .rate(BigDecimal.valueOf(0.0075))
                                        .wageBase(WAGES_BASE)
                                        .ceilingAmount(BigDecimal.valueOf(21000.00))
                                        .effectiveFrom(epoch)
                                        .build());

                        statutoryRateConfigRepo.save(StatutoryRateConfig.builder()
                                        .componentCode("ESI_ER")
                                        .rate(BigDecimal.valueOf(0.0325))
                                        .wageBase(WAGES_BASE)
                                        .ceilingAmount(BigDecimal.valueOf(21000.00))
                                        .effectiveFrom(epoch)
                                        .build());
                        log.info(" Statutory rate configurations seeded successfully.");
                }

                LocalDate epoch = LocalDate.of(2020, Month.JANUARY, 1);
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
                        log.info(" State Professional Tax configurations for KA seeded successfully.");
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
                        log.info(" State Professional Tax configurations for MH seeded successfully.");
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
                        log.info(" State Professional Tax configurations for GJ seeded successfully.");
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
                        log.info(" State Professional Tax configurations for TS seeded successfully.");
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
                        log.info(" State Professional Tax configurations for AP seeded successfully.");
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
                        log.info(" State Professional Tax configurations for WB seeded successfully.");
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
                        log.info(" State Professional Tax configurations for MP seeded successfully.");
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
                        log.info(" State Professional Tax configurations for TN seeded successfully.");
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
                        log.info(" State Professional Tax configurations for KL seeded successfully.");
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
                        log.info(" State Professional Tax configurations for OD seeded successfully.");
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
                        log.info(" State Professional Tax configurations for CG seeded successfully.");
                }

                seedTaxRegimeSlabConfigs();
                backfillTenantTdsComponents();
        }

        private void seedTaxRegimeSlabConfigs() {
                log.info("Checking/seeding platform tax slab configurations...");

                // FY 2025-26 NEW_REGIME (₹7L threshold, ₹25k rebate, ₹50k standard deduction,
                // slabs at 3L intervals)
                if (taxSlabConfigRepo.findByFinancialYearAndRegime(FY_2025_26, TaxRegime.NEW_REGIME).isEmpty()) {
                        log.info("Seeding NEW_REGIME slab config for FY 2025-26...");
                        List<TaxSlabRow> slabs = List.of(
                                        new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(300000.0), BigDecimal.ZERO),
                                        new TaxSlabRow(BigDecimal.valueOf(300000.0), BigDecimal.valueOf(600000.0),
                                                        BigDecimal.valueOf(5.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(600000.0), BigDecimal.valueOf(900000.0),
                                                        BigDecimal.valueOf(10.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(900000.0), BigDecimal.valueOf(1200000.0),
                                                        BigDecimal.valueOf(15.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(1200000.0), BigDecimal.valueOf(1500000.0),
                                                        BigDecimal.valueOf(20.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(1500000.0), null, BigDecimal.valueOf(30.0)));

                        List<SurchargeSlab> surchargeSlabs = List.of(
                                        new SurchargeSlab(BigDecimal.valueOf(5000000.0), BigDecimal.valueOf(10.0)),
                                        new SurchargeSlab(BigDecimal.valueOf(10000000.0), BigDecimal.valueOf(15.0)),
                                        new SurchargeSlab(BigDecimal.valueOf(20000000.0), BigDecimal.valueOf(25.0)));

                        taxSlabConfigRepo.save(TaxRegimeSlabConfig.builder()
                                        .financialYear(FY_2025_26)
                                        .regime(TaxRegime.NEW_REGIME)
                                        .slabs(slabs)
                                        .standardDeduction(BigDecimal.valueOf(50000.0))
                                        .rebateLimit(BigDecimal.valueOf(700000.0))
                                        .rebateMaxAmount(BigDecimal.valueOf(25000.0))
                                        .cessPercent(BigDecimal.valueOf(4.0))
                                        .surchargeSlabs(surchargeSlabs)
                                        .build());
                }

                // FY 2026-27 NEW_REGIME (₹12L threshold, ₹60k rebate, ₹75k standard deduction,
                // slabs at 4L intervals)
                if (taxSlabConfigRepo.findByFinancialYearAndRegime(FY_2026_27, TaxRegime.NEW_REGIME).isEmpty()) {
                        log.info("Seeding NEW_REGIME slab config for FY 2026-27...");
                        List<TaxSlabRow> slabs = List.of(
                                        new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(400000.0), BigDecimal.ZERO),
                                        new TaxSlabRow(BigDecimal.valueOf(400000.0), BigDecimal.valueOf(800000.0),
                                                        BigDecimal.valueOf(5.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(800000.0), BigDecimal.valueOf(1200000.0),
                                                        BigDecimal.valueOf(10.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(1200000.0), BigDecimal.valueOf(1600000.0),
                                                        BigDecimal.valueOf(15.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(1600000.0), BigDecimal.valueOf(2000000.0),
                                                        BigDecimal.valueOf(20.0)),
                                        new TaxSlabRow(BigDecimal.valueOf(2000000.0), null, BigDecimal.valueOf(30.0)));

                        List<SurchargeSlab> surchargeSlabs = List.of(
                                        new SurchargeSlab(BigDecimal.valueOf(5000000.0), BigDecimal.valueOf(10.0)),
                                        new SurchargeSlab(BigDecimal.valueOf(10000000.0), BigDecimal.valueOf(15.0)),
                                        new SurchargeSlab(BigDecimal.valueOf(20000000.0), BigDecimal.valueOf(25.0)));

                        taxSlabConfigRepo.save(TaxRegimeSlabConfig.builder()
                                        .financialYear(FY_2026_27)
                                        .regime(TaxRegime.NEW_REGIME)
                                        .slabs(slabs)
                                        .standardDeduction(BigDecimal.valueOf(75000.0))
                                        .rebateLimit(BigDecimal.valueOf(1200000.0))
                                        .rebateMaxAmount(BigDecimal.valueOf(60000.0))
                                        .cessPercent(BigDecimal.valueOf(4.0))
                                        .surchargeSlabs(surchargeSlabs)
                                        .build());
                }

                // OLD_REGIME seeding for both years
                String[] financialYears = { FY_2025_26, FY_2026_27 };
                for (String fy : financialYears) {
                        if (taxSlabConfigRepo.findByFinancialYearAndRegime(fy, TaxRegime.OLD_REGIME).isEmpty()) {
                                log.info("Seeding OLD_REGIME slab config for FY {}...", fy);
                                List<TaxSlabRow> slabs = List.of(
                                                new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(250000.0),
                                                                BigDecimal.ZERO),
                                                new TaxSlabRow(BigDecimal.valueOf(250000.0),
                                                                BigDecimal.valueOf(500000.0), BigDecimal.valueOf(5.0)),
                                                new TaxSlabRow(BigDecimal.valueOf(500000.0),
                                                                BigDecimal.valueOf(1000000.0),
                                                                BigDecimal.valueOf(20.0)),
                                                new TaxSlabRow(BigDecimal.valueOf(1000000.0), null,
                                                                BigDecimal.valueOf(30.0)));

                                List<SurchargeSlab> surchargeSlabs = List.of(
                                                new SurchargeSlab(BigDecimal.valueOf(5000000.0),
                                                                BigDecimal.valueOf(10.0)),
                                                new SurchargeSlab(BigDecimal.valueOf(10000000.0),
                                                                BigDecimal.valueOf(15.0)),
                                                new SurchargeSlab(BigDecimal.valueOf(20000000.0),
                                                                BigDecimal.valueOf(25.0)));

                                taxSlabConfigRepo.save(TaxRegimeSlabConfig.builder()
                                                .financialYear(fy)
                                                .regime(TaxRegime.OLD_REGIME)
                                                .slabs(slabs)
                                                .standardDeduction(BigDecimal.valueOf(50000.0))
                                                .rebateLimit(BigDecimal.valueOf(500000.0))
                                                .rebateMaxAmount(BigDecimal.valueOf(12500.0))
                                                .cessPercent(BigDecimal.valueOf(4.0))
                                                .surchargeSlabs(surchargeSlabs)
                                                .build());
                        }
                }
        }

        private void backfillTenantTdsComponents() {
                log.info("Checking if tenant TDS component backfill is required...");
                if (platformMigrationFlagRepo.existsById("TDS_BACKFILLED")) {
                        log.info("TDS component backfill sentinel exists, skipping.");
                        return;
                }

                log.info("Performing TDS component backfill for all tenants...");
                List<Tenant> tenants = tenantRepository.findAll();
                LocalDate epoch = LocalDate.of(2020, Month.JANUARY, 1);

                for (Tenant tenant : tenants) {
                        List<TenantPayrollConfig> configs = tenantPayrollConfigRepo.findActiveByTenant(tenant.getId());
                        for (TenantPayrollConfig config : configs) {
                                // 1. Backfill SalaryComponentDefinition
                                if (!componentDefinitionRepo.existsByTenantIdAndComponentCode(tenant.getId(), "TDS")) {
                                        log.info("Seeding TDS Component Definition for tenant ID: {}", tenant.getId());
                                        componentDefinitionRepo.save(SalaryComponentDefinition.builder()
                                                        .tenant(tenant)
                                                        .tenantPayrollConfigId(config.getId())
                                                        .componentCode("TDS")
                                                        .componentName("Tax Deducted at Source")
                                                        .componentType("DEDUCTION")
                                                        .calculationType("FORMULA")
                                                        .defaultValue(BigDecimal.ZERO)
                                                        .formulaExpression(null)
                                                        .evaluationOrder(99)
                                                        .isLopApplicable(false)
                                                        .isEmployerContribution(false)
                                                        .isMandatory(true)
                                                        .allowEmployeeOverride(false)
                                                        .isAllowedByTenant(true)
                                                        .effectiveFrom(epoch)
                                                        .isActive(true)
                                                        .build());
                                }

                                // 2. Backfill TenantSalaryStructure
                                boolean structureExists = tenantSalaryStructureRepo
                                                .findActiveByTenantAndDate(tenant.getId(), LocalDate.now())
                                                .stream().anyMatch(s -> "TDS".equalsIgnoreCase(s.getComponentCode()));
                                if (!structureExists) {
                                        log.info("Seeding TDS TenantSalaryStructure row for tenant ID: {}",
                                                        tenant.getId());
                                        tenantSalaryStructureRepo.save(TenantSalaryStructure.builder()
                                                        .tenant(tenant)
                                                        .tenantPayrollConfigId(config.getId())
                                                        .componentCode("TDS")
                                                        .calculationType("FORMULA")
                                                        .value(BigDecimal.ZERO)
                                                        .evaluationOrder(99)
                                                        .isPartOfPfWages(false)
                                                        .isPartOfEsiWages(false)
                                                        .isTaxable(false)
                                                        .effectiveFrom(epoch)
                                                        .build());
                                }
                        }
                }

                platformMigrationFlagRepo.save(PlatformMigrationFlag.builder()
                                .flagKey("TDS_BACKFILLED")
                                .executedAt(java.time.LocalDateTime.now())
                                .build());
                log.info("TDS component backfill completed and sentinel registered.");
        }
}