package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hibernate.annotations.QueryHints.CACHEABLE;

@Repository
public interface TenantRoleRepository extends JpaRepository<TenantRole, Long> {

    // =====================================================
    // EXISTENCE CHECKS
    // =====================================================

    /**
     * Check if role exists for a specific tenant
     */
    boolean existsByTenantIdAndName(Long tenantId, String name);

    /**
     * Check if system role exists (global template)
     */
    boolean existsByNameAndTenantIdIsNull(String name);

    // =====================================================
    // FIND BY NAME
    // =====================================================

    /**
     * Find system role (global template role where tenantId is NULL)
     */
    Optional<TenantRole> findByNameAndTenantIdIsNull(String name);

    /**
     * Find tenant-specific role
     */
    Optional<TenantRole> findByTenantIdAndName(Long tenantId, String name);

    /**
     * Find any role by name (returns first match - use with caution)
     */
    Optional<TenantRole> findByName(String name);

    // =====================================================
    // FIND BY ID WITH TENANT
    // =====================================================

    /**
     * Find role by ID and tenant ID
     */
    Optional<TenantRole> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Find role by ID and tenant ID with permissions eagerly loaded
     */
    @Query("SELECT DISTINCT r FROM TenantRole r LEFT JOIN FETCH r.permissions WHERE r.id = :id AND r.tenantId = :tenantId")
    @QueryHints(@QueryHint(name = CACHEABLE, value = "true"))
    Optional<TenantRole> findByIdAndTenantIdWithPermissions(@Param("id") Long id, @Param("tenantId") Long tenantId);

    // =====================================================
    // FIND ALL BY TENANT
    // =====================================================

    /**
     * Find all roles for a tenant (with pagination) ✅ FIXED - Added pagination
     */
    Page<TenantRole> findAllByTenantId(Long tenantId, Pageable pageable);

    /**
     * Find all roles for a tenant (without pagination)
     */
    List<TenantRole> findAllByTenantId(Long tenantId);

    /**
     * Find all roles for a tenant with permissions eagerly loaded
     */
    @Query("SELECT DISTINCT r FROM TenantRole r LEFT JOIN FETCH r.permissions WHERE r.tenantId = :tenantId")
    @QueryHints(@QueryHint(name = CACHEABLE, value = "true"))
    List<TenantRole> findAllByTenantIdWithPermissions(@Param("tenantId") Long tenantId);

    // =====================================================
    // DEFAULT ROLES
    // =====================================================

    /**
     * Get all default roles for a tenant (should be one, but returns list for safety)
     */
    List<TenantRole> findByTenantIdAndIsDefaultTrue(Long tenantId);

    /**
     * Get the single default role for a tenant ✅ FIXED - Added this method
     */
    @Query("SELECT r FROM TenantRole r WHERE r.tenantId = :tenantId AND r.isDefault = true")
    Optional<TenantRole> findDefaultRoleByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Find default role by tenant and name
     */
    Optional<TenantRole> findByTenantIdAndIsDefaultTrueAndName(Long tenantId, String name);

    /**
     * Check if tenant has a default role ✅ NEW
     */
    boolean existsByTenantIdAndIsDefaultTrue(Long tenantId);

    // =====================================================
    // SYSTEM ROLES (GLOBAL TEMPLATES)
    // =====================================================

    /**
     * Get all system roles (global templates)
     */
    List<TenantRole> findByTenantIdIsNull();

    /**
     * Get all system roles sorted
     */
    @Query("SELECT r FROM TenantRole r WHERE r.tenantId IS NULL ORDER BY r.name")
    List<TenantRole> findAllSystemRoles();

    /**
     * Get system role with permissions ✅ NEW
     */
    @Query("SELECT DISTINCT r FROM TenantRole r LEFT JOIN FETCH r.permissions WHERE r.tenantId IS NULL AND r.name = :name")
    Optional<TenantRole> findSystemRoleWithPermissions(@Param("name") String name);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    /**
     * Search roles by name
     */
    @Query("SELECT r FROM TenantRole r WHERE r.tenantId = :tenantId AND LOWER(r.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<TenantRole> searchRoles(@Param("tenantId") Long tenantId, @Param("searchTerm") String searchTerm);

    /**
     * Search roles with pagination ✅ NEW
     */
    @Query("SELECT r FROM TenantRole r WHERE r.tenantId = :tenantId AND LOWER(r.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<TenantRole> searchRoles(@Param("tenantId") Long tenantId, @Param("searchTerm") String searchTerm, Pageable pageable);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    /**
     * Count roles by tenant
     */
    long countByTenantId(Long tenantId);

    /**
     * Count default roles by tenant
     */
    long countByTenantIdAndIsDefaultTrue(Long tenantId);

    /**
     * Count system roles
     */
    long countByTenantIdIsNull();

    /**
     * Count roles with permission ✅ NEW
     */
    @Query("SELECT COUNT(DISTINCT r) FROM TenantRole r JOIN r.permissions p WHERE p.id = :permissionId AND r.tenantId = :tenantId")
    long countRolesWithPermission(@Param("tenantId") Long tenantId, @Param("permissionId") Long permissionId);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    /**
     * Delete all roles for a tenant ✅ NEW (use with caution)
     */
    void deleteByTenantId(Long tenantId);
    Optional<TenantRole> findByNameAndTenantId(String name, Long tenantId);

    /**
     * Find roles by IDs with tenant validation ✅ NEW
     */
    @Query("SELECT r FROM TenantRole r WHERE r.id IN :ids AND r.tenantId = :tenantId")
    List<TenantRole> findAllByIdInAndTenantId(@Param("ids") Set<Long> ids, @Param("tenantId") Long tenantId);
}