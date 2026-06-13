package com.sonixhr.repository.employee;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.employee.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.Set;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    Optional<Employee> findByTenant_IdAndEmployeeCode(Long tenantId, String employeeCode);
    Optional<Employee> findByTenant_IdAndEmail(Long tenantId, String email);

    @Query("SELECT e.id, e.email, e.firstName, e.lastName, e.employeeCode FROM Employee e WHERE e.tenant.id = :tenantId")
    List<Object[]> findEmployeeSummariesByTenantId(@Param("tenantId") Long tenantId);

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
    List<Employee> findEmployeesWithNoManager(@Param("tenantId") Long tenantId);

    // =====================================================
    // MANAGER QUERIES
    // =====================================================

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.manager WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    List<Employee> findByManagerIdAndTenantId(@Param("managerId") Long managerId,
                                              @Param("tenantId") Long tenantId);

    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    Page<Employee> findByManagerIdAndTenantId(@Param("managerId") Long managerId,
                                              @Param("tenantId") Long tenantId,
                                              Pageable pageable);

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.manager WHERE e.manager.id = :managerId")
    List<Employee> findByManagerId(@Param("managerId") Long managerId);

    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId")
    Page<Employee> findByManagerId(@Param("managerId") Long managerId, Pageable pageable);

    boolean existsByManagerIdAndTenant_Id(Long managerId, Long tenantId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.manager.id = :managerId AND e.tenant.id = :tenantId")
    long countByManagerIdAndTenantId(@Param("managerId") Long managerId,
                                     @Param("tenantId") Long tenantId);

    // =====================================================
    // DEPARTMENT QUERIES
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.tenant.id = :tenantId AND e.department.name = :departmentName")
    List<Employee> findByTenantIdAndDepartmentName(@Param("tenantId") Long tenantId,
                                                   @Param("departmentName") String departmentName);

    @Query("SELECT DISTINCT e.department.name FROM Employee e WHERE e.tenant.id = :tenantId AND e.department IS NOT NULL")
    List<String> findDistinctDepartmentsByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT e.department.name, COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId GROUP BY e.department.name")
    List<Object[]> countEmployeesByDepartment(@Param("tenantId") Long tenantId);

    @Query("SELECT e.department.id, e.status, COUNT(e) FROM Employee e " +
           "WHERE e.tenant.id = :tenantId AND e.department IS NOT NULL " +
           "GROUP BY e.department.id, e.status")
    List<Object[]> countEmployeesByDepartmentAndStatus(@Param("tenantId") Long tenantId);

    @Query("SELECT e.status, COUNT(e) FROM Employee e " +
           "WHERE e.tenant.id = :tenantId AND e.department.id = :departmentId " +
           "GROUP BY e.status")
    List<Object[]> countEmployeesByStatusForDepartment(@Param("tenantId") Long tenantId,
                                                       @Param("departmentId") Long departmentId);

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
    long countByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId AND e.isActive = true")
    long countActiveByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId AND e.status IN :statuses")
    long countByTenantIdAndStatuses(@Param("tenantId") Long tenantId,
                                    @Param("statuses") Set<EmployeeStatus> statuses);

    // =====================================================
    // EMPLOYEE CODE GENERATION QUERIES
    // =====================================================

    @Query(value = "SELECT employee_code FROM employees WHERE tenant_id = :tenantId ORDER BY id DESC LIMIT 1",
            nativeQuery = true)
    String findLastEmployeeCodeByTenant(@Param("tenantId") Long tenantId);

    @Query(value = "SELECT employee_code FROM employees WHERE tenant_id = :tenantId AND " +
            "employee_code LIKE CONCAT(:prefix, '%') ORDER BY id DESC LIMIT 1", nativeQuery = true)
    String findLastEmployeeCodeByTenantAndPrefix(@Param("tenantId") Long tenantId,
                                                 @Param("prefix") String prefix);

    @Query(value = "SELECT employee_code FROM employees WHERE tenant_id = :tenantId AND " +
            "employee_code LIKE CONCAT(:prefix, '-', :year, '-%') ORDER BY id DESC LIMIT 1", nativeQuery = true)
    String findLastEmployeeCodeByTenantAndYear(@Param("tenantId") Long tenantId,
                                               @Param("year") int year,
                                               @Param("prefix") String prefix);

    @Query(value = "SELECT employee_code FROM employees WHERE tenant_id = :tenantId AND " +
            "department_id = (SELECT id FROM departments WHERE name = :department) AND " +
            "employee_code LIKE CONCAT(:deptCode, '-', :year, '-%') ORDER BY id DESC LIMIT 1", nativeQuery = true)
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

    @Modifying
    @Transactional
    @Query("UPDATE Employee e SET e.rolesVersion = e.rolesVersion + 1 WHERE e.tenant.id = :tenantId AND e.id IN :employeeIds")
    int bulkIncrementRolesVersion(@Param("tenantId") Long tenantId,
                                  @Param("employeeIds") List<Long> employeeIds);

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

    @Query("SELECT e.id, e.email, e.firstName, e.lastName FROM Employee e WHERE e.tenant.id = :tenantId AND e.isActive = true")
    List<Object[]> findActiveEmployeeSummariesByTenantId(@Param("tenantId") Long tenantId);

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

    // =====================================================
    // ROLE QUERIES
    // =====================================================

    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.roles r WHERE r.id = :roleId AND e.tenant.id = :tenantId")
    List<Employee> findByRolesIdAndTenantId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(DISTINCT e) FROM Employee e JOIN e.roles r WHERE r.id = :roleId AND e.tenant.id = :tenantId")
    long countUsersByRoleIdAndTenantId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    @Query("SELECT r.id, COUNT(e) FROM Employee e JOIN e.roles r WHERE e.tenant.id = :tenantId GROUP BY r.id")
    List<Object[]> countEmployeesForRolesByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT e.id, e.email FROM Employee e JOIN e.roles r WHERE r.id = :roleId AND e.tenant.id = :tenantId")
    List<Object[]> findUserIdsAndEmailsByRoleId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    // =====================================================
    // FIND BY ID WITH TENANT
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.id = :id AND e.tenant.id = :tenantId")
    Optional<Employee> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);


    // =====================================================
    // FETCH JOIN FOR ROLES & PERMISSIONS (CRITICAL FOR AUTH)
    // =====================================================

    @Query("""
    SELECT DISTINCT e FROM Employee e
    LEFT JOIN FETCH e.roles r
    LEFT JOIN FETCH r.permissions
    WHERE e.email = :email
    """)
    Optional<Employee> findByEmailWithRolesAndPermissions(@Param("email") String email);

    @Query("""
    SELECT DISTINCT e FROM Employee e
    LEFT JOIN FETCH e.roles r
    LEFT JOIN FETCH r.permissions
    WHERE e.email IN :emails
    """)
    List<Employee> findAllByEmailsWithRolesAndPermissions(@Param("emails") List<String> emails);

    // =====================================================
    // FIND BY EMAIL AND TENANT WITH ROLES (For tenant-specific auth)
    // =====================================================

    @Query("""
    SELECT DISTINCT e FROM Employee e
    LEFT JOIN FETCH e.roles r
    LEFT JOIN FETCH r.permissions
    WHERE e.email = :email AND e.tenant.id = :tenantId
    """)
    Optional<Employee> findByEmailAndTenantIdWithRoles(@Param("email") String email,
                                                       @Param("tenantId") Long tenantId);

    // =====================================================
    // LEAVE MANAGEMENT SPECIFIC QUERIES
    // =====================================================

    @Query("SELECT e.manager.id, COUNT(e) FROM Employee e " +
            "WHERE e.tenant.id = :tenantId AND e.manager IS NOT NULL " +
            "GROUP BY e.manager.id")
    List<Object[]> countEmployeesByManager(@Param("tenantId") Long tenantId);

    //@Query("SELECT DISTINCT e FROM Employee e JOIN Leave l ON l.employee.id = e.id " +
   //         "WHERE e.tenant.id = :tenantId AND l.status = 'PENDING'")
  //  List<Employee> findEmployeesWithPendingLeaves(@Param("tenantId") Long tenantId);

    // =====================================================
    // UPCOMING BIRTHDAYS & ANNIVERSARIES
    // =====================================================

    @Query(value = "SELECT * FROM employees WHERE tenant_id = :tenantId " +
            "AND date_of_birth IS NOT NULL " +
            "AND TO_CHAR(date_of_birth, 'MM-DD') BETWEEN TO_CHAR(:startDate, 'MM-DD') AND TO_CHAR(:endDate, 'MM-DD') " +
            "ORDER BY TO_CHAR(date_of_birth, 'MM-DD')",
            nativeQuery = true)
    List<Employee> findEmployeesWithUpcomingBirthdays(@Param("tenantId") Long tenantId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    @Query(value = "SELECT * FROM employees WHERE tenant_id = :tenantId " +
            "AND hire_date IS NOT NULL " +
            "AND TO_CHAR(hire_date, 'MM-DD') BETWEEN TO_CHAR(:startDate, 'MM-DD') AND TO_CHAR(:endDate, 'MM-DD') " +
            "ORDER BY TO_CHAR(hire_date, 'MM-DD')",
            nativeQuery = true)
    List<Employee> findEmployeesWithUpcomingAnniversaries(@Param("tenantId") Long tenantId,
                                                          @Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);

    // =====================================================
    // BATCH QUERIES FOR PERFORMANCE
    // =====================================================

    @Query("SELECT e FROM Employee e WHERE e.id IN :ids AND e.tenant.id = :tenantId")
    List<Employee> findAllByIdsAndTenantId(@Param("ids") List<Long> ids,
                                           @Param("tenantId") Long tenantId);

    @Query("SELECT e.id FROM Employee e WHERE e.id IN :ids AND e.tenant.id = :tenantId")
    Set<Long> findExistingEmployeeIds(@Param("ids") List<Long> ids,
                                      @Param("tenantId") Long tenantId);

    @Query("SELECT e.status, COUNT(e) FROM Employee e WHERE e.tenant.id = :tenantId GROUP BY e.status")
    List<Object[]> getEmployeeStatisticsByTenant(@Param("tenantId") Long tenantId);

    // =====================================================
// ADD THESE MISSING METHODS
// =====================================================

    /**
     * Find employees by emails with roles (for batch loading)
     */
    @Query("SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.roles r " +
            "LEFT JOIN FETCH r.permissions " +
            "WHERE e.email IN :emails")
    List<Employee> findAllByEmailsWithRoles(@Param("emails") List<String> emails);



    /**
     * Alternative if you need pagination for preloading
     */
    default List<Employee> findTop100ByIsActiveTrue() {
        return findTop100ByIsActiveTrue(PageRequest.of(0, 100));
    }

    /**
     * Find top active employees with pagination
     */
    @Query("SELECT e FROM Employee e WHERE e.isActive = true ORDER BY e.lastLoginAt DESC")
    List<Employee> findTop100ByIsActiveTrue(Pageable pageable);
}