package com.sonixhr.repository.attendance;

import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.enums.attendance.AttendanceStatus;
import com.sonixhr.security.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ManualAttendanceRepository extends TenantAwareRepository<AttendanceRecord, Long> {

    // =====================================================
    // BASIC QUERIES WITH TENANT ISOLATION
    // =====================================================

    /**
     * Find attendance record by tenant, employee, and date
     */
    Optional<AttendanceRecord> findByTenantIdAndEmployeeIdAndAttendanceDate(
            Long tenantId, Long employeeId, LocalDate date);

    /**
     * Check if attendance exists for given tenant, employee, and date
     */
    boolean existsByTenantIdAndEmployeeIdAndAttendanceDate(
            Long tenantId, Long employeeId, LocalDate date);

    // =====================================================
    // EMPLOYEE ATTENDANCE (Self view)
    // =====================================================

    /**
     * Get employee attendance for date range (paginated)
     */
    Page<AttendanceRecord> findByTenantIdAndEmployeeIdAndAttendanceDateBetween(
            Long tenantId, Long employeeId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Get employee attendance for date range (list)
     */
    List<AttendanceRecord> findByTenantIdAndEmployeeIdAndAttendanceDateBetween(
            Long tenantId, Long employeeId, LocalDate startDate, LocalDate endDate);

    // =====================================================
    // TEAM ATTENDANCE (Manager view)
    // =====================================================

    /**
     * Get attendance for multiple employees (team) for date range (paginated)
     */
    Page<AttendanceRecord> findByTenantIdAndEmployeeIdInAndAttendanceDateBetween(
            Long tenantId, List<Long> employeeIds, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Get attendance for multiple employees (team) for date range (list)
     */
    List<AttendanceRecord> findByTenantIdAndEmployeeIdInAndAttendanceDateBetween(
            Long tenantId, List<Long> employeeIds, LocalDate startDate, LocalDate endDate);

    /**
     * Get team attendance by manager ID (using employee manager relationship)
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.employee.manager.id = :managerId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    Page<AttendanceRecord> findTeamAttendanceByManagerId(
            @Param("tenantId") Long tenantId,
            @Param("managerId") Long managerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    // =====================================================
    // ADMIN/TENANT WIDE ATTENDANCE
    // =====================================================

    /**
     * Get all attendance for tenant for date range (paginated)
     */
    Page<AttendanceRecord> findByTenantIdAndAttendanceDateBetween(
            Long tenantId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Get all attendance for tenant for date range (list)
     */
    List<AttendanceRecord> findByTenantIdAndAttendanceDateBetween(
            Long tenantId, LocalDate startDate, LocalDate endDate);

    /**
     * Get attendance for specific date (paginated)
     */
    Page<AttendanceRecord> findByTenantIdAndAttendanceDate(
            Long tenantId, LocalDate date, Pageable pageable);

    /**
     * Get attendance for specific date (list)
     */
    List<AttendanceRecord> findByTenantIdAndAttendanceDate(
            Long tenantId, LocalDate date);

    // =====================================================
    // STATISTICS QUERIES
    // =====================================================

    /**
     * Count attendance by status for a specific date
     */
    long countByTenantIdAndAttendanceDateAndStatus(
            Long tenantId, LocalDate date, AttendanceStatus status);

    /**
     * Count attendance by multiple statuses for a specific date
     */
    long countByTenantIdAndAttendanceDateAndStatusIn(
            Long tenantId, LocalDate date, List<AttendanceStatus> statuses);

    /**
     * Count attendance by status for date range
     */
    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "AND a.status = :status")
    long countByTenantIdAndDateRangeAndStatus(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") AttendanceStatus status);

    /**
     * Count attendance by multiple statuses for date range
     */
    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "AND a.status IN :statuses")
    long countByTenantIdAndDateRangeAndStatuses(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") List<AttendanceStatus> statuses);

    // =====================================================
    // SUMMARY & GROUPING QUERIES
    // =====================================================

    /**
     * Get status count grouped by status for date range
     */
    @Query("SELECT a.status, COUNT(a) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.employee.id = :employeeId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "GROUP BY a.status")
    List<Object[]> getStatusCountByEmployeeAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Get total overtime for an employee for date range
     */
    @Query("SELECT COALESCE(SUM(a.overtimeHours), 0) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.employee.id = :employeeId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    Double getTotalOvertimeByEmployeeAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    /**
     * Delete all attendance records for a tenant on a specific date
     */
    @Modifying
    @Query("DELETE FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate = :date")
    void deleteByTenantIdAndAttendanceDate(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date);

    /**
     * Delete attendance record for specific employee and date
     */
    @Modifying
    @Query("DELETE FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.employee.id = :employeeId " +
            "AND a.attendanceDate = :date")
    void deleteByTenantIdAndEmployeeIdAndAttendanceDate(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    /**
     * Search attendance records by employee name or code
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "AND (LOWER(a.employee.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.employee.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.employee.employeeCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<AttendanceRecord> searchAttendanceByDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    // =====================================================
    // DASHBOARD QUERIES
    // =====================================================

    /**
     * Get today's attendance summary for all employees in tenant
     */
    @Query("SELECT a.status, COUNT(a) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId AND a.attendanceDate = :date " +
            "GROUP BY a.status")
    List<Object[]> getTodayAttendanceSummary(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date);

    /**
     * Get attendance percentage for date range
     */
    @Query("SELECT COUNT(DISTINCT a.employee.id) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "AND a.status IN ('PRESENT', 'LATE', 'HALF_DAY')")
    long countEmployeesPresentInDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // =====================================================
    // LEAVE QUERIES
    // =====================================================

    /**
     * Get employees on leave for a specific date
     */
    @Query("SELECT a.employee FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate = :date " +
            "AND a.status = 'ON_LEAVE'")
    List<com.sonixhr.entity.employee.Employee> getEmployeesOnLeave(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date);

    /**
     * Count employees on leave for a date range
     */
    @Query("SELECT COUNT(DISTINCT a.employee.id) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "AND a.status = 'ON_LEAVE'")
    long countEmployeesOnLeaveInDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}