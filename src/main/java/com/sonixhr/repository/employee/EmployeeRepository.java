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

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // =====================================================
    // BASIC QUERIES - ALL USING Long tenantId
    // =====================================================

    Optional<Employee> findByTenant_IdAndEmployeeCode(Long tenantId, String employeeCode);
    Optional<Employee> findByTenant_IdAndEmail(Long tenantId, String email);
    List<Employee> findByTenant_Id(Long tenantId);
    Page<Employee> findByTenant_Id(Long tenantId, Pageable pageable);
    Optional<Employee> findByEmail(String email);


    boolean existsByEmail(String email);
    boolean existsByTenant_IdAndEmail(Long tenantId, String email);
    boolean existsByTenant_IdAndEmployeeCode(Long tenantId, String employeeCode);

    // =====================================================
    // STATUS-BASED QUERIES
    // =====================================================

    List<Employee> findByTenant_IdAndStatus(Long tenantId, EmployeeStatus status);
    Page<Employee> findByTenant_IdAndStatus(Long tenantId, EmployeeStatus status, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE e.manager IS NULL AND e.tenant.id = :tenantId")
    List<Employee> findByManagerIsNullAndTenant_Id(@Param("tenantId") Long tenantId);

    // =====================================================
    // MANAGER QUERIES
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    List<Employee> findByManagerIdAndTenant_Id(@Param("managerId") Long managerId,
                                               @Param("tenantId") Long tenantId);

    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    Page<Employee> findByManagerIdAndTenant_Id(@Param("managerId") Long managerId,
                                               @Param("tenantId") Long tenantId,
                                               Pageable pageable);

    boolean existsByManagerIdAndTenant_Id(Long managerId, Long tenantId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    long countByManagerIdAndTenant_Id(@Param("managerId") Long managerId,
                                      @Param("tenantId") Long tenantId);

    // =====================================================
    // DEPARTMENT QUERIES
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.department.name = :departmentName")
    List<Employee> findByTenantIdAndDepartmentName(@Param("tenantId") Long tenantId,
                                                   @Param("departmentName") String departmentName);

    @Query("SELECT DISTINCT e.department.name FROM Employee e WHERE e.tenant.id = :tenantId AND e.department IS NOT NULL")
    List<String> findDistinctDepartmentsByTenant_Id(@Param("tenantId") Long tenantId);

    @Query("SELECT e.department.name, COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId GROUP BY e.department.name")
    List<Object[]> countEmployeesByDepartment(@Param("tenantId") Long tenantId);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.department.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.position) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Employee> searchEmployees(@Param("tenantId") Long tenantId,
                                   @Param("searchTerm") String searchTerm,
                                   Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "e.employeeCode LIKE CONCAT('%', :query, '%'))")
    Page<Employee> searchEmployeesForAssignment(@Param("tenantId") Long tenantId,
                                                @Param("query") String query,
                                                Pageable pageable);
    /**
     * Search team members for a manager (simpler version for attendance marking)
     */
    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId AND " +
            "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Employee> searchTeamMembers(@Param("managerId") Long managerId,
                                     @Param("tenantId") Long tenantId,
                                     @Param("searchTerm") String searchTerm);
    // =====================================================
    // COUNT QUERIES
    // =====================================================

    long countByTenant_IdAndStatus(Long tenantId, EmployeeStatus status);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId")
    long countByTenant_Id(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId AND e.status = :status")
    long countByTenant_IdAndStatus2(@Param("tenantId") Long tenantId,
                                    @Param("status") EmployeeStatus status);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId AND e.isActive = true")
    long countByTenantIdAndIsActiveTrue(@Param("tenantId") Long tenantId);

    //  Add this method for counting by tenant ID
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId")
    long countByTenantId(@Param("tenantId") Long tenantId);

    // =====================================================
    // EMPLOYEE CODE GENERATION QUERIES
    // =====================================================

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "e.employeeCode LIKE CONCAT(:prefix, '%') ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenantAndPrefix(@Param("tenantId") Long tenantId,
                                                 @Param("prefix") String prefix);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "e.employeeCode LIKE CONCAT(:prefix, '-', :year, '-%') ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenantAndYear(@Param("tenantId") Long tenantId,
                                               @Param("year") int year,
                                               @Param("prefix") String prefix);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "e.department = :department AND e.employeeCode LIKE CONCAT(:deptCode, '-', :year, '-%') " +
            "ORDER BY e.id DESC LIMIT 1")
    String findLastEmployeeCodeByTenantAndDepartment(@Param("tenantId") Long tenantId,
                                                     @Param("department") String department,
                                                     @Param("year") int year,
                                                     @Param("deptCode") String deptCode);

    // =====================================================
    // BIRTHDAY & ANNIVERSARY QUERIES
    // =====================================================

    @Query(value = "SELECT * FROM employees WHERE tenant_id = :tenantId AND " +
            "EXTRACT(MONTH FROM hire_date) = :month AND EXTRACT(DAY FROM hire_date) = :day",
            nativeQuery = true)
    List<Employee> findEmployeesWithAnniversary(@Param("tenantId") Long tenantId,
                                                @Param("month") int month,
                                                @Param("day") int day);

    @Query(value = "SELECT * FROM employees WHERE tenant_id = :tenantId AND " +
            "EXTRACT(MONTH FROM date_of_birth) = :month AND EXTRACT(DAY FROM date_of_birth) = :day",
            nativeQuery = true)
    List<Employee> findEmployeesWithBirthday(@Param("tenantId") Long tenantId,
                                             @Param("month") int month,
                                             @Param("day") int day);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE Employee e SET e.status = :status WHERE e.tenant.id = :tenantId AND e.id IN :employeeIds")
    int bulkUpdateEmployeeStatus(@Param("tenantId") Long tenantId,
                                 @Param("employeeIds") List<Long> employeeIds,
                                 @Param("status") EmployeeStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Employee e SET e.manager.id = :managerId WHERE e.tenant.id = :tenantId AND e.id IN :employeeIds")
    int bulkAssignManager(@Param("tenantId") Long tenantId,
                          @Param("employeeIds") List<Long> employeeIds,
                          @Param("managerId") Long managerId);

    @Modifying
    @Transactional
    @Query("UPDATE Employee e SET e.status = 'TERMINATED', e.lastWorkingDate = :lastWorkingDate " +
            "WHERE e.tenant.id = :tenantId AND e.id IN :employeeIds")
    int bulkTerminateEmployees(@Param("tenantId") Long tenantId,
                               @Param("employeeIds") List<Long> employeeIds,
                               @Param("lastWorkingDate") LocalDate lastWorkingDate);

    // =====================================================
    // HIRING DATE QUERIES
    // =====================================================

    List<Employee> findByTenant_IdAndHireDateBetween(Long tenantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.hireDate <= :date")
    List<Employee> findEmployeesHiredBefore(@Param("tenantId") Long tenantId,
                                            @Param("date") LocalDate date);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.hireDate >= :date")
    List<Employee> findEmployeesHiredAfter(@Param("tenantId") Long tenantId,
                                           @Param("date") LocalDate date);

    // =====================================================
    // EMPLOYEE WITHOUT MANAGER
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.manager IS NULL")
    List<Employee> findEmployeesWithNoManager(@Param("tenantId") Long tenantId);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.manager IS NOT NULL")
    List<Employee> findEmployeesWithManager(@Param("tenantId") Long tenantId);

    @Query("SELECT e FROM Employee e WHERE e.manager IS NULL AND e.tenant.id = :tenantId")
    List<Employee> findByManagerIsNullAndTenant_Id2(@Param("tenantId") Long tenantId);

    // =====================================================
    // DEPARTMENT STATISTICS
    // =====================================================

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId")
    long countByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                        @Param("tenantId") Long tenantId);

    @Query("SELECT e FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId")
    List<Employee> findByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                                 @Param("tenantId") Long tenantId);

    @Query("SELECT e FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId")
    Page<Employee> findByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                                 @Param("tenantId") Long tenantId,
                                                 Pageable pageable);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.id = :departmentId AND e.tenant.id = :tenantId AND e.status = :status")
    long countActiveByDepartmentIdAndTenantId(@Param("departmentId") Long departmentId,
                                              @Param("tenantId") Long tenantId,
                                              @Param("status") EmployeeStatus status);

    // =====================================================
    // ADDITIONAL USEFUL QUERIES
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.isActive = true")
    List<Employee> findActiveEmployeesByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.isActive = true")
    Page<Employee> findActiveEmployeesByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.email LIKE CONCAT('%@', :domain)")
    List<Employee> findByEmailDomain(@Param("tenantId") Long tenantId, @Param("domain") String domain);

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND " +
            "EXTRACT(YEAR FROM e.hireDate) = EXTRACT(YEAR FROM CURRENT_DATE) AND " +
            "EXTRACT(MONTH FROM e.hireDate) = EXTRACT(MONTH FROM CURRENT_DATE)")
    List<Employee> findEmployeesHiredCurrentMonth(@Param("tenantId") Long tenantId);

    @Query("SELECT e.employmentType, COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId GROUP BY e.employmentType")
    List<Object[]> countByEmploymentType(@Param("tenantId") Long tenantId);

    @Query("SELECT e, (SELECT COUNT(sub) FROM Employee sub WHERE sub.manager.id = e.id) " +
            "FROM Employee e WHERE e.tenant.id = :tenantId AND e.manager.id = :managerId")
    Page<Object[]> findTeamMembersWithReportCount(@Param("tenantId") Long tenantId,
                                                  @Param("managerId") Long managerId,
                                                  Pageable pageable);
    //  CORRECT - Uses e.tenant.id through relationship
    @Query("SELECT DISTINCT e FROM Employee e JOIN e.roles r WHERE r.id = :roleId AND e.tenant.id = :tenantId")
    List<Employee> findByRolesIdAndTenantId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    //  CORRECT - Uses e.tenant.id through relationship
    @Query("SELECT COUNT(DISTINCT e) FROM Employee e JOIN e.roles r WHERE r.id = :roleId AND e.tenant.id = :tenantId")
    long countUsersByRoleIdAndTenantId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    // CORRECT - Spring Data JPA will derive this query using getTenantId() method
    @Query("SELECT e FROM Employee e WHERE e.id = :id AND e.tenant.id = :tenantId")
    Optional<Employee> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

// Add this in the MANAGER QUERIES section

// =====================================================
// MANAGER QUERIES
// =====================================================

    //  Add this simple method (without tenant_id for cases where you have the manager object)
    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId")
    List<Employee> findByManagerId(@Param("managerId") Long managerId);

    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId")
    Page<Employee> findByManagerId(@Param("managerId") Long managerId, Pageable pageable);


// Add to EmployeeRepository.java

// =====================================================
// LEAVE MANAGEMENT SPECIFIC QUERIES
// =====================================================



    /**
     * Count employees by manager for leave reporting
     */
    @Query("SELECT e.manager.id, COUNT(e) FROM Employee e " +
            "WHERE e.tenant.id = :tenantId AND e.manager IS NOT NULL " +
            "GROUP BY e.manager.id")
    List<Object[]> countEmployeesByManager(@Param("tenantId") Long tenantId);

    /**
     * Find employees with pending leave requests (for notification)
     */
    @Query("SELECT DISTINCT e FROM Employee e JOIN LeaveRequest l ON l.employee.id = e.id " +
            "WHERE e.tenant.id = :tenantId AND l.status = 'PENDING'")
    List<Employee> findEmployeesWithPendingLeaves(@Param("tenantId") Long tenantId);

    // Add this in the BASIC QUERIES section, after findByEmail(String email)

    @Query("""
    SELECT DISTINCT e FROM Employee e
    LEFT JOIN FETCH e.roles r
    LEFT JOIN FETCH r.permissions
    WHERE e.email = :email
""")
    Optional<Employee> findByEmailWithRolesAndPermissions(@Param("email") String email);
}