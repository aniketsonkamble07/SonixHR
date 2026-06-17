package com.sonixhr.repository.platform;
 
import com.sonixhr.entity.platform.PlatformPermission;
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
    // BASIC QUERIES - ALL USE STRING (since DB stores enum names as strings)
    // =====================================================

    Optional<PlatformPermission> findByPermission(String permission);

    // ONLY THIS VERSION - accepts Set<String>
    List<PlatformPermission> findByPermissionIn(Set<String> permissions);

    List<PlatformPermission> findByCategory(String category);

    Page<PlatformPermission> findByCategory(String category, Pageable pageable);

    @Query("SELECT p FROM PlatformPermission p WHERE p.active = true ORDER BY p.category ASC, p.displayOrder ASC")
    List<PlatformPermission> findAllOrdered();
 
    List<PlatformPermission> findByActiveTrue();

    @Query("SELECT p FROM PlatformPermission p WHERE p.active = true ORDER BY p.displayOrder ASC")
    List<PlatformPermission> findAllActiveOrdered();

    @Query("SELECT p FROM PlatformPermission p WHERE p.id IN :ids AND p.active = true")
    List<PlatformPermission> findAllByIdsAndActive(@Param("ids") Set<Long> ids);

    // =====================================================
    // EXISTENCE CHECKS - USE STRING
    // =====================================================

    boolean existsByPermission(String permission);

    boolean existsByIdAndActiveTrue(Long id);

    @Query("SELECT COUNT(p) > 0 FROM PlatformPermission p WHERE p.permission = :permission AND p.active = true")
    boolean existsActiveByPermission(@Param("permission") String permission);

    // =====================================================
    // CATEGORY QUERIES
    // =====================================================

    @Query("SELECT DISTINCT p.category FROM PlatformPermission p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    @Query("SELECT DISTINCT p.category FROM PlatformPermission p WHERE p.active = true AND p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllActiveCategories();

    @Query("SELECT p.category, COUNT(p) FROM PlatformPermission p GROUP BY p.category ORDER BY p.category")
    List<Object[]> countPermissionsByCategory();

    @Query("SELECT p FROM PlatformPermission p WHERE p.category = :category AND p.active = true ORDER BY p.displayOrder ASC")
    List<PlatformPermission> findByCategoryOrdered(@Param("category") String category);

    // =====================================================
    // ORDERED QUERIES
    // =====================================================

    @Query("SELECT p FROM PlatformPermission p ORDER BY p.displayOrder ASC, p.permission ASC")
    List<PlatformPermission> findAllOrderByDisplayOrder();

    @Query("SELECT p FROM PlatformPermission p WHERE p.category = :category ORDER BY p.displayOrder ASC")
    Page<PlatformPermission> findByCategoryOrdered(@Param("category") String category, Pageable pageable);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    @Query("SELECT COUNT(r) FROM PlatformRole r JOIN r.permissions p WHERE p.id = :permissionId")
    long countRolesByPermissionId(@Param("permissionId") Long permissionId);

    long countByActiveTrue();

    long countByCategory(String category);

    @Query("SELECT COUNT(p) FROM PlatformPermission p WHERE p.active = true AND p.category = :category")
    long countActiveByCategory(@Param("category") String category);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    @Query("SELECT p FROM PlatformPermission p WHERE " +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.category) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<PlatformPermission> searchPermissions(@Param("searchTerm") String searchTerm);

    @Query("SELECT p FROM PlatformPermission p WHERE " +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<PlatformPermission> searchPermissions(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT p FROM PlatformPermission p WHERE p.category = :category AND " +
            "(LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<PlatformPermission> searchPermissionsByCategory(@Param("category") String category,
                                                         @Param("searchTerm") String searchTerm);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE PlatformPermission p SET p.displayOrder = :displayOrder WHERE p.id = :id")
    int updateDisplayOrder(@Param("id") Long id, @Param("displayOrder") Integer displayOrder);

    @Modifying
    @Transactional
    @Query("UPDATE PlatformPermission p SET p.active = true WHERE p.id IN :ids")
    int bulkActivate(@Param("ids") Set<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE PlatformPermission p SET p.active = false WHERE p.id IN :ids")
    int bulkDeactivate(@Param("ids") Set<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE PlatformPermission p SET p.category = :category WHERE p.id IN :ids")
    int bulkUpdateCategory(@Param("ids") Set<Long> ids, @Param("category") String category);

    // =====================================================
    // BATCH QUERIES
    // =====================================================

    List<PlatformPermission> findAllByIdIn(Set<Long> ids);

    @Query("SELECT p FROM PlatformPermission p WHERE p.permission IN :permissions")
    List<PlatformPermission> findAllByPermissionIn(@Param("permissions") Set<String> permissions);

    @Query("SELECT p.id, p.permission, p.description, p.category FROM PlatformPermission p WHERE p.id IN :ids")
    List<Object[]> findPermissionSummariesByIds(@Param("ids") Set<Long> ids);

    // =====================================================
    // VALIDATION QUERIES
    // =====================================================

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM PlatformRole r JOIN r.permissions p WHERE p.id = :permissionId")
    boolean isPermissionAssignedToRole(@Param("permissionId") Long permissionId);

    @Query("SELECT DISTINCT p.id FROM PlatformRole r JOIN r.permissions p")
    Set<Long> findPermissionIdsAssignedToRoles();

    // =====================================================
    // INITIALIZATION QUERIES
    // =====================================================

    @Query("SELECT p.permission FROM PlatformPermission p")
    List<String> findAllExistingPermissionNames();

    @Query(value = "SELECT DISTINCT p.permission FROM platform_permissions p", nativeQuery = true)
    List<String> findGlobalPermissionNames();

    @Query("SELECT p.category, COUNT(p) FROM PlatformPermission p WHERE p.active = true GROUP BY p.category")
    List<Object[]> getPermissionStatisticsByModule();

    // =====================================================
    // PERFORMANCE OPTIMIZATION QUERIES
    // =====================================================

    @Query("SELECT p FROM PlatformPermission p WHERE " +
            "(:category IS NULL OR p.category = :category) AND " +
            "(:active IS NULL OR p.active = :active) " +
            "ORDER BY p.category ASC, p.displayOrder ASC")
    Page<PlatformPermission> findPermissionsFiltered(@Param("category") String category,
                                                     @Param("active") Boolean active,
                                                     Pageable pageable);

    @Query("SELECT p.id FROM PlatformPermission p WHERE p.permission = :permission")
    Optional<Long> findIdByPermission(@Param("permission") String permission);

    @Query("SELECT p.permission FROM PlatformPermission p WHERE p.permission IN :permissions")
    Set<String> findExistingPermissionsInSet(@Param("permissions") Set<String> permissions);
}