package com.sonixhr.repository.attendance;

import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.enums.attendance.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface ManualAttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    /**
     * Find attendance record by employee ID and date
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.attendanceDate = :attendanceDate")
    Optional<AttendanceRecord> findByEmployeeIdAndAttendanceDate(
            @Param("employeeId") Long employeeId,
            @Param("attendanceDate") LocalDate attendanceDate);

    /**
     * Check if attendance exists for employee on a date
     */
    @Query("SELECT COUNT(a) > 0 FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.attendanceDate = :attendanceDate")
    boolean existsByEmployeeIdAndAttendanceDate(
            @Param("employeeId") Long employeeId,
            @Param("attendanceDate") LocalDate attendanceDate);

    // =====================================================
    // EMPLOYEE ATTENDANCE QUERIES
    // =====================================================

    /**
     * Get all attendance records for an employee within date range
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate ORDER BY a.attendanceDate DESC")
    Page<AttendanceRecord> findByEmployeeIdAndDateRange(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Get all attendance records for an employee (all time)
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId ORDER BY a.attendanceDate DESC")
    Page<AttendanceRecord> findAllByEmployeeId(@Param("employeeId") Long employeeId, Pageable pageable);

    // =====================================================
    // MANAGER / TEAM ATTENDANCE QUERIES
    // =====================================================

    /**
     * Get team attendance (employees under a manager) within date range
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.manager.id = :managerId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate ORDER BY a.attendanceDate DESC, a.employee.firstName ASC")
    Page<AttendanceRecord> findTeamAttendanceByManagerIdAndDateRange(
            @Param("managerId") Long managerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Get pending approval attendance records for a manager's team
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.manager.id = :managerId " +
            "AND a.approvalStatus = 'PENDING' ORDER BY a.attendanceDate ASC")
    List<AttendanceRecord> findPendingApprovalsByManagerId(@Param("managerId") Long managerId);

    /**
     * Get attendance records for a manager's team by status
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.manager.id = :managerId " +
            "AND a.approvalStatus = :approvalStatus AND a.attendanceDate BETWEEN :startDate AND :endDate")
    Page<AttendanceRecord> findTeamAttendanceByManagerIdAndStatusAndDateRange(
            @Param("managerId") Long managerId,
            @Param("approvalStatus") String approvalStatus,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    // =====================================================
    // TENANT-WIDE ATTENDANCE QUERIES (Super Admin)
    // =====================================================

    /**
     * Get all attendance records for a tenant within date range
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate ORDER BY a.attendanceDate DESC")
    Page<AttendanceRecord> findAllByTenantIdAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Get attendance records for a tenant by department within date range
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.employee.department.id = :departmentId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate ORDER BY a.attendanceDate DESC")
    Page<AttendanceRecord> findAllByTenantIdAndDepartmentIdAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("departmentId") Long departmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Get attendance records for a tenant by status
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.tenant.id = :tenantId " +
            "AND a.approvalStatus = :approvalStatus AND a.attendanceDate BETWEEN :startDate AND :endDate")
    Page<AttendanceRecord> findAllByTenantIdAndStatusAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("approvalStatus") String approvalStatus,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    // =====================================================
    // DEPARTMENT ATTENDANCE QUERIES
    // =====================================================

    /**
     * Get attendance records for a department within date range
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.department.id = :departmentId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate ORDER BY a.attendanceDate DESC")
    Page<AttendanceRecord> findByDepartmentIdAndDateRange(
            @Param("departmentId") Long departmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    // =====================================================
    // STATISTICS QUERIES
    // =====================================================

    /**
     * Count attendance records by status for a tenant within date range
     */
    @Query("SELECT a.attendanceStatus, COUNT(a) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "GROUP BY a.attendanceStatus")
    List<Object[]> countAttendanceByStatusAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Count attendance records by approval status for a manager's team
     */
    @Query("SELECT a.approvalStatus, COUNT(a) FROM AttendanceRecord a " +
            "WHERE a.employee.manager.id = :managerId AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "GROUP BY a.approvalStatus")
    List<Object[]> countTeamAttendanceByApprovalStatus(
            @Param("managerId") Long managerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Get monthly attendance summary for an employee
     */
    @Query("SELECT FUNCTION('YEAR', a.attendanceDate) as year, " +
            "FUNCTION('MONTH', a.attendanceDate) as month, " +
            "COUNT(a) as totalDays, " +
            "SUM(CASE WHEN a.attendanceStatus = 'PRESENT' THEN 1 ELSE 0 END) as presentDays, " +
            "SUM(CASE WHEN a.attendanceStatus = 'LATE' THEN 1 ELSE 0 END) as lateDays, " +
            "SUM(CASE WHEN a.attendanceStatus = 'ABSENT' THEN 1 ELSE 0 END) as absentDays " +
            "FROM AttendanceRecord a WHERE a.employee.id = :employeeId " +
            "GROUP BY FUNCTION('YEAR', a.attendanceDate), FUNCTION('MONTH', a.attendanceDate) " +
            "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyAttendanceSummary(@Param("employeeId") Long employeeId);

    // =====================================================
    // AGGREGATION & STATISTICS QUERIES (for Dashboard)
    // =====================================================

    /**
     * Count attendance records by status for a tenant within date range
     */
    @Query("SELECT COUNT(a) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceStatus IN :statuses " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    long countByTenantIdAndAttendanceStatusInAndDateBetween(
            @Param("tenantId") Long tenantId,
            @Param("statuses") List<AttendanceStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Count attendance records by specific status for a tenant within date range
     */
    @Query("SELECT COUNT(a) FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.attendanceStatus = :status " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    long countByTenantIdAndAttendanceStatusAndDateBetween(
            @Param("tenantId") Long tenantId,
            @Param("status") AttendanceStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Get average check-in time for a tenant within date range
     */
    @Query("SELECT AVG(CAST(FUNCTION('HOUR', a.checkInTime) * 60 + FUNCTION('MINUTE', a.checkInTime) AS double)) " +
            "FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.checkInTime IS NOT NULL " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    Optional<Double> getAverageCheckInTimeInMinutes(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Default method to get average check-in time as LocalTime
     */
    default Optional<LocalTime> getAverageCheckInTime(Long tenantId, LocalDate startDate, LocalDate endDate) {
        Optional<Double> avgMinutes = getAverageCheckInTimeInMinutes(tenantId, startDate, endDate);
        if (avgMinutes.isPresent() && avgMinutes.get() > 0) {
            int hours = (int) (avgMinutes.get() / 60);
            int minutes = (int) (avgMinutes.get() % 60);
            return Optional.of(LocalTime.of(hours, minutes));
        }
        return Optional.empty();
    }

    /**
     * Get average check-out time for a tenant within date range
     */
    @Query("SELECT AVG(CAST(FUNCTION('HOUR', a.checkOutTime) * 60 + FUNCTION('MINUTE', a.checkOutTime) AS double)) " +
            "FROM AttendanceRecord a " +
            "WHERE a.tenant.id = :tenantId " +
            "AND a.checkOutTime IS NOT NULL " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    Optional<Double> getAverageCheckOutTimeInMinutes(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Default method to get average check-out time as LocalTime
     */
    default Optional<LocalTime> getAverageCheckOutTime(Long tenantId, LocalDate startDate, LocalDate endDate) {
        Optional<Double> avgMinutes = getAverageCheckOutTimeInMinutes(tenantId, startDate, endDate);
        if (avgMinutes.isPresent() && avgMinutes.get() > 0) {
            int hours = (int) (avgMinutes.get() / 60);
            int minutes = (int) (avgMinutes.get() % 60);
            return Optional.of(LocalTime.of(hours, minutes));
        }
        return Optional.empty();
    }

    // =====================================================
    // LEAVE QUERIES
    // =====================================================

    /**
     * Get leave records for an employee within date range
     */
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId " +
            "AND a.attendanceStatus = 'ON_LEAVE' " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "ORDER BY a.attendanceDate ASC")
    List<AttendanceRecord> findLeavesByEmployeeIdAndDateRange(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Check if employee is on leave on a specific date
     */
    @Query("SELECT COUNT(a) > 0 FROM AttendanceRecord a " +
            "WHERE a.employee.id = :employeeId " +
            "AND a.attendanceDate = :date " +
            "AND a.attendanceStatus = 'ON_LEAVE'")
    boolean isOnLeaveOnDate(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date);

    // =====================================================
    // BULK OPERATIONS (if needed)
    // =====================================================

    /**
     * Delete attendance records for an employee within date range
     */
    @Query("DELETE FROM AttendanceRecord a WHERE a.employee.id = :employeeId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    void deleteByEmployeeIdAndDateRange(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Update approval status for a list of attendance records
     */
    @Query("UPDATE AttendanceRecord a SET a.approvalStatus = :status, " +
            "a.approvedBy = :approvedBy, a.approvedAt = CURRENT_TIMESTAMP " +
            "WHERE a.id IN :attendanceIds")
    void bulkUpdateApprovalStatus(
            @Param("attendanceIds") List<Long> attendanceIds,
            @Param("status") String status,
            @Param("approvedBy") Long approvedBy);
}