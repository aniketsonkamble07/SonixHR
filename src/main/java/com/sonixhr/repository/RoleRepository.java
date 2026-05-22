package com.sonixhr.repository;

import com.sonixhr.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, Long> {

    // ===== Tenant-specific roles (tenant_id NOT NULL) =====

    // Find roles belonging to a specific tenant
    List<Role> findByTenantId(UUID tenantId);

    // Check if a role name already exists within a tenant
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    // Find role by tenant and name
    Optional<Role> findByTenantIdAndName(UUID tenantId, String name);

    // Delete all roles of a tenant (when tenant is deleted)
    void deleteByTenantId(UUID tenantId);

    // Load role with permissions for a specific tenant
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :roleId AND r.tenantId = :tenantId")
    Optional<Role> findByIdWithPermissions(@Param("tenantId") UUID tenantId, @Param("roleId") Long roleId);

    // Find all roles for a tenant with permissions preloaded
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.tenantId = :tenantId")
    List<Role> findByTenantIdWithPermissions(@Param("tenantId") UUID tenantId);

    // Find roles assigned to a specific user
    @Query("SELECT u.roles FROM User u WHERE u.id = :userId")
    List<Role> findRolesByUserId(@Param("userId") Long userId);


    // ===== Template roles (tenant_id = NULL) for seeding =====

    // Find all template roles (not assigned to any tenant)
    List<Role> findByTenantIdIsNull();

    // Check if a template role exists by name
    boolean existsByNameAndTenantIdIsNull(String name);

    // Find a template role by name
    Optional<Role> findByNameAndTenantIdIsNull(String name);

    // Find template role with permissions
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name AND r.tenantId IS NULL")
    Optional<Role> findTemplateRoleWithPermissions(@Param("name") String name);

    // Copy template roles for a new tenant (returns template roles)
    @Query("SELECT r FROM Role r WHERE r.tenantId IS NULL")
    List<Role> findAllTemplateRoles();
}