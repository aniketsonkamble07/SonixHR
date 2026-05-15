package com.sonixhr.repository;

import com.sonixhr.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, Long> {

    // Find roles belonging to a specific tenant
    List<Role> findByTenantId(UUID tenantId);

    // Check if a role name already exists within a tenant
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    // Find role by tenant and name (useful for updates)
    Optional<Role> findByTenantIdAndName(UUID tenantId, String name);

    // Delete all roles of a tenant (when tenant is deleted)
    void deleteByTenantId(UUID tenantId);

    // Custom query to load role with its permissions eagerly (if not using fetch type EAGER)
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :roleId AND r.tenantId = :tenantId")
    Optional<Role> findByIdWithPermissions(@Param("tenantId") UUID tenantId, @Param("roleId") Long roleId);

    // Find roles that are assigned to a specific user (useful for permission check caching)
    @Query("SELECT u.roles FROM User u WHERE u.id = :userId")
    List<Role> findRolesByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.tenantId = :tenantId")
    List<Role> findByTenantIdWithPermissions(@Param("tenantId") UUID tenantId);
}