package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.enums.TenantPermissionEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TenantPermissionRepository extends JpaRepository<TenantPermission, Long> {

    // =====================================================
    // BASIC QUERIES - ALL USE STRING
    // =====================================================

    /**
     * Find permission by permission name (String)
     */
    Optional<TenantPermission> findByPermission(String permission);

    /**
     * Find permissions by multiple permission names (Set of Strings)
     * ONLY THIS VERSION - REMOVE the Enum version
     */
    List<TenantPermission> findByPermissionIn(Set<String> permissions);

    /**
     * Find permissions by category (unsorted)
     */
    List<TenantPermission> findByCategory(String category);

    /**
     * Find permissions by category with sorting
     */
    List<TenantPermission> findByCategoryOrderByDisplayOrderAsc(String category);

    /**
     * Find all permissions sorted by category then display order
     */
    List<TenantPermission> findAllByOrderByCategoryAscDisplayOrderAsc();

    // =====================================================
    // BATCH QUERIES
    // =====================================================

    /**
     * Find permissions by IDs
     */
    @Query("SELECT p FROM TenantPermission p WHERE p.id IN :ids")
    List<TenantPermission> findAllByIdIn(@Param("ids") Set<Long> ids);

    /**
     * Find permissions by IDs with pagination
     */
    @Query("SELECT p FROM TenantPermission p WHERE p.id IN :ids")
    Page<TenantPermission> findByIdIn(@Param("ids") Set<Long> ids, Pageable pageable);

    // =====================================================
    // CATEGORY QUERIES
    // =====================================================

    /**
     * Get all unique categories
     */
    @Query("SELECT DISTINCT p.category FROM TenantPermission p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    /**
     * Get permissions by category with pagination
     */
    Page<TenantPermission> findByCategory(String category, Pageable pageable);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    /**
     * Search permissions by name or description
     */
    @Query("SELECT p FROM TenantPermission p WHERE " +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<TenantPermission> searchPermissions(@Param("searchTerm") String searchTerm);

    /**
     * Search permissions with pagination
     */
    @Query("SELECT p FROM TenantPermission p WHERE " +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<TenantPermission> searchPermissions(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Search permissions by category and term
     */
    @Query("SELECT p FROM TenantPermission p WHERE " +
            "p.category = :category AND (" +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<TenantPermission> searchPermissionsByCategory(@Param("category") String category,
                                                       @Param("searchTerm") String searchTerm);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    /**
     * Count how many roles have this permission
     */
    @Query("SELECT COUNT(r) FROM TenantRole r JOIN r.permissions p WHERE p.id = :permissionId")
    long countRolesByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * Count permissions by category
     */
    @Query("SELECT p.category, COUNT(p) FROM TenantPermission p GROUP BY p.category")
    List<Object[]> countPermissionsByCategory();

    /**
     * Check if permission exists by name (String)
     */
    boolean existsByPermission(String permission);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    /**
     * Delete permissions by IDs (use with caution)
     */
    void deleteByIdIn(Set<Long> ids);

    // =====================================================
    // HELPER METHODS - Convert enums to strings at caller level
    // (No default methods to avoid erasure conflicts)
    // =====================================================
}