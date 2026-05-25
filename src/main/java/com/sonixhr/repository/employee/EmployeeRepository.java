package com.sonixhr.repository.employee;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.employee.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================
    Optional<Employee> findByTenant_IdAndEmployeeCode(UUID tenantId, String employeeCode);
    Optional<Employee> findByTenant_IdAndEmail(UUID tenantId, String email);
    List<Employee> findByTenant_Id(UUID tenantId);
    Page<Employee> findByTenant_Id(UUID tenantId, Pageable pageable);
    Optional<Employee> findByEmail(String email);

    boolean existsByTenant_IdAndEmail(UUID tenantId, String email);
    boolean existsByTenant_IdAndEmployeeCode(UUID tenantId, String employeeCode);

    // =====================================================
    // STATUS-BASED QUERIES
    // =====================================================
    List<Employee> findByTenant_IdAndStatus(UUID tenantId, EmployeeStatus status);
    Page<Employee> findByTenant_IdAndStatus(UUID tenantId, EmployeeStatus status, Pageable pageable);

    // =====================================================
    // MANAGER QUERIES
    // =====================================================
    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    List<Employee> findByManagerIdAndTenant_Id(@Param("managerId") Long managerId,
                                               @Param("tenantId") UUID tenantId);

    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    Page<Employee> findByManagerIdAndTenant_Id(@Param("managerId") Long managerId,
                                               @Param("tenantId") UUID tenantId,
                                               Pageable pageable);

    boolean existsByManagerIdAndTenant_Id(Long managerId, UUID tenantId);

    // =====================================================
    // DEPARTMENT QUERIES - CORRECTED
    // =====================================================
    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.department.name = :departmentName")
    List<Employee> findByTenantIdAndDepartmentName(@Param("tenantId") UUID tenantId,
                                                   @Param("departmentName") String departmentName);

    @Query("SELECT DISTINCT e.department.name FROM Employee e WHERE e.tenant.id = :tenantId AND e.department IS NOT NULL")
    List<String> findDistinctDepartmentsByTenant_Id(@Param("tenantId") UUID tenantId);

    @Query("SELECT e.department.name, COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId GROUP BY e.department.name")
    List<Object[]> countEmployeesByDepartment(@Param("tenantId") UUID tenantId);

    // =====================================================
    // SEARCH QUERIES - CORRECTED
    // =====================================================
    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.department.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.position) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Employee> searchEmployees(@Param("tenantId") UUID tenantId,
                                   @Param("searchTerm") String searchTerm,
                                   Pageable pageable);

    // =====================================================
    // COUNT QUERIES
    // =====================================================
    long countByTenant_IdAndStatus(UUID tenantId, EmployeeStatus status);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId")
    long countByTenant_Id(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId AND e.status = :status")
    long countByTenant_IdAndStatus2(@Param("tenantId") UUID tenantId,
                                    @Param("status") EmployeeStatus status);

    // =====================================================
    // EMPLOYEE CODE GENERATION QUERIES
    // =====================================================
    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "e.employeeCode LIKE CONCAT(:prefix, '%') ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenantAndPrefix(@Param("tenantId") UUID tenantId,
                                                 @Param("prefix") String prefix);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "e.employeeCode LIKE CONCAT(:prefix, '-', :year, '-%') ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenantAndYear(@Param("tenantId") UUID tenantId,
                                               @Param("year") int year,
                                               @Param("prefix") String prefix);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "e.department = :department AND e.employeeCode LIKE CONCAT(:deptCode, '-', :year, '-%') " +
            "ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenantAndDepartment(@Param("tenantId") UUID tenantId,
                                                     @Param("department") String department,
                                                     @Param("year") int year,
                                                     @Param("deptCode") String deptCode);

    // =====================================================
    // BIRTHDAY & ANNIVERSARY QUERIES
    // =====================================================
    @Query(value = "SELECT * FROM employees WHERE tenant_id = :tenantId AND " +
            "EXTRACT(MONTH FROM hire_date) = :month AND EXTRACT(DAY FROM hire_date) = :day",
            nativeQuery = true)
    List<Employee> findEmployeesWithAnniversary(@Param("tenantId") UUID tenantId,
                                                @Param("month") int month,
                                                @Param("day") int day);

    @Query(value = "SELECT * FROM employees WHERE tenant_id = :tenantId AND " +
            "EXTRACT(MONTH FROM date_of_birth) = :month AND EXTRACT(DAY FROM date_of_birth) = :day",
            nativeQuery = true)
    List<Employee> findEmployeesWithBirthday(@Param("tenantId") UUID tenantId,
                                             @Param("month") int month,
                                             @Param("day") int day);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================
    @Modifying
    @Transactional
    @Query("UPDATE Employee e SET e.status = :status WHERE e.tenant.id = :tenantId AND e.id IN :employeeIds")
    int bulkUpdateEmployeeStatus(@Param("tenantId") UUID tenantId,
                                 @Param("employeeIds") List<Long> employeeIds,
                                 @Param("status") EmployeeStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Employee e SET e.manager.id = :managerId WHERE e.tenant.id = :tenantId AND e.id IN :employeeIds")
    int bulkAssignManager(@Param("tenantId") UUID tenantId,
                          @Param("employeeIds") List<Long> employeeIds,
                          @Param("managerId") Long managerId);

    @Modifying
    @Transactional
    @Query("UPDATE Employee e SET e.status = 'TERMINATED', e.lastWorkingDate = :lastWorkingDate " +
            "WHERE e.tenant.id = :tenantId AND e.id IN :employeeIds")
    int bulkTerminateEmployees(@Param("tenantId") UUID tenantId,
                               @Param("employeeIds") List<Long> employeeIds,
                               @Param("lastWorkingDate") LocalDate lastWorkingDate);

    // =====================================================
    // HIRING DATE QUERIES
    // =====================================================
    List<Employee> findByTenant_IdAndHireDateBetween(UUID tenantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.hireDate <= :date")
    List<Employee> findEmployeesHiredBefore(@Param("tenantId") UUID tenantId,
                                            @Param("date") LocalDate date);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.hireDate >= :date")
    List<Employee> findEmployeesHiredAfter(@Param("tenantId") UUID tenantId,
                                           @Param("date") LocalDate date);

    // =====================================================
    // EMPLOYEE WITHOUT MANAGER
    // =====================================================
    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.manager IS NULL")
    List<Employee> findEmployeesWithNoManager(@Param("tenantId") UUID tenantId);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.manager IS NOT NULL")
    List<Employee> findEmployeesWithManager(@Param("tenantId") UUID tenantId);

    // =====================================================
    // DEPARTMENT STATISTICS
    // =====================================================
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId")
    long countByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                        @Param("tenantId") UUID tenantId);

    @Query("SELECT e FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId")
    List<Employee> findByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                                 @Param("tenantId") UUID tenantId);

    @Query("SELECT e FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId")
    Page<Employee> findByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                                 @Param("tenantId") UUID tenantId,
                                                 Pageable pageable);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId AND e.status = :status")
    long countActiveByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                              @Param("tenantId") UUID tenantId,
                                              @Param("status") EmployeeStatus status);
}