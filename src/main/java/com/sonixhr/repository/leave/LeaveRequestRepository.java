package com.sonixhr.repository.leave;

import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.LeaveType;
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
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // =====================================================
    // EMPLOYEE SELF QUERIES
    // =====================================================

    /**
     * Get all leave requests for an employee in a tenant
     */
    List<LeaveRequest> findByEmployeeIdAndTenantId(Long employeeId, Long tenantId);

    /**
     * Get paginated leave requests for an employee
     */
    Page<LeaveRequest> findByEmployeeIdAndTenantId(Long employeeId, Long tenantId, Pageable pageable);

    /**
     * Get leave requests by status for an employee
     */
    List<LeaveRequest> findByEmployeeIdAndStatusAndTenantId(Long employeeId, LeaveStatus status, Long tenantId);

    /**
     * Get leave request by ID for an employee
     */
    Optional<LeaveRequest> findByIdAndEmployeeIdAndTenantId(Long id, Long employeeId, Long tenantId);

    // =====================================================
    // MANAGER QUERIES - TEAM LEAVE REQUESTS
    // =====================================================

    /**
     * Get pending leave requests for manager's team
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.employee.manager.id = :managerId AND l.status = :status")
    Page<LeaveRequest> findTeamLeaveRequestsByStatus(@Param("tenantId") Long tenantId,
                                                     @Param("managerId") Long managerId,
                                                     @Param("status") LeaveStatus status,
                                                     Pageable pageable);

    /**
     * Get all leave requests for manager's team
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.employee.manager.id = :managerId")
    Page<LeaveRequest> findTeamLeaveRequests(@Param("tenantId") Long tenantId,
                                             @Param("managerId") Long managerId,
                                             Pageable pageable);

    /**
     * Get leave requests by type for manager's team
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.employee.manager.id = :managerId " +
            "AND l.leaveType = :leaveType")
    List<LeaveRequest> findTeamLeaveRequestsByType(@Param("tenantId") Long tenantId,
                                                   @Param("managerId") Long managerId,
                                                   @Param("leaveType") LeaveType leaveType);

    // =====================================================
    // ADMIN QUERIES - ALL LEAVE REQUESTS
    // =====================================================

    /**
     * Get all leave requests for a tenant (paginated)
     */
    Page<LeaveRequest> findByTenantId(Long tenantId, Pageable pageable);

    /**
     * Get leave requests by status for a tenant
     */
    Page<LeaveRequest> findByTenantIdAndStatus(Long tenantId, LeaveStatus status, Pageable pageable);

    /**
     * Get all leave requests by status for a tenant
     */
    List<LeaveRequest> findByTenantIdAndStatus(Long tenantId, LeaveStatus status);

    /**
     * Get leave requests in date range for a tenant
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.startDate BETWEEN :startDate AND :endDate")
    List<LeaveRequest> findByTenantIdAndDateRange(@Param("tenantId") Long tenantId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    // =====================================================
    // CALENDAR QUERIES
    // =====================================================

    /**
     * Get approved leaves for calendar view
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.employee.id = :employeeId " +
            "AND l.status = 'APPROVED' " +
            "AND ((l.startDate BETWEEN :startDate AND :endDate) OR " +
            "(l.endDate BETWEEN :startDate AND :endDate) OR " +
            "(l.startDate <= :startDate AND l.endDate >= :endDate))")
    List<LeaveRequest> findApprovedLeavesInDateRange(@Param("tenantId") Long tenantId,
                                                     @Param("employeeId") Long employeeId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    /**
     * Get all approved leaves in date range for a tenant
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.status = 'APPROVED' " +
            "AND l.startDate <= :endDate AND l.endDate >= :startDate")
    List<LeaveRequest> findAllApprovedLeavesInDateRange(@Param("tenantId") Long tenantId,
                                                        @Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);

    // =====================================================
    // OVERLAP CHECK
    // =====================================================

    /**
     * Check for overlapping leave requests
     */
    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.employee.id = :employeeId " +
            "AND l.status IN ('PENDING', 'APPROVED') " +
            "AND ((l.startDate <= :endDate AND l.endDate >= :startDate))")
    boolean hasOverlappingLeave(@Param("tenantId") Long tenantId,
                                @Param("employeeId") Long employeeId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    /**
     * Check for overlapping leave excluding a specific leave ID
     */
    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.employee.id = :employeeId " +
            "AND l.id != :leaveId " +
            "AND l.status IN ('PENDING', 'APPROVED') " +
            "AND ((l.startDate <= :endDate AND l.endDate >= :startDate))")
    boolean hasOverlappingLeaveExcludingSelf(@Param("tenantId") Long tenantId,
                                             @Param("employeeId") Long employeeId,
                                             @Param("leaveId") Long leaveId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    // =====================================================
    // LEAVE BALANCE CALCULATION
    // =====================================================

    /**
     * Get total used leave days for a specific leave type in a year
     */
    @Query("SELECT COALESCE(SUM(l.totalDays), 0) FROM LeaveRequest l WHERE l.employee.id = :employeeId " +
            "AND l.leaveType = :leaveType AND l.status = 'APPROVED' " +
            "AND EXTRACT(YEAR FROM l.startDate) = :year")
    double getUsedLeaveDays(@Param("employeeId") Long employeeId,
                            @Param("leaveType") LeaveType leaveType,
                            @Param("year") int year);

    /**
     * Get used leave days grouped by leave type for an employee in a year
     */
    @Query("SELECT l.leaveType, COALESCE(SUM(l.totalDays), 0) FROM LeaveRequest l " +
            "WHERE l.employee.id = :employeeId AND l.status = 'APPROVED' " +
            "AND EXTRACT(YEAR FROM l.startDate) = :year GROUP BY l.leaveType")
    List<Object[]> getUsedLeaveDaysByType(@Param("employeeId") Long employeeId,
                                          @Param("year") int year);

    /**
     * Get total leave days taken in a date range
     */
    @Query("SELECT COALESCE(SUM(l.totalDays), 0) FROM LeaveRequest l " +
            "WHERE l.employee.id = :employeeId AND l.status = 'APPROVED' " +
            "AND l.startDate BETWEEN :startDate AND :endDate")
    double getTotalLeaveDaysInDateRange(@Param("employeeId") Long employeeId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    /**
     * Count pending leave requests for a tenant
     */
    long countByTenantIdAndStatus(Long tenantId, LeaveStatus status);

    /**
     * Count leave requests by status for an employee
     */
    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    /**
     * Count employees on leave today
     */
    @Query("SELECT COUNT(DISTINCT l.employee.id) FROM LeaveRequest l " +
            "WHERE l.tenant.id = :tenantId " +
            "AND l.status = 'APPROVED' " +
            "AND l.startDate <= CURRENT_DATE AND l.endDate >= CURRENT_DATE")
    long countEmployeesOnLeaveToday(@Param("tenantId") Long tenantId);

    /**
     * Get list of employees on leave today
     */
    @Query("SELECT DISTINCT l.employee FROM LeaveRequest l " +
            "WHERE l.tenant.id = :tenantId " +
            "AND l.status = 'APPROVED' " +
            "AND l.startDate <= CURRENT_DATE AND l.endDate >= CURRENT_DATE")
    List<com.sonixhr.entity.employee.Employee> getEmployeesOnLeaveToday(@Param("tenantId") Long tenantId);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    /**
     * Bulk update leave status
     */
    @Modifying
    @Transactional
    @Query("UPDATE LeaveRequest l SET l.status = :status, l.approvedBy = :approvedBy, " +
            "l.approvedByName = :approvedByName, l.approvedAt = CURRENT_TIMESTAMP " +
            "WHERE l.id IN :leaveIds")
    int bulkApproveLeaves(@Param("leaveIds") List<Long> leaveIds,
                          @Param("status") LeaveStatus status,
                          @Param("approvedBy") Long approvedBy,
                          @Param("approvedByName") String approvedByName);

    /**
     * Delete cancelled/rejected leaves older than specified date
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM LeaveRequest l WHERE l.status IN ('CANCELLED', 'REJECTED') " +
            "AND l.updatedAt < :date")
    int deleteOldCancelledLeaves(@Param("date") LocalDate date);

    // =====================================================
    // LEAVE SUMMARY FOR REPORTING
    // =====================================================

    /**
     * Get leave summary by department
     */
    @Query("SELECT l.employee.department.name, COUNT(l), SUM(l.totalDays) FROM LeaveRequest l " +
            "WHERE l.tenant.id = :tenantId AND l.status = 'APPROVED' " +
            "AND YEAR(l.startDate) = :year GROUP BY l.employee.department.name")
    List<Object[]> getLeaveSummaryByDepartment(@Param("tenantId") Long tenantId,
                                               @Param("year") int year);

    /**
     * Get leave summary by month
     */
    @Query("SELECT MONTH(l.startDate), COUNT(l), SUM(l.totalDays) FROM LeaveRequest l " +
            "WHERE l.tenant.id = :tenantId AND l.status = 'APPROVED' " +
            "AND YEAR(l.startDate) = :year GROUP BY MONTH(l.startDate) ORDER BY MONTH(l.startDate)")
    List<Object[]> getLeaveSummaryByMonth(@Param("tenantId") Long tenantId,
                                          @Param("year") int year);

    /**
     * Get upcoming leaves (next 7 days)
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.tenant.id = :tenantId " +
            "AND l.status = 'APPROVED' " +
            "AND l.startDate BETWEEN CURRENT_DATE AND CURRENT_DATE + 7 " +
            "ORDER BY l.startDate ASC")
    List<LeaveRequest> findUpcomingLeaves(@Param("tenantId") Long tenantId);
}