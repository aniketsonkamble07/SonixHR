package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.enums.PlatformPermissionEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PlatformPermissionRepository extends JpaRepository<PlatformPermission, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    Optional<PlatformPermission> findByPermission(PlatformPermissionEnum permission);

    List<PlatformPermission> findByPermissionIn(Set<PlatformPermissionEnum> permissions);

    List<PlatformPermission> findByCategory(String category);

    List<PlatformPermission> findByTenantIdOrTenantIdIsNull(Long tenantId);

    List<PlatformPermission> findByTenantId(Long tenantId);

    Page<PlatformPermission> findByTenantId(Long tenantId, Pageable pageable);

    Page<PlatformPermission> findByCategory(String category, Pageable pageable);

    // =====================================================
    // EXISTENCE CHECKS
    // =====================================================

    boolean existsByPermission(PlatformPermissionEnum permission);

    boolean existsByTenantIdAndPermission(Long tenantId, PlatformPermissionEnum permission);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PlatformPermission p " +
            "WHERE p.permission = :permission AND (p.tenantId = :tenantId OR p.tenantId IS NULL)")
    boolean existsByPermissionForTenant(@Param("permission") PlatformPermissionEnum permission,
                                        @Param("tenantId") Long tenantId);

    // =====================================================
    // CATEGORY QUERIES
    // =====================================================

    @Query("SELECT DISTINCT p.category FROM PlatformPermission p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    @Query("SELECT DISTINCT p.category FROM PlatformPermission p WHERE p.category IS NOT NULL AND (p.tenantId = :tenantId OR p.tenantId IS NULL) ORDER BY p.category")
    List<String> findCategoriesByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT p FROM PlatformPermission p WHERE p.category = :category AND (p.tenantId = :tenantId OR p.tenantId IS NULL)")
    List<PlatformPermission> findByCategoryAndTenant(@Param("category") String category,
                                                     @Param("tenantId") Long tenantId);

    // =====================================================
    // ORDERED QUERIES
    // =====================================================

    @Query("SELECT p FROM PlatformPermission p WHERE p.tenantId = :tenantId ORDER BY p.displayOrder")
    List<PlatformPermission> findByTenantIdOrderByDisplayOrder(@Param("tenantId") Long tenantId);

    @Query("SELECT p FROM PlatformPermission p WHERE p.category = :category AND (p.tenantId = :tenantId OR p.tenantId IS NULL) ORDER BY p.displayOrder")
    List<PlatformPermission> findByCategoryAndTenantIdOrderByDisplayOrder(@Param("category") String category,
                                                                          @Param("tenantId") Long tenantId);

    @Query("SELECT p FROM PlatformPermission p WHERE p.tenantId = :tenantId OR p.tenantId IS NULL ORDER BY p.category, p.displayOrder")
    List<PlatformPermission> findAllOrderedByCategoryAndDisplayOrder(@Param("tenantId") Long tenantId);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    @Query("SELECT COUNT(r) FROM PlatformRole r JOIN r.permissions p WHERE p.id = :permissionId")
    long countRolesByPermissionId(@Param("permissionId") Long permissionId);

    @Query("SELECT COUNT(p) FROM PlatformPermission p WHERE p.category = :category AND (p.tenantId = :tenantId OR p.tenantId IS NULL)")
    long countByCategoryAndTenant(@Param("category") String category, @Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(p) FROM PlatformPermission p WHERE p.tenantId = :tenantId OR p.tenantId IS NULL")
    long countByTenant(@Param("tenantId") Long tenantId);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    @Query("SELECT p FROM PlatformPermission p WHERE " +
            "(p.tenantId = :tenantId OR p.tenantId IS NULL) AND " +
            "(LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<PlatformPermission> searchPermissions(@Param("tenantId") Long tenantId,
                                               @Param("searchTerm") String searchTerm);

    @Query("SELECT p FROM PlatformPermission p WHERE " +
            "(p.tenantId = :tenantId OR p.tenantId IS NULL) AND " +
            "(LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<PlatformPermission> searchPermissions(@Param("tenantId") Long tenantId,
                                               @Param("searchTerm") String searchTerm,
                                               Pageable pageable);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE PlatformPermission p SET p.category = :newCategory WHERE p.category = :oldCategory AND p.tenantId = :tenantId")
    int bulkUpdateCategory(@Param("oldCategory") String oldCategory,
                           @Param("newCategory") String newCategory,
                           @Param("tenantId") Long tenantId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PlatformPermission p WHERE p.category = :category AND p.tenantId = :tenantId AND p.id NOT IN (SELECT DISTINCT perm.id FROM PlatformRole r JOIN r.permissions perm)")
    int deletePermissionsByCategoryNotAssigned(@Param("category") String category,
                                               @Param("tenantId") Long tenantId);

    // =====================================================
    // BATCH QUERIES
    // =====================================================

    @Query("SELECT p FROM PlatformPermission p WHERE p.id IN :ids AND (p.tenantId = :tenantId OR p.tenantId IS NULL)")
    List<PlatformPermission> findAllByIdInAndTenant(@Param("ids") Set<Long> ids,
                                                    @Param("tenantId") Long tenantId);

    @Query("SELECT p FROM PlatformPermission p WHERE p.category IN :categories AND (p.tenantId = :tenantId OR p.tenantId IS NULL)")
    List<PlatformPermission> findByCategoriesIn(@Param("categories") Set<String> categories,
                                                @Param("tenantId") Long tenantId);

    // =====================================================
    // VALIDATION QUERIES
    // =====================================================

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM PlatformRole r " +
            "JOIN r.permissions p WHERE p.id = :permissionId AND r.tenantId = :tenantId")
    boolean isPermissionAssignedInTenant(@Param("permissionId") Long permissionId,
                                         @Param("tenantId") Long tenantId);

    // ✅ REMOVED - This doesn't belong here
    // @Query("SELECT p.id FROM PlatformPermission p JOIN p.roles r WHERE r.id = :roleId")
    // Set<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    // =====================================================
    // INITIALIZATION QUERIES
    // =====================================================

    @Query("SELECT p.permission FROM PlatformPermission p WHERE p.tenantId IS NULL " +
            "AND p.permission NOT IN (SELECT tp.permission FROM PlatformPermission tp WHERE tp.tenantId = :tenantId)")
    List<PlatformPermissionEnum> findGlobalPermissionsNotInTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT DISTINCT p.permission FROM PlatformPermission p WHERE p.tenantId IS NULL")
    List<PlatformPermissionEnum> findGlobalPermissionEnums();

    @Query(value = "SELECT DISTINCT p.permission FROM platform_permissions p WHERE p.tenant_id IS NULL", nativeQuery = true)
    List<String> findGlobalPermissionNames();

    @Query("SELECT p FROM PlatformPermission p WHERE p.id NOT IN " +
            "(SELECT perm.id FROM PlatformRole r JOIN r.permissions perm WHERE r.id = :roleId) " +
            "AND (p.tenantId = :tenantId OR p.tenantId IS NULL)")
    List<PlatformPermission> findPermissionsNotAssignedToRole(@Param("roleId") Long roleId,
                                                              @Param("tenantId") Long tenantId);

    @Query("SELECT p FROM PlatformPermission p WHERE p.id IN :ids AND (p.tenantId = :tenantId OR p.tenantId IS NULL)")
    List<PlatformPermission> findAllByIdAndTenantId(@Param("ids") Set<Long> ids,
                                                    @Param("tenantId") Long tenantId);
}