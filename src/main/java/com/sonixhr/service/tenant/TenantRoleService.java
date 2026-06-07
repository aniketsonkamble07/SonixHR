package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
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
public class TenantRoleService {

    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public TenantRole createRole(TenantRoleCreateRequest request, Long tenantId, Long createdBy) {
        log.info("Creating tenant role: {} for tenant: {}", request.getName(), tenantId);

        if (roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException("Role already exists: " + request.getName() + " for this tenant");
        }

        Set<TenantPermission> permissions = new HashSet<>();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            if (permissions.size() != request.getPermissionIds().size()) {
                throw new BusinessException("One or more permission IDs are invalid");
            }
        }

        boolean isFirstRole = roleRepository.countByTenantId(tenantId) == 0;
        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault() : isFirstRole;

        TenantRole role = TenantRole.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .isDefault(isDefault)
                .permissions(permissions)
                .createdBy(createdBy)
                .build();

        return roleRepository.save(role);
    }

    @Transactional
    public TenantRole updateRole(Long roleId, TenantRoleCreateRequest request, Long tenantId) {
        log.info("Updating tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        // Check if new name conflicts
        if (!role.getName().equals(request.getName()) &&
                roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException("Role name already exists: " + request.getName());
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());

        // Update permissions if provided
        if (request.getPermissionIds() != null) {
            Set<TenantPermission> permissions = new HashSet<>(
                    permissionRepository.findAllById(request.getPermissionIds())
            );
            if (permissions.size() != request.getPermissionIds().size()) {
                throw new BusinessException("One or more permission IDs are invalid");
            }
            role.setPermissions(permissions);
            log.info("Updated permissions for role: {}", roleId);
        }

        // ✅ FIXED: Use existing methods instead of setIsDefault
        if (request.getIsDefault() != null) {
            if (request.getIsDefault() && !role.isDefault()) {
                // Remove default flag from all other roles
                List<TenantRole> defaultRoles = roleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
                for (TenantRole defaultRole : defaultRoles) {
                    if (!defaultRole.getId().equals(roleId)) {
                        defaultRole.removeDefaultFlag();  // ✅ Using existing method
                        roleRepository.save(defaultRole);
                    }
                }
                role.setAsDefault();  // ✅ Using existing method
            } else if (!request.getIsDefault() && role.isDefault()) {
                // Check if we can remove default flag (must have at least one default role)
                long defaultCount = roleRepository.countByTenantIdAndIsDefaultTrue(tenantId);
                if (defaultCount <= 1) {
                    throw new BusinessException("Cannot remove default flag. At least one role must be default.");
                }
                role.removeDefaultFlag();  // ✅ Using existing method
            }
        }

        return roleRepository.save(role);
    }

    @Transactional
    public TenantRole updateRolePermissions(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Updating permissions for role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (permissionIds == null || permissionIds.isEmpty()) {
            role.setPermissions(new HashSet<>());
            return roleRepository.save(role);
        }

        Set<TenantPermission> permissions = new HashSet<>(
                permissionRepository.findAllById(permissionIds)
        );

        if (permissions.size() != permissionIds.size()) {
            Set<Long> foundIds = permissions.stream().map(TenantPermission::getId).collect(Collectors.toSet());
            Set<Long> missingIds = permissionIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());
            throw new BusinessException("Invalid permission IDs: " + missingIds);
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    @Transactional
    public TenantRole addPermissionsToRole(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Adding permissions to role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        Set<TenantPermission> permissions = new HashSet<>(
                permissionRepository.findAllById(permissionIds)
        );

        if (permissions.size() != permissionIds.size()) {
            throw new BusinessException("One or more permission IDs are invalid");
        }

        role.getPermissions().addAll(permissions);
        return roleRepository.save(role);
    }

    @Transactional
    public TenantRole removePermissionsFromRole(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Removing permissions from role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        role.getPermissions().removeIf(p -> permissionIds.contains(p.getId()));
        return roleRepository.save(role);
    }

    public TenantRole getRoleByIdAndTenant(Long roleId, Long tenantId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId + " for tenant: " + tenantId));
    }

    public TenantRole getRoleByIdWithPermissions(Long roleId, Long tenantId) {
        return roleRepository.findByIdAndTenantIdWithPermissions(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId + " for tenant: " + tenantId));
    }

    public List<TenantRole> getAllRolesForTenant(Long tenantId) {
        return roleRepository.findAllByTenantIdWithPermissions(tenantId);
    }

    public List<TenantRole> getDefaultRolesForTenant(Long tenantId) {
        return roleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
    }

    @Transactional
    public void deleteRole(Long roleId, Long tenantId) {
        log.info("Deleting tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (role.isDefault()) {
            throw new BusinessException("Cannot delete the default role. Set another role as default first.");
        }

        long userCount = employeeRepository.countUsersByRoleIdAndTenantId(roleId, tenantId);
        if (userCount > 0) {
            throw new BusinessException("Cannot delete role that is assigned to " + userCount + " user(s). Remove the role from users first.");
        }

        long totalRoles = roleRepository.countByTenantId(tenantId);
        if (totalRoles <= 1) {
            throw new BusinessException("Cannot delete the last role in tenant. At least one role must exist.");
        }

        roleRepository.delete(role);
        log.info("Role deleted successfully: {}", roleId);
    }

    public List<Employee> getUsersByRole(Long roleId, Long tenantId) {
        log.info("Getting users for role: {} in tenant: {}", roleId, tenantId);
        getRoleByIdAndTenant(roleId, tenantId);
        return employeeRepository.findByRolesIdAndTenantId(roleId, tenantId);
    }

    @Transactional
    public void assignRoleToUser(Long roleId, Long userId, Long tenantId) {
        log.info("Assigning role {} to user {} in tenant {}", roleId, userId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);
        Employee user = employeeRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId + " for tenant: " + tenantId));

        if (user.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            throw new BusinessException("User already has role: " + role.getName());
        }

        user.getRoles().add(role);
        employeeRepository.save(user);

        log.info("Role '{}' assigned to user '{}' in tenant {}", role.getName(), user.getEmail(), tenantId);
    }

    @Transactional
    public void removeRoleFromUser(Long roleId, Long userId, Long tenantId) {
        log.info("Removing role {} from user {} in tenant {}", roleId, userId, tenantId);

        Employee user = employeeRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId + " for tenant: " + tenantId));

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (user.getRoles().size() <= 1 && user.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            throw new BusinessException("User must have at least one role. Cannot remove the last role.");
        }

        boolean removed = user.getRoles().removeIf(r -> r.getId().equals(roleId));

        if (!removed) {
            throw new BusinessException("User does not have role: " + role.getName());
        }

        employeeRepository.save(user);
        log.info("Role '{}' removed from user {} in tenant {}", role.getName(), userId, tenantId);
    }

    @Transactional
    public TenantRole setDefaultRole(Long roleId, Long tenantId) {
        log.info("Setting default role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        // ✅ FIXED: Use existing methods
        List<TenantRole> allRoles = roleRepository.findAllByTenantId(tenantId);
        for (TenantRole r : allRoles) {
            boolean isThisRole = r.getId().equals(roleId);
            if (r.isDefault() != isThisRole) {
                if (isThisRole) {
                    r.setAsDefault();  // ✅ Using existing method
                } else {
                    r.removeDefaultFlag();  // ✅ Using existing method
                }
                roleRepository.save(r);
            }
        }

        return role;
    }

    public long getRoleCountForTenant(Long tenantId) {
        return roleRepository.countByTenantId(tenantId);
    }

    public boolean hasUsersAssigned(Long roleId, Long tenantId) {
        return employeeRepository.countUsersByRoleIdAndTenantId(roleId, tenantId) > 0;
    }
}