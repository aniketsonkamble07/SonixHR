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

        // Fetch permissions if provided
        Set<TenantPermission> permissions = new HashSet<>();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            // ✅ FIXED: Use findAllById instead of findAllByIdInAndTenantId
            permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            if (permissions.size() != request.getPermissionIds().size()) {
                throw new BusinessException("One or more permission IDs are invalid");
            }
        }

        TenantRole role = TenantRole.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .permissions(permissions)
                .createdBy(createdBy)
                .build();

        return roleRepository.save(role);
    }
    @Transactional
    public TenantRole updateRole(Long roleId, TenantRoleCreateRequest request, Long tenantId) {
        log.info("Updating tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        // Check if new name conflicts with existing role in same tenant
        if (!role.getName().equals(request.getName()) &&
                roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException("Role name already exists: " + request.getName());
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());
        if (request.getIsDefault() != null) {
            role.setIsDefault(request.getIsDefault());
        }

        return roleRepository.save(role);
    }

    @Transactional
    public TenantRole updateRolePermissions(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Updating permissions for role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        // ✅ FIXED: Use findAllById instead of findAllByIdInAndTenantId
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

    public TenantRole getRoleByIdAndTenant(Long roleId, Long tenantId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId)
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

        long userCount = employeeRepository.countUsersByRoleIdAndTenantId(roleId, tenantId);
        if (userCount > 0) {
            throw new BusinessException("Cannot delete role that is assigned to " + userCount + " user(s)");
        }

        roleRepository.delete(role);
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

        boolean removed = user.getRoles().removeIf(role -> role.getId().equals(roleId));

        if (!removed) {
            TenantRole role = getRoleByIdAndTenant(roleId, tenantId);
            throw new BusinessException("User does not have role: " + role.getName());
        }

        employeeRepository.save(user);
        log.info("Role removed from user {} in tenant {}", userId, tenantId);
    }

    @Transactional
    public TenantRole setDefaultRole(Long roleId, Long tenantId) {
        log.info("Setting default role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        // Remove default flag from all other roles in this tenant
        List<TenantRole> defaultRoles = roleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
        for (TenantRole defaultRole : defaultRoles) {
            defaultRole.setIsDefault(false);
            roleRepository.save(defaultRole);
        }

        role.setIsDefault(true);
        return roleRepository.save(role);
    }
}