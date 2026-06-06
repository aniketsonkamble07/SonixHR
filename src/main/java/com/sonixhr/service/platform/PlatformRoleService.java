package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformRoleCreateRequest;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformRoleService {

    private final PlatformRoleRepository roleRepository;
    private final PlatformPermissionRepository permissionRepository;
    private final PlatformUserRepository userRepository;

    @Transactional
    public PlatformRole createRole(PlatformRoleCreateRequest request, Long tenantId, Long createdBy) {
        log.info("Creating platform role: {} for tenant: {}", request.getName(), tenantId);

        if (roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException("Role already exists: " + request.getName() + " for this tenant");
        }

        // Fetch permissions if provided (must belong to same tenant)
        Set<PlatformPermission> permissions = new HashSet<>();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = new HashSet<>(permissionRepository.findAllByIdAndTenantId(request.getPermissionIds(), tenantId));
            if (permissions.size() != request.getPermissionIds().size()) {
                throw new BusinessException("One or more permission IDs are invalid for this tenant");
            }
        }

        PlatformRole role = PlatformRole.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .isSystemRole(false)
                .permissions(permissions)
                .createdBy(createdBy)
                .build();

        return roleRepository.save(role);
    }

    @Transactional
    public PlatformRole updateRole(Long roleId, PlatformRoleCreateRequest request, Long tenantId) {
        log.info("Updating platform role: {} for tenant: {}", roleId, tenantId);

        PlatformRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot modify system role");
        }

        // Check if new name conflicts with existing role in same tenant
        if (!role.getName().equals(request.getName()) &&
                roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException("Role name already exists: " + request.getName());
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());

        return roleRepository.save(role);
    }

    @Transactional
    public PlatformRole updateRolePermissions(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Updating permissions for role: {} in tenant: {}", roleId, tenantId);

        PlatformRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot modify permissions of system role");
        }

        Set<PlatformPermission> permissions = new HashSet<>(
                permissionRepository.findAllByIdAndTenantId(permissionIds, tenantId)
        );

        if (permissions.size() != permissionIds.size()) {
            Set<Long> foundIds = permissions.stream().map(PlatformPermission::getId).collect(Collectors.toSet());
            Set<Long> missingIds = permissionIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toSet());
            throw new BusinessException("Invalid permission IDs for this tenant: " + missingIds);
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    public PlatformRole getRoleByIdAndTenant(Long roleId, Long tenantId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId + " for tenant: " + tenantId));
    }

    public List<PlatformRole> getAllRolesForTenant(Long tenantId) {
        return roleRepository.findAllByTenantIdWithPermissions(tenantId);
    }

    @Transactional
    public void deleteRole(Long roleId, Long tenantId) {
        log.info("Deleting platform role: {} for tenant: {}", roleId, tenantId);

        PlatformRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot delete system role");
        }

        long userCount = userRepository.countUsersByRoleIdAndTenantId(roleId, tenantId);
        if (userCount > 0) {
            throw new BusinessException("Cannot delete role that is assigned to " + userCount + " user(s)");
        }

        roleRepository.delete(role);
    }

    public List<PlatformUser> getUsersByRole(Long roleId, Long tenantId) {
        log.info("Getting users for role: {} in tenant: {}", roleId, tenantId);
        // Verify role exists for this tenant
        getRoleByIdAndTenant(roleId, tenantId);
        return userRepository.findByRolesIdAndTenantId(roleId, tenantId);
    }

    @Transactional
    public void assignRoleToUser(Long roleId, Long userId, Long tenantId) {
        log.info("Assigning role {} to user {} in tenant {}", roleId, userId, tenantId);

        PlatformRole role = getRoleByIdAndTenant(roleId, tenantId);
        PlatformUser user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId + " for tenant: " + tenantId));

        if (user.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            throw new BusinessException("User already has role: " + role.getName());
        }

        user.getRoles().add(role);
        userRepository.save(user);

        log.info("Role '{}' assigned to user '{}' in tenant {}", role.getName(), user.getEmail(), tenantId);
    }

    @Transactional
    public void removeRoleFromUser(Long roleId, Long userId, Long tenantId) {
        log.info("Removing role {} from user {} in tenant {}", roleId, userId, tenantId);

        PlatformUser user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId + " for tenant: " + tenantId));

        boolean removed = user.getRoles().removeIf(role -> role.getId().equals(roleId));

        if (!removed) {
            PlatformRole role = getRoleByIdAndTenant(roleId, tenantId);
            throw new BusinessException("User does not have role: " + role.getName());
        }

        userRepository.save(user);
        log.info("Role removed from user {} in tenant {}", userId, tenantId);
    }
}