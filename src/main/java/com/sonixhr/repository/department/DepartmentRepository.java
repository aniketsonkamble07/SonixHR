package com.sonixhr.repository.department;

import com.sonixhr.entity.department.Department;
import com.sonixhr.security.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends TenantAwareRepository<Department, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    Optional<Department> findByIdAndTenant_Id(Long id, Long tenantId);

    List<Department> findByTenant_Id(Long tenantId);

    Page<Department> findByTenant_Id(Long tenantId, Pageable pageable);

    List<Department> findByTenant_IdAndIsActiveTrue(Long tenantId);

    // =====================================================
    // UNIQUE VALIDATION QUERIES
    // =====================================================

    Optional<Department> findByTenant_IdAndCode(Long tenantId, String code);

    Optional<Department> findByTenant_IdAndName(Long tenantId, String name);

    boolean existsByTenant_IdAndName(Long tenantId, String name);

    boolean existsByTenant_IdAndCode(Long tenantId, String code);

    // =====================================================
    // SORTED QUERIES
    // =====================================================

    @Query("SELECT d FROM Department d WHERE d.tenant.id = :tenantId ORDER BY d.name ASC")
    List<Department> findAllByTenantIdOrderByName(@Param("tenantId") Long tenantId);

    // =====================================================
    // QUERY TO GET DEPARTMENTS WITH EMPLOYEE COUNTS
    // =====================================================

    @Query("SELECT d, COUNT(e) FROM Department d " +
            "LEFT JOIN Employee e ON e.department.id = d.id " +
            "WHERE d.tenant.id = :tenantId " +
            "GROUP BY d.id ORDER BY d.name ASC")
    List<Object[]> findAllWithEmployeeCount(@Param("tenantId") Long tenantId);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    @Query("SELECT d FROM Department d WHERE d.tenant.id = :tenantId " +
            "AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Department> searchDepartments(@Param("tenantId") Long tenantId,
                                       @Param("searchTerm") String searchTerm,
                                       Pageable pageable);

    // =====================================================
    // UPDATE QUERIES
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE Department d SET d.isActive = :isActive WHERE d.id = :id AND d.tenant.id = :tenantId")
    int updateDepartmentStatus(@Param("id") Long id,
                               @Param("tenantId") Long tenantId,
                               @Param("isActive") Boolean isActive);

    @Modifying
    @Transactional
    @Query("UPDATE Department d SET d.isActive = false WHERE d.tenant.id = :tenantId")
    void deactivateAllDepartmentsForTenant(@Param("tenantId") Long tenantId);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    long countByTenant_Id(Long tenantId);

    long countByTenant_IdAndIsActiveTrue(Long tenantId);
}