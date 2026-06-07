package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformRoleCreateRequest;
import com.sonixhr.dto.platform.PlatformUserResponse;
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
    public PlatformRole createRole(PlatformRoleCreateRequest request, Long createdBy) {
        log.info("Creating platform role: {}", request.getName());

        // Check if role already exists (global)
        if (roleRepository.existsByName(request.getName())) {
            throw new BusinessException("Role already exists: " + request.getName());
        }

        // Fetch permissions if provided
        Set<PlatformPermission> permissions = new HashSet<>();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            if (permissions.size() != request.getPermissionIds().size()) {
                throw new BusinessException("One or more permission IDs are invalid");
            }
        }

        PlatformRole role = PlatformRole.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isSystemRole(false)
                .permissions(permissions)
                .build();

        return roleRepository.save(role);
    }

    @Transactional
    public PlatformRole updateRole(Long roleId, PlatformRoleCreateRequest request) {
        log.info("Updating platform role: {}", roleId);

        PlatformRole role = getRoleById(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot modify system role");
        }

        // Check if new name conflicts with existing role
        if (!role.getName().equals(request.getName()) &&
                roleRepository.existsByName(request.getName())) {
            throw new BusinessException("Role name already exists: " + request.getName());
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());

        return roleRepository.save(role);
    }

    @Transactional
    public PlatformRole updateRolePermissions(Long roleId, Set<Long> permissionIds) {
        log.info("Updating permissions for role: {}", roleId);

        PlatformRole role = getRoleById(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot modify permissions of system role");
        }

        Set<PlatformPermission> permissions = new HashSet<>(
                permissionRepository.findAllById(permissionIds)
        );

        if (permissions.size() != permissionIds.size()) {
            Set<Long> foundIds = permissions.stream().map(PlatformPermission::getId).collect(Collectors.toSet());
            Set<Long> missingIds = permissionIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());
            throw new BusinessException("Invalid permission IDs: " + missingIds);
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    public PlatformRole getRoleById(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));
    }

    public List<PlatformRole> getAllRoles() {
        return roleRepository.findAllWithPermissions();
    }

    @Transactional
    public void deleteRole(Long roleId) {
        log.info("Deleting platform role: {}", roleId);

        PlatformRole role = getRoleById(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot delete system role");
        }

        // Check if role is assigned to any users
        long userCount = userRepository.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new BusinessException("Cannot delete role that is assigned to " + userCount + " user(s)");
        }

        roleRepository.delete(role);
    }

    public List<PlatformUserResponse> getUsersByRole(Long roleId) {
        log.info("Getting users for role: {}", roleId);

        // Verify role exists
        getRoleById(roleId);

        List<PlatformUser> users = userRepository.findByRolesId(roleId);

        return users.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignRoleToUser(Long roleId, Long userId) {
        log.info("Assigning role {} to user {}", roleId, userId);

        PlatformRole role = getRoleById(roleId);
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (user.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            throw new BusinessException("User already has role: " + role.getName());
        }

        user.getRoles().add(role);
        userRepository.save(user);

        log.info("Role '{}' assigned to user '{}'", role.getName(), user.getEmail());
    }

    @Transactional
    public void removeRoleFromUser(Long roleId, Long userId) {
        log.info("Removing role {} from user {}", roleId, userId);

        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        boolean removed = user.getRoles().removeIf(role -> role.getId().equals(roleId));

        if (!removed) {
            PlatformRole role = getRoleById(roleId);
            throw new BusinessException("User does not have role: " + role.getName());
        }

        userRepository.save(user);
        log.info("Role removed from user {}", userId);
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private PlatformUserResponse toUserResponse(PlatformUser user) {
        if (user == null) return null;

        return PlatformUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .designation(user.getDesignation())
                .status(user.getStatus())
                .isActive(user.getStatus().isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles().stream()
                        .map(role -> PlatformUserResponse.PlatformRoleResponse.builder()
                                .id(role.getId())
                                .name(role.getName())
                                .description(role.getDescription())
                                .build())
                        .collect(Collectors.toSet()))
                .build();
    }
}