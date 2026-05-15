package com.sonixhr.service;

import com.sonixhr.entity.Permission;
import com.sonixhr.entity.Role;
import com.sonixhr.exceptions.RoleExistException;
import com.sonixhr.repository.PermissionRepository;
import com.sonixhr.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public Role createRole(UUID tenantId, String name, String description, Set<Long> permissionIds) {
        if (roleRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new RoleExistException("Role name already exists in this tenant");
        }

        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        if (permissions.size() != permissionIds.size()) {
            throw new IllegalArgumentException("One or more permission IDs are invalid");
        }

        Role role = Role.builder()
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .permissions(permissions)
                .build();

        return roleRepository.save(role);
    }

    @Transactional(readOnly = true)
    public List<Role> getRolesForTenant(UUID tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }

    // Optional: get roles with permissions preloaded
    @Transactional(readOnly = true)
    public List<Role> getRolesForTenantWithPermissions(UUID tenantId) {
        return roleRepository.findByTenantIdWithPermissions(tenantId);
    }
}