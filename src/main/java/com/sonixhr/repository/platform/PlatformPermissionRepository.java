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




    Page<PlatformPermission> findByCategory(String category, Pageable pageable);

    // =====================================================
    // EXISTENCE CHECKS
    // =====================================================

    boolean existsByPermission(PlatformPermissionEnum permission);




    // =====================================================
    // CATEGORY QUERIES
    // =====================================================

    @Query("SELECT DISTINCT p.category FROM PlatformPermission p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();





    // =====================================================
    // ORDERED QUERIES
    // =====================================================



    // =====================================================
    // COUNT QUERIES
    // =====================================================

    @Query("SELECT COUNT(r) FROM PlatformRole r JOIN r.permissions p WHERE p.id = :permissionId")
    long countRolesByPermissionId(@Param("permissionId") Long permissionId);




    // =====================================================
    // SEARCH QUERIES
    // =====================================================



    // =====================================================
    // BULK OPERATIONS
    // =====================================================





    // =====================================================
    // BATCH QUERIES
    // =====================================================




    // =====================================================
    // VALIDATION QUERIES
    // =====================================================




    // =====================================================
    // INITIALIZATION QUERIES
    // =====================================================





    @Query(value = "SELECT DISTINCT p.permission FROM platform_permissions p WHERE p.tenant_id IS NULL", nativeQuery = true)
    List<String> findGlobalPermissionNames();




}