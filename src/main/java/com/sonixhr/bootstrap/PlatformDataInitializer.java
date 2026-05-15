package com.sonixhr.bootstrap;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.AdminPermission;
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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class PlatformDataInitializer implements ApplicationRunner {

    private final PlatformPermissionRepository permissionRepository;
    private final PlatformRoleRepository roleRepository;
    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createPermissionsIfMissing();
        createSuperAdminRoleIfMissing();
        createSuperAdminUserIfMissing();
    }

    private void createPermissionsIfMissing() {
        log.info("Seeding platform permissions...");
        for (AdminPermission adminPerm : AdminPermission.values()) {
            if (!permissionRepository.existsByName(adminPerm)) {
                PlatformPermission perm = PlatformPermission.builder()
                        .name(adminPerm)
                        .description(adminPerm.getDescription())
                        .build();
                permissionRepository.save(perm);
                log.debug("Created permission: {}", adminPerm.name());
            }
        }
        log.info("Platform permissions seeding completed.");
    }

    private void createSuperAdminRoleIfMissing() {
        Set<PlatformPermission> allPermissions = new HashSet<>(permissionRepository.findAll());
        if (allPermissions.isEmpty()) {
            log.warn("No permissions found – run createPermissionsIfMissing first.");
            return;
        }

        PlatformRole superRole = roleRepository.findByName("Super Admin").orElse(null);
        if (superRole == null) {
            superRole = PlatformRole.builder()
                    .name("Super Admin")
                    .description("Full platform access – manages everything")
                    .permissions(allPermissions)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            roleRepository.save(superRole);
            log.info("Created Super Admin role with {} permissions.", allPermissions.size());
        } else {
            //  Update existing role to have all permissions (in case new ones were added)
            superRole.setPermissions(allPermissions);
            roleRepository.save(superRole);
            log.info("Updated Super Admin role to have {} permissions.", allPermissions.size());
        }
    }

    private void createSuperAdminUserIfMissing() {
        final String DEFAULT_EMAIL = "admin@sonixhr.com";
        if (userRepository.findByEmail(DEFAULT_EMAIL).isPresent()) {
            log.info("Default super admin user already exists: {}", DEFAULT_EMAIL);
            return;
        }

        PlatformRole superRole = roleRepository.findByName("Super Admin")
                .orElseThrow(() -> new IllegalStateException("Super Admin role not found – seeding order problem?"));

        PlatformUser superAdmin = PlatformUser.builder()
                .id(UUID.randomUUID())
                .email(DEFAULT_EMAIL)
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .fullName("System Administrator")
                .active(true)
                .roles(Set.of(superRole))
                .build();
        userRepository.save(superAdmin);
        log.info("Default super admin created: {} / password: Admin@123", DEFAULT_EMAIL);
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        log.warn("!! PLEASE CHANGE THE DEFAULT PASSWORD AFTER FIRST LOGIN !!");
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }
}