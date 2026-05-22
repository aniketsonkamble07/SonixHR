package com.sonixhr.bootstrap;

import com.sonixhr.entity.Permission;
import com.sonixhr.entity.Role;
import com.sonixhr.repository.PermissionRepository;
import com.sonixhr.repository.RoleRepository;
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

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class TenantRoleSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createSuperAdminRoleTemplate();
    }

    private void createSuperAdminRoleTemplate() {
        log.info("Creating Super Admin role template...");

        // Fetch ALL permissions from database
        List<Permission> allPermissionsList = permissionRepository.findAll();
        Set<Permission> allPermissions = new HashSet<>(allPermissionsList);

        log.info("Total permissions found in database: {}", allPermissions.size());

        // Check if Super Admin template already exists
        if (!roleRepository.existsByNameAndTenantIdIsNull("Super Admin")) {
            // Create new Super Admin template with ALL permissions
            Role superAdminTemplate = Role.builder()
                    .tenantId(null)
                    .name("Super Admin")
                    .description("Full tenant access - has ALL permissions")
                    .permissions(allPermissions)
                    .build();
            roleRepository.save(superAdminTemplate);
            log.info("Created Super Admin template with ALL {} permissions", allPermissions.size());
        } else {
            // Update existing Super Admin with any new permissions
            Role existingSuperAdmin = roleRepository.findByNameAndTenantIdIsNull("Super Admin")
                    .orElseThrow();

            // Check if new permissions were added
            if (existingSuperAdmin.getPermissions().size() != allPermissions.size()) {
                existingSuperAdmin.setPermissions(allPermissions);
                roleRepository.save(existingSuperAdmin);
                log.info("Updated Super Admin template with ALL {} permissions", allPermissions.size());
            } else {
                log.info("Super Admin template already exists with {} permissions", allPermissions.size());
            }
        }

        log.info("Super Admin role template created successfully!");
    }
}