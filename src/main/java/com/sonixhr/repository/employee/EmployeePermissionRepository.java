package com.sonixhr.repository.employee;

import com.sonixhr.entity.employee.EmployeePermission;
import com.sonixhr.enums.employee.EmployeePermissionEnum;
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
public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    /**
     * Find permission by enum value
     */
    Optional<EmployeePermission> findByPermission(EmployeePermissionEnum permission);

    /**
     * Find permissions by category (sorted by display order)
     */
    List<EmployeePermission> findByCategoryOrderByDisplayOrderAsc(String category);

    /**
     * Find all permissions sorted by category then display order
     */
    List<EmployeePermission> findAllByOrderByCategoryAscDisplayOrderAsc();

    // =====================================================
    // SEARCH QUERIES ✅ ADD THESE
    // =====================================================

    /**
     * Search permissions by name or description
     */
    @Query("SELECT p FROM EmployeePermission p WHERE " +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<EmployeePermission> searchPermissions(@Param("searchTerm") String searchTerm);

    /**
     * Search permissions with pagination
     */
    @Query("SELECT p FROM EmployeePermission p WHERE " +
            "LOWER(p.permission) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<EmployeePermission> searchPermissions(@Param("searchTerm") String searchTerm, Pageable pageable);

    // =====================================================
    // BATCH QUERIES ✅ ADD THESE
    // =====================================================

    /**
     * Find permissions by multiple enum values
     */
    List<EmployeePermission> findByPermissionIn(Set<EmployeePermissionEnum> permissions);

    /**
     * Find permissions by IDs
     */
    @Query("SELECT p FROM EmployeePermission p WHERE p.id IN :ids")
    List<EmployeePermission> findAllByIdIn(@Param("ids") Set<Long> ids);

    // =====================================================
    // COUNT QUERIES ✅ ADD THESE
    // =====================================================

    /**
     * Count permissions by category
     */
    long countByCategory(String category);

    /**
     * Count total permissions
     */
    @Query("SELECT COUNT(p) FROM EmployeePermission p")
    long countTotalPermissions();

    // =====================================================
    // PAGINATION ✅ ADD THESE
    // =====================================================

    /**
     * Find all permissions by category with pagination
     */
    Page<EmployeePermission> findByCategory(String category, Pageable pageable);

    /**
     * Find all permissions with pagination (already in JpaRepository)
     * Page<EmployeePermission> findAll(Pageable pageable) - already available
     */

    // =====================================================
    // EXISTENCE CHECKS ✅ ADD THESE
    // =====================================================

    /**
     * Check if permission exists by enum
     */
    boolean existsByPermission(EmployeePermissionEnum permission);

    /**
     * Check if permission exists by ID
     */
    boolean existsById(Long id);
}