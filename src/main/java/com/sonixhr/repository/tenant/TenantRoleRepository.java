package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TenantRoleRepository extends JpaRepository<TenantRole, Long> {

    // =====================================================
    // EXISTENCE CHECKS
    // =====================================================

    boolean existsByTenantIdAndName(Long tenantId, String name);

    boolean existsByNameAndTenantIdIsNull(String name);

    // =====================================================
    // FIND BY NAME
    // =====================================================

    // Find system role (global template role where tenantId is NULL)
    Optional<TenantRole> findByNameAndTenantIdIsNull(String name);

    // Find tenant-specific role
    Optional<TenantRole> findByTenantIdAndName(Long tenantId, String name);

    // Find any role by name (returns first match)
    Optional<TenantRole> findByName(String name);

    // =====================================================
    // FIND BY ID WITH TENANT
    // =====================================================

    Optional<TenantRole> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT DISTINCT r FROM TenantRole r LEFT JOIN FETCH r.permissions WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<TenantRole> findByIdAndTenantIdWithPermissions(@Param("id") Long id, @Param("tenantId") Long tenantId);

    // =====================================================
    // FIND ALL BY TENANT
    // =====================================================

    List<TenantRole> findAllByTenantId(Long tenantId);

    @Query("SELECT DISTINCT r FROM TenantRole r LEFT JOIN FETCH r.permissions WHERE r.tenantId = :tenantId")
    List<TenantRole> findAllByTenantIdWithPermissions(@Param("tenantId") Long tenantId);

    // =====================================================
    // DEFAULT ROLES
    // =====================================================

    List<TenantRole> findByTenantIdAndIsDefaultTrue(Long tenantId);

    Optional<TenantRole> findByTenantIdAndIsDefaultTrueAndName(Long tenantId, String name);

    // =====================================================
    // SYSTEM ROLES (GLOBAL TEMPLATES)
    // =====================================================

    List<TenantRole> findByTenantIdIsNull();

    @Query("SELECT r FROM TenantRole r WHERE r.tenantId IS NULL ORDER BY r.name")
    List<TenantRole> findAllSystemRoles();

    // =====================================================
    // PERMISSION METHODS (FIXED - These were incorrectly placed)
    // =====================================================

    // Note: These permission-related methods should be in TenantPermissionRepository,
    // not in TenantRoleRepository. But if you need them here, use native queries.

    // Find permissions by IDs (without tenant check)
    @Query("SELECT p FROM TenantPermission p WHERE p.id IN :ids")
    List<TenantPermission> findAllPermissionsByIdIn(@Param("ids") Set<Long> ids);


    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    @Query("SELECT r FROM TenantRole r WHERE r.tenantId = :tenantId AND LOWER(r.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<TenantRole> searchRoles(@Param("tenantId") Long tenantId, @Param("searchTerm") String searchTerm);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    long countByTenantId(Long tenantId);

    long countByTenantIdAndIsDefaultTrue(Long tenantId);

    long countByTenantIdIsNull();
}