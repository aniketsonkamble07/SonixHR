package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformRoleCreateRequest;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.exceptions.DuplicateException;
import com.sonixhr.exceptions.RoleNotFoundException;

import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformRoleService {

    private final PlatformRoleRepository roleRepository;
    private final PlatformPermissionRepository permissionRepository;

    @Transactional
    public PlatformRole createRole(PlatformRoleCreateRequest request, UUID createdBy) {
        log.info("Creating platform role: name={}, createdBy={}", request.getName(), createdBy);

        if (roleRepository.existsByName(request.getName())) {
            log.warn("Role creation failed - name already exists: {}", request.getName());
            throw new DuplicateException("Role name already exists: " + request.getName());
        }

        log.debug("Fetching permissions for IDs: {}", request.getPermissionIds());
        Set<PlatformPermission> permissions = new HashSet<>(
                permissionRepository.findAllById(request.getPermissionIds())
        );
        if (permissions.size() != request.getPermissionIds().size()) {
            log.error("Permission ID validation failed: expected {} valid IDs, got {} valid IDs",
                    request.getPermissionIds().size(), permissions.size());
            throw new IllegalArgumentException("Some permission IDs are invalid");
        }

        PlatformRole role = PlatformRole.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(createdBy)
                .createdAt(java.time.LocalDateTime.now())
                .permissions(permissions)
                .build();

        PlatformRole savedRole = roleRepository.save(role);
        log.info("Platform role created successfully: id={}, name={}", savedRole.getId(), savedRole.getName());
        return savedRole;
    }

    @Transactional
    public PlatformRole updateRolePermissions(Long roleId, Set<Long> permissionIds) {
        log.info("Updating permissions for platform role: roleId={}, permissionIds={}", roleId, permissionIds);

        PlatformRole role = roleRepository.findByIdWithPermissions(roleId)
                .orElseThrow(() -> {
                    log.warn("Role not found for permission update: roleId={}", roleId);
                    return new RoleNotFoundException("Role not found");
                });

        log.debug("Current permissions for role {}: {}", roleId, role.getPermissions());

        Set<PlatformPermission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        if (permissions.size() != permissionIds.size()) {
            log.error("Permission ID validation failed for update: expected {} valid IDs, got {}",
                    permissionIds.size(), permissions.size());
            throw new IllegalArgumentException("Some permission IDs are invalid");
        }

        role.setPermissions(permissions);
        PlatformRole updatedRole = roleRepository.save(role);
        log.info("Role permissions updated successfully: roleId={}, newPermissionCount={}",
                updatedRole.getId(), updatedRole.getPermissions().size());
        return updatedRole;
    }

    @Transactional(readOnly = true)
    public List<PlatformRole> getAllRolesWithPermissions() {
        log.debug("Fetching all platform roles with permissions");
        List<PlatformRole> roles = roleRepository.findAllWithPermissions();
        log.info("Retrieved {} platform roles", roles.size());
        return roles;
    }

    @Transactional(readOnly = true)
    public PlatformRole getRoleById(Long roleId) {
        log.debug("Fetching platform role by id: {}", roleId);
        PlatformRole role = roleRepository.findByIdWithPermissions(roleId)
                .orElseThrow(() -> {
                    log.warn("Role not found: id={}", roleId);
                    return new RoleNotFoundException("Role not found");
                });
        log.info("Platform role retrieved: id={}, name={}, permissionCount={}",
                role.getId(), role.getName(), role.getPermissions().size());
        return role;
    }

    @Transactional
    public void deleteRole(Long roleId) {
        log.info("Deleting platform role: id={}", roleId);
        // optional: check if any platform user has this role
        if (roleRepository.existsById(roleId)) {
            roleRepository.deleteById(roleId);
            log.info("Platform role deleted successfully: id={}", roleId);
        } else {
            log.warn("Attempted to delete non-existent role: id={}", roleId);
            throw new RoleNotFoundException("Role not found");
        }
    }
}