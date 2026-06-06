package com.sonixhr.service.attendance;

import com.sonixhr.dto.attendance.AttendanceDashboardStats;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.attendance.AttendanceApprovalStatus;
import com.sonixhr.enums.attendance.AttendanceStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManualAttendanceService {

    private final ManualAttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    private static final LocalTime LATE_THRESHOLD = LocalTime.of(10, 0); // 10:00 AM
    private static final double HALF_DAY_HOURS = 4.0;
    private static final int MAX_PAST_DAYS = 90;
    private static final int MAX_FUTURE_LEAVE_DAYS = 30;

    // =====================================================
    // MARK ATTENDANCE BY MANAGER (Team Member)
    // =====================================================

    @Transactional
    public AttendanceRecord markAttendanceByManager(Long managerEmployeeId, Long employeeId,
                                                    LocalDate attendanceDate, LocalTime checkInTime,
                                                    LocalTime checkOutTime, String reason,
                                                    Long tenantId, Long userId) {

        log.info("Manager {} marking attendance for employee: {} on date: {}",
                managerEmployeeId, employeeId, attendanceDate);

        // 1. Verify manager exists
        Employee manager = employeeRepository.findById(managerEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + managerEmployeeId));

        // 2. Verify employee exists and reports to this manager
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (employee.getManager() == null || !employee.getManager().getId().equals(managerEmployeeId)) {
            throw new BusinessException("This employee is not in your team");
        }

        // 3. Validate date rules
        validateAttendanceDate(employee, attendanceDate, checkInTime != null);

        // 4. Check if attendance already exists
        if (attendanceRepository.existsByEmployeeIdAndAttendanceDate(employeeId, attendanceDate)) {
            throw new BusinessException("Attendance already marked for this date. Use update endpoint to modify.");
        }

        // 5. Calculate working hours
        Double workingHours = calculateWorkingHours(checkInTime, checkOutTime);

        // 6. Determine status
        AttendanceStatus status = determineStatus(checkInTime, workingHours);

        // 7. Build and save attendance record
        AttendanceRecord attendance = AttendanceRecord.builder()
                .tenant(employee.getTenant())
                .employee(employee)
                .markedByManager(manager)
                .attendanceDate(attendanceDate)
                .checkInTime(checkInTime)
                .checkOutTime(checkOutTime)
                .totalWorkingHours(workingHours)
                .reason(reason)
                .attendanceStatus(status)
                .approvalStatus(AttendanceApprovalStatus.APPROVED)
                .createdBy(userId)
                .build();

        AttendanceRecord saved = attendanceRepository.save(attendance);
        log.info("Manager {} marked attendance for employee: {} on date: {} with status: {}",
                managerEmployeeId, employeeId, attendanceDate, status);

        return saved;
    }

    // =====================================================
    // MARK ATTENDANCE BY SUPER ADMIN
    // =====================================================

    @Transactional
    public AttendanceRecord markAttendanceByAdmin(Long employeeId, LocalDate attendanceDate,
                                                  LocalTime checkInTime, LocalTime checkOutTime,
                                                  String reason, Long tenantId, Long adminUserId,
                                                  String adminName) {

        log.info("Admin {} marking attendance for employee: {} on date: {}", adminName, employeeId, attendanceDate);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (attendanceRepository.existsByEmployeeIdAndAttendanceDate(employeeId, attendanceDate)) {
            throw new BusinessException("Attendance already marked for this date");
        }

        Double workingHours = calculateWorkingHours(checkInTime, checkOutTime);
        AttendanceStatus status = determineStatus(checkInTime, workingHours);

        AttendanceRecord attendance = AttendanceRecord.builder()
                .tenant(employee.getTenant())
                .employee(employee)
                .markedByAdminId(adminUserId)
                .markedByAdminName(adminName)
                .markedByManager(null)
                .attendanceDate(attendanceDate)
                .checkInTime(checkInTime)
                .checkOutTime(checkOutTime)
                .totalWorkingHours(workingHours)
                .reason(reason)
                .attendanceStatus(status)
                .approvalStatus(AttendanceApprovalStatus.APPROVED)
                .approvedBy(adminUserId)
                .approvedByName(adminName)
                .approvedAt(LocalDateTime.now())
                .createdBy(adminUserId)
                .build();

        return attendanceRepository.save(attendance);
    }

    // =====================================================
    // UPDATE ATTENDANCE
    // =====================================================

    @Transactional
    public AttendanceRecord updateAttendance(Long attendanceId, Long managerEmployeeId,
                                             LocalTime checkInTime, LocalTime checkOutTime,
                                             String reason, boolean isAdmin) {

        AttendanceRecord attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + attendanceId));

        // Validate authorization
        if (!isAdmin) {
            if (attendance.getMarkedByManager() == null ||
                    !attendance.getMarkedByManager().getId().equals(managerEmployeeId)) {
                throw new BusinessException("You are not authorized to update this attendance");
            }
        }

        // Validate date rules for update
        validateAttendanceDate(attendance.getEmployee(), attendance.getAttendanceDate(), checkInTime != null);

        // Update fields
        if (checkInTime != null) attendance.setCheckInTime(checkInTime);
        if (checkOutTime != null) attendance.setCheckOutTime(checkOutTime);
        if (reason != null) attendance.setReason(reason);

        // Recalculate working hours
        Double workingHours = calculateWorkingHours(attendance.getCheckInTime(), attendance.getCheckOutTime());
        attendance.setTotalWorkingHours(workingHours);

        // Re-determine status
        attendance.setAttendanceStatus(determineStatus(attendance.getCheckInTime(), workingHours));

        return attendanceRepository.save(attendance);
    }

    // =====================================================
    // DELETE ATTENDANCE RECORD
    // =====================================================

    @Transactional
    public void deleteAttendanceRecord(Long attendanceId, Long adminId) {
        log.info("Admin {} deleting attendance record: {}", adminId, attendanceId);

        AttendanceRecord attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + attendanceId));

        attendanceRepository.delete(attendance);
        log.info("Successfully deleted attendance record: {}", attendanceId);
    }

    // =====================================================
    // GET TEAM ATTENDANCE (Manager View)
    // =====================================================

    public Page<AttendanceRecord> getTeamAttendance(Long managerEmployeeId, LocalDate startDate,
                                                    LocalDate endDate, Pageable pageable) {
        log.info("Getting team attendance for manager: {} between {} and {}", managerEmployeeId, startDate, endDate);

        validateDateRange(startDate, endDate);

        return attendanceRepository.findTeamAttendanceByManagerIdAndDateRange(
                managerEmployeeId, startDate, endDate, pageable);
    }

    // =====================================================
    // GET ALL ATTENDANCE (Super Admin View)
    // =====================================================

    public Page<AttendanceRecord> getAllAttendance(Long tenantId, LocalDate startDate,
                                                   LocalDate endDate, Long departmentId, Pageable pageable) {
        log.info("Getting all attendance for tenant: {} between {} and {}", tenantId, startDate, endDate);

        validateDateRange(startDate, endDate);

        if (departmentId != null) {
            return attendanceRepository.findAllByTenantIdAndDepartmentIdAndDateRange(
                    tenantId, departmentId, startDate, endDate, pageable);
        }

        return attendanceRepository.findAllByTenantIdAndDateRange(tenantId, startDate, endDate, pageable);
    }

    // =====================================================
    // GET EMPLOYEE ATTENDANCE (Self view for employee)
    // =====================================================

    public Page<AttendanceRecord> getEmployeeAttendance(Long employeeId, LocalDate startDate,
                                                        LocalDate endDate, Pageable pageable) {
        log.info("Getting attendance for employee: {} between {} and {}", employeeId, startDate, endDate);

        validateDateRange(startDate, endDate);

        return attendanceRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate, pageable);
    }

    // =====================================================
    // GET TODAY'S ATTENDANCE FOR EMPLOYEE
    // =====================================================

    public AttendanceRecord getTodayAttendance(Long employeeId) {
        LocalDate today = LocalDate.now();
        log.info("Getting today's attendance for employee: {}", employeeId);

        return attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, today)
                .orElse(null); // Return null if not found (controller will handle)
    }

    // =====================================================
    // GET ATTENDANCE SUMMARY
    // =====================================================

    public Map<String, Object> getAttendanceSummary(Long employeeId, int year, int month) {
        log.info("Getting attendance summary for employee: {} for {}-{}", employeeId, year, month);

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        Page<AttendanceRecord> records = attendanceRepository.findByEmployeeIdAndDateRange(
                employeeId, startDate, endDate, Pageable.unpaged());

        Map<String, Object> summary = new HashMap<>();
        summary.put("year", year);
        summary.put("month", month);
        summary.put("totalDays", endDate.getDayOfMonth());

        long present = 0, late = 0, halfDay = 0, absent = 0, leave = 0;
        double totalWorkingHours = 0;

        for (AttendanceRecord record : records) {
            switch (record.getAttendanceStatus()) {
                case PRESENT:
                    present++;
                    break;
                case LATE:
                    late++;
                    break;
                case HALF_DAY:
                    halfDay++;
                    break;
                case ABSENT:
                    absent++;
                    break;
                case ON_LEAVE:
                    leave++;
                    break;
            }

            if (record.getTotalWorkingHours() != null) {
                totalWorkingHours += record.getTotalWorkingHours();
            }
        }

        summary.put("present", present);
        summary.put("late", late);
        summary.put("halfDay", halfDay);
        summary.put("absent", absent);
        summary.put("onLeave", leave);
        summary.put("totalWorkingHours", totalWorkingHours);
        summary.put("attendanceRate", (present + late + halfDay) * 100.0 / endDate.getDayOfMonth());

        return summary;
    }

    // =====================================================
    // GET ATTENDANCE CALENDAR
    // =====================================================

    public Map<LocalDate, AttendanceRecord> getAttendanceCalendar(Long employeeId, int year, int month) {
        log.info("Getting attendance calendar for employee: {} for {}-{}", employeeId, year, month);

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<AttendanceRecord> records = attendanceRepository.findByEmployeeIdAndDateRange(
                employeeId, startDate, endDate, Pageable.unpaged()).getContent();

        Map<LocalDate, AttendanceRecord> calendar = new HashMap<>();
        for (AttendanceRecord record : records) {
            calendar.put(record.getAttendanceDate(), record);
        }

        return calendar;
    }

    // =====================================================
    // GET TOTAL ACTIVE EMPLOYEES
    // =====================================================

    public long getTotalActiveEmployees(Long tenantId) {
        log.info("Getting total active employees for tenant: {}", tenantId);
        return employeeRepository.countByTenantIdAndIsActiveTrue(tenantId);
    }

    // =====================================================
    // GET TOTAL PRESENT DAYS
    // =====================================================

    public long getTotalPresentDays(Long tenantId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting total present days for tenant: {} between {} and {}", tenantId, startDate, endDate);
        return attendanceRepository.countByTenantIdAndAttendanceStatusInAndDateBetween(
                tenantId,
                List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.HALF_DAY),
                startDate,
                endDate);
    }

    // =====================================================
    // GET TOTAL ABSENT DAYS
    // =====================================================

    public long getTotalAbsentDays(Long tenantId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting total absent days for tenant: {} between {} and {}", tenantId, startDate, endDate);
        return attendanceRepository.countByTenantIdAndAttendanceStatusAndDateBetween(
                tenantId, AttendanceStatus.ABSENT, startDate, endDate);
    }

    // =====================================================
    // GET AVERAGE CHECK-IN TIME
    // =====================================================

    public LocalTime getAverageCheckInTime(Long tenantId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting average check-in time for tenant: {} between {} and {}", tenantId, startDate, endDate);
        return attendanceRepository.getAverageCheckInTime(tenantId, startDate, endDate)
                .orElse(LocalTime.of(9, 0)); // Default to 9:00 AM if no data
    }

    // =====================================================
    // GET AVERAGE CHECK-OUT TIME
    // =====================================================

    public LocalTime getAverageCheckOutTime(Long tenantId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting average check-out time for tenant: {} between {} and {}", tenantId, startDate, endDate);
        return attendanceRepository.getAverageCheckOutTime(tenantId, startDate, endDate)
                .orElse(LocalTime.of(18, 0)); // Default to 6:00 PM if no data
    }

    // =====================================================
    // GET ATTENDANCE RATE
    // =====================================================

    public double getAttendanceRate(Long tenantId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting attendance rate for tenant: {} between {} and {}", tenantId, startDate, endDate);

        long totalEmployees = getTotalActiveEmployees(tenantId);
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        long totalPresentDays = getTotalPresentDays(tenantId, startDate, endDate);

        long maxPossibleAttendance = totalEmployees * totalDays;

        if (maxPossibleAttendance == 0) {
            return 0.0;
        }

        return (totalPresentDays * 100.0) / maxPossibleAttendance;
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    /**
     * Validate attendance date rules
     */
    private void validateAttendanceDate(Employee employee, LocalDate attendanceDate, boolean hasCheckInTime) {
        LocalDate today = LocalDate.now();

        // Rule 1: Cannot mark before hire date
        if (attendanceDate.isBefore(employee.getHireDate())) {
            throw new BusinessException("Cannot mark attendance before hire date: " + employee.getHireDate());
        }

        // Rule 2: Cannot mark after termination/resignation
        if (employee.getLastWorkingDate() != null && attendanceDate.isAfter(employee.getLastWorkingDate())) {
            throw new BusinessException("Cannot mark attendance after last working date: " + employee.getLastWorkingDate());
        }

        // Rule 3: Future dates with check-in time are not allowed (only leave)
        if (attendanceDate.isAfter(today) && hasCheckInTime) {
            throw new BusinessException("Cannot mark PRESENT/LATE for future dates. Use leave marking for future dates.");
        }

        // Rule 4: Cannot mark older than MAX_PAST_DAYS days
        LocalDate maxPastDate = today.minusDays(MAX_PAST_DAYS);
        if (attendanceDate.isBefore(maxPastDate)) {
            throw new BusinessException("Cannot mark attendance older than " + MAX_PAST_DAYS + " days");
        }

        // Rule 5: Future leave cannot exceed MAX_FUTURE_LEAVE_DAYS
        if (attendanceDate.isAfter(today.plusDays(MAX_FUTURE_LEAVE_DAYS))) {
            throw new BusinessException("Cannot mark leave for dates beyond " + MAX_FUTURE_LEAVE_DAYS + " days in advance");
        }
    }

    /**
     * Validate date range
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("Start date cannot be after end date");
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > 365) {
            throw new BusinessException("Date range cannot exceed 365 days");
        }
    }

    /**
     * Calculate working hours between check-in and check-out
     */
    private Double calculateWorkingHours(LocalTime checkInTime, LocalTime checkOutTime) {
        if (checkInTime == null || checkOutTime == null) {
            return null;
        }

        long minutes = ChronoUnit.MINUTES.between(checkInTime, checkOutTime);
        double hours = minutes / 60.0;
        return Math.round(hours * 100.0) / 100.0;
    }

    /**
     * Determine attendance status based on check-in time and working hours
     */
    private AttendanceStatus determineStatus(LocalTime checkInTime, Double workingHours) {
        // Case 1: No check-in = ABSENT
        if (checkInTime == null) {
            return AttendanceStatus.ABSENT;
        }

        // Case 2: Late check-in (after threshold)
        if (checkInTime.isAfter(LATE_THRESHOLD)) {
            return AttendanceStatus.LATE;
        }

        // Case 3: Half day (less than 4 hours)
        if (workingHours != null && workingHours < HALF_DAY_HOURS) {
            return AttendanceStatus.HALF_DAY;
        }

        // Case 4: Full day present
        return AttendanceStatus.PRESENT;
    }
}