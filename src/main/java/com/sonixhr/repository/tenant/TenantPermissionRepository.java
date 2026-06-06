package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.enums.TenantPermissionEnum;
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
    // BASIC QUERIES
    // =====================================================

    Optional<TenantPermission> findByPermission(TenantPermissionEnum permission);

    List<TenantPermission> findByPermissionIn(Set<TenantPermissionEnum> permissions);

    List<TenantPermission> findByCategory(String category);

    List<TenantPermission> findAllByOrderByCategoryAscDisplayOrderAsc();



    // =====================================================
    // BATCH QUERIES
    // =====================================================

    // Find permissions by IDs (basic)
    @Query("SELECT p FROM TenantPermission p WHERE p.id IN :ids")
    List<TenantPermission> findAllByIdIn(@Param("ids") Set<Long> ids);


    // =====================================================
    // CATEGORY QUERIES
    // =====================================================

    @Query("SELECT DISTINCT p.category FROM TenantPermission p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    // =====================================================
    // SEARCH QUERIES (Optional but useful)
    // =====================================================

    @Query("SELECT p FROM TenantPermission p WHERE " +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<TenantPermission> searchPermissions(@Param("searchTerm") String searchTerm);

    // =====================================================
    // COUNT QUERIES (Optional)
    // =====================================================

    @Query("SELECT COUNT(r) FROM TenantRole r JOIN r.permissions p WHERE p.id = :permissionId")
    long countRolesByPermissionId(@Param("permissionId") Long permissionId);
}