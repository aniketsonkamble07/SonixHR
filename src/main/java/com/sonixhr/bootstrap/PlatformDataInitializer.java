package com.sonixhr.bootstrap;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.common.constant.AppConstants;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
@SuppressWarnings("null")
public class PlatformDataInitializer implements ApplicationRunner {

    private final PlatformPermissionRepository permissionRepository;
    private final PlatformRoleRepository roleRepository;
    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // =====================================================
    // CONFIGURABLE PROPERTIES
    // =====================================================

    @Value("${app.platform.admin.email:admin@sonixhr.com}")
    private String adminEmail;

    @Value("${app.platform.admin.name:Platform Administrator}")
    private String adminName;

    @Value("${app.platform.admin.role:Admin}")
    private String adminRoleName;

    @Value("${app.platform.admin.default-password:Admin@2026}")
    private String defaultAdminPassword;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info(AppConstants.DIVIDER);
        log.info("Platform Data Initializer Started");
        log.info(AppConstants.DIVIDER);

        try {
            entityManager.createNativeQuery("ALTER TABLE employees DROP CONSTRAINT IF EXISTS employees_status_check").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE employees DROP CONSTRAINT IF EXISTS employees_resignation_status_check").executeUpdate();
            log.info("Dropped legacy status check constraints on employees table");
        } catch (Exception e) {
            log.warn("Could not drop status check constraints: {}", e.getMessage());
        }

        // Step 1: Create all permissions
        createAllPermissions();

        // Step 2: Create Admin role with ALL permissions
        PlatformRole adminRole = createAdminRole();

        // Step 3: Create Admin user
        createAdminUser(adminRole);

        log.info(AppConstants.DIVIDER);
        log.info("Platform Data Initializer Completed");
        log.info(AppConstants.DIVIDER);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logAdminCredentials() {
        String envPassword = System.getenv("SONIXHR_PLATFORM_ADMIN_PASSWORD");
        String password = (envPassword != null && !envPassword.isBlank()) ? envPassword : defaultAdminPassword;

        log.info(AppConstants.DIVIDER);
        log.info("🔐 PLATFORM ADMIN CREDENTIALS:");
        log.info("   👤 Email: {}", adminEmail);
        log.info("   📛 Name: {}", adminName);
        log.info("   🔑 Role: {}", adminRoleName);
        log.info("   🔐 Password: {}", password);
        log.info("   ⚠️  PLEASE CHANGE THE DEFAULT PASSWORD IN PRODUCTION!");
        log.info(AppConstants.DIVIDER);
    }

    // =====================================================
    // PERMISSION CREATION
    // =====================================================

    private void createAllPermissions() {
        log.info("Creating all platform permissions...");
        int createdCount = 0;

        for (PlatformPermissionEnum permEnum : PlatformPermissionEnum.values()) {
            if (permissionRepository.findByPermission(permEnum.name()).isEmpty()) {
                PlatformPermission permission = PlatformPermission.builder()
                        .permission(permEnum.name())
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

    // =====================================================
    // ADMIN ROLE CREATION
    // =====================================================

    private PlatformRole createAdminRole() {
        log.info("Creating Admin role with ALL permissions...");
        Set<PlatformPermission> allPermissions = new HashSet<>(permissionRepository.findAll());

        return roleRepository.findByName(adminRoleName)
                .map(existing -> {
                    if (existing.getPermissions().size() < allPermissions.size()) {
                        existing.setPermissions(allPermissions);
                        existing.setSystemRole(true);
                        existing.setActive(true);
                        PlatformRole updated = roleRepository.save(existing);
                        log.info("Updated Admin role: {} permissions (ALL)", allPermissions.size());
                        return updated;
                    }
                    log.debug("Admin role up to date: {} permissions", existing.getPermissions().size());
                    return existing;
                })
                .orElseGet(() -> {
                    PlatformRole role = PlatformRole.builder()
                            .name(adminRoleName)
                            .description("Platform Administrator - Full access with ALL permissions")
                            .systemRole(true)
                            .active(true)
                            .permissions(allPermissions)
                            .build();
                    PlatformRole saved = roleRepository.save(role);
                    log.info("Created Admin role with {} permissions (ALL)", allPermissions.size());
                    return saved;
                });
    }

    // =====================================================
    // ADMIN USER CREATION
    // =====================================================

    private void createAdminUser(PlatformRole adminRole) {
        log.info("Creating Platform Admin user...");

        String envPassword = System.getenv("SONIXHR_PLATFORM_ADMIN_PASSWORD");
        final String password = (envPassword != null && !envPassword.isBlank()) ? envPassword : defaultAdminPassword;

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            log.info("Admin user already exists: {}", adminEmail);
            PlatformUser existing = userRepository.findByEmail(adminEmail).get();

            if (!passwordEncoder.matches(password, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(password));
                userRepository.save(existing);
                log.info("Admin password updated.");
            }

            if (!existing.getRoles().contains(adminRole)) {
                existing.getRoles().add(adminRole);
                userRepository.save(existing);
                log.info("Admin role assigned to existing user.");
            }
            return;
        }

        if (envPassword == null || envPassword.isBlank()) {
            log.warn("⚠️  SONIXHR_PLATFORM_ADMIN_PASSWORD env var is not set. Defaulting to configured password.");
        }

        PlatformUser adminUser = PlatformUser.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(password))
                .fullName(adminName)
                .designation("Platform Administrator")
                .status(UserStatus.ACTIVE)
                .rolesVersion(1)
                .build();

        adminUser.getRoles().add(adminRole);
        userRepository.save(adminUser);

        log.info("✅ PLATFORM ADMIN CREATED SUCCESSFULLY!");
        log.info("   👤 Email: {}", adminEmail);
        log.info("   📛 Name: {}", adminName);
        log.info("   🔑 Role: {} (ALL permissions)", adminRoleName);
        log.info("   📋 Authorities: {}", adminUser.getAuthorities().size());
    }
}