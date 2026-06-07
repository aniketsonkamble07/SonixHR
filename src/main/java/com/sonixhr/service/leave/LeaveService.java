package com.sonixhr.service.leave;

import com.sonixhr.dto.leave.LeaveRequestDTO;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.entity.leave.PublicHoliday;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.enums.attendance.AttendanceStatus;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.LeaveType;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.leave.LeaveRequestRepository;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final ManualAttendanceRepository attendanceRepository;
    private final TenantLeaveSettingsRepository settingsRepository;
    private final PublicHolidayRepository holidayRepository;

    // =====================================================
    // WEEKEND CHECK BASED ON TENANT CONFIGURATION
    // =====================================================

    /**
     * Check if a date is a weekend based on tenant configuration
     */
    private boolean isWeekendForTenant(LocalDate date, TenantLeaveSettings settings) {
        if (settings == null) {
            // Default: Saturday and Sunday are weekends
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();

        switch (settings.getWeekendConfig()) {
            case SATURDAY_SUNDAY:
                return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            case FRIDAY_SATURDAY:
                return dayOfWeek == DayOfWeek.FRIDAY || dayOfWeek == DayOfWeek.SATURDAY;
            case SUNDAY_ONLY:
                return dayOfWeek == DayOfWeek.SUNDAY;
            case CUSTOM:
                return isCustomWeekend(date, settings.getCustomWeekendDays());
            default:
                return false;
        }
    }

    /**
     * Parse custom weekend days from JSON configuration
     */
    private boolean isCustomWeekend(LocalDate date, String customWeekendDays) {
        if (customWeekendDays == null || customWeekendDays.isEmpty()) {
            return false;
        }

        try {
            // Expected format: {"days": ["FRIDAY", "SATURDAY"]}
            String dayOfWeek = date.getDayOfWeek().toString();
            return customWeekendDays.contains(dayOfWeek);
        } catch (Exception e) {
            log.warn("Error parsing custom weekend days: {}", customWeekendDays);
            return false;
        }
    }

    // =====================================================
    // PUBLIC HOLIDAY CHECK
    // =====================================================

    /**
     * Check if a date is a public holiday
     */
    private boolean isPublicHoliday(Long tenantId, LocalDate date, TenantLeaveSettings settings) {
        if (settings == null) {
            return false;
        }

        // Check if tenant wants to include holidays
        if (!settings.getIncludeNationalHolidays() && !settings.getIncludeStateHolidays()) {
            return false;
        }

        // Check national holidays
        if (settings.getIncludeNationalHolidays()) {
            Optional<PublicHoliday> nationalHoliday = holidayRepository
                    .findByTenantIdAndHolidayDateAndType(tenantId, date, "NATIONAL");
            if (nationalHoliday.isPresent()) {
                return true;
            }
        }

        // Check state holidays
        if (settings.getIncludeStateHolidays() && settings.getState() != null && !settings.getState().isEmpty()) {
            Optional<PublicHoliday> stateHoliday = holidayRepository
                    .findByTenantIdAndHolidayDateAndRegion(tenantId, date, settings.getState());
            if (stateHoliday.isPresent()) {
                return true;
            }
        }

        return false;
    }

    // =====================================================
    // LEAVE DAYS CALCULATION (Excluding Weekends/Holidays)
    // =====================================================

    /**
     * Calculate total leave days excluding weekends and holidays based on tenant settings
     */
    private double calculateTotalLeaveDays(Long tenantId, LocalDate startDate, LocalDate endDate,
                                           LeaveType leaveType, TenantLeaveSettings settings) {
        double totalDays = 0;
        LocalDate date = startDate;

        while (!date.isAfter(endDate)) {
            boolean isWeekend = isWeekendForTenant(date, settings);
            boolean isHoliday = isPublicHoliday(tenantId, date, settings);

            // Decide whether to count this day
            boolean countDay = true;

            // If it's a weekend
            if (isWeekend) {
                // Count weekend only if tenant configures to count weekends as leave
                countDay = settings.getCountWeekendsAsLeave() != null && settings.getCountWeekendsAsLeave();
            }

            // If it's a holiday
            if (isHoliday) {
                // Count holiday only if tenant configures to count holidays as leave
                countDay = settings.getCountHolidaysAsLeave() != null && settings.getCountHolidaysAsLeave();
            }

            if (countDay) {
                totalDays++;
            }

            date = date.plusDays(1);
        }

        return totalDays;
    }

    // =====================================================
    // CREATE ATTENDANCE FOR LEAVE DAYS
    // =====================================================

    private void createAttendanceForLeaveDays(LeaveRequest leave) {
        LocalDate date = leave.getStartDate();
        TenantLeaveSettings settings = settingsRepository.findById(leave.getTenant().getId()).orElse(null);

        while (!date.isAfter(leave.getEndDate())) {
            boolean isWeekend = isWeekendForTenant(date, settings);
            boolean isHoliday = isPublicHoliday(leave.getTenant().getId(), date, settings);

            // Skip creating attendance for weekends if they are not counted as working days
            if (isWeekend && (settings == null || !settings.getCountWeekendsAsLeave())) {
                date = date.plusDays(1);
                continue;
            }

            // Skip creating attendance for holidays if they are not counted as working days
            if (isHoliday && (settings == null || !settings.getCountHolidaysAsLeave())) {
                date = date.plusDays(1);
                continue;
            }

            Optional<AttendanceRecord> existingAttendance = attendanceRepository
                    .findByTenantIdAndEmployeeIdAndAttendanceDate(
                            leave.getTenant().getId(), leave.getEmployee().getId(), date);

            if (existingAttendance.isEmpty()) {
                AttendanceRecord attendance = AttendanceRecord.builder()
                        .tenant(leave.getTenant())
                        .employee(leave.getEmployee())
                        .attendanceDate(date)
                        .status(AttendanceStatus.ON_LEAVE)
                        .reason("Leave: " + leave.getLeaveType().getDisplayName() +
                                (leave.getReason() != null ? " - " + leave.getReason() : ""))
                        .markedBy(leave.getApprovedBy())
                        .markedByName(leave.getApprovedByName())
                        .markedByRole("MANAGER")
                        .markedAt(LocalDateTime.now())
                        .build();
                attendanceRepository.save(attendance);
                log.info("Created attendance record for leave on: {}", date);
            }
            date = date.plusDays(1);
        }
    }

    // =====================================================
    // LEAVE BALANCE (With Tenant Settings)
    // =====================================================

    /**
     * Get leave balance considering tenant settings
     */
    public Map<String, Object> getLeaveBalanceWithTenantSettings(Long employeeId, Long tenantId) {
        TenantLeaveSettings settings = settingsRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant leave settings not configured. Please contact admin."));

        int year = LocalDate.now().getYear();
        Map<String, Object> balance = new LinkedHashMap<>();

        // Get used leave days for each type
        double casualUsed = leaveRepository.getUsedLeaveDays(employeeId, LeaveType.CASUAL, year);
        double sickUsed = leaveRepository.getUsedLeaveDays(employeeId, LeaveType.SICK, year);
        double earnedUsed = leaveRepository.getUsedLeaveDays(employeeId, LeaveType.EARNED, year);
        double emergencyUsed = leaveRepository.getUsedLeaveDays(employeeId, LeaveType.EMERGENCY, year);

        // Calculate remaining
        balance.put("CASUAL", Map.of(
                "total", settings.getCasualLeavePerYear(),
                "used", casualUsed,
                "remaining", Math.max(0, settings.getCasualLeavePerYear() - casualUsed),
                "color", "#4caf50"
        ));
        balance.put("SICK", Map.of(
                "total", settings.getSickLeavePerYear(),
                "used", sickUsed,
                "remaining", Math.max(0, settings.getSickLeavePerYear() - sickUsed),
                "color", "#2196f3"
        ));
        balance.put("EARNED", Map.of(
                "total", settings.getEarnedLeavePerYear(),
                "used", earnedUsed,
                "remaining", Math.max(0, settings.getEarnedLeavePerYear() - earnedUsed),
                "color", "#ff9800"
        ));
        balance.put("EMERGENCY", Map.of(
                "total", 3,
                "used", emergencyUsed,
                "remaining", Math.max(0, 3 - emergencyUsed),
                "color", "#f44336"
        ));
        balance.put("UNPAID", Map.of(
                "total", "Unlimited",
                "used", 0,
                "remaining", "Unlimited",
                "color", "#9e9e9e"
        ));

        // Add summary
        double totalUsed = casualUsed + sickUsed + earnedUsed + emergencyUsed;
        double totalAvailable = settings.getCasualLeavePerYear() + settings.getSickLeavePerYear() +
                settings.getEarnedLeavePerYear() + 3;

        balance.put("summary", Map.of(
                "totalUsed", totalUsed,
                "totalAvailable", totalAvailable,
                "remaining", totalAvailable - totalUsed,
                "utilizationPercentage", totalAvailable > 0 ? (totalUsed / totalAvailable) * 100 : 0
        ));

        return balance;
    }

    // =====================================================
    // REQUEST LEAVE (With Tenant Settings Validation)
    // =====================================================

    /**
     * Request leave with tenant setting validation
     */
    @Transactional
    public LeaveResponseDTO requestLeaveWithTenantSettings(Long employeeId, LeaveRequestDTO request, Employee currentUser) {
        log.info("Employee {} requesting leave from {} to {}", employeeId, request.getStartDate(), request.getEndDate());

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        TenantLeaveSettings settings = settingsRepository.findById(employee.getTenant().getId())
                .orElseThrow(() -> new BusinessException("Tenant leave settings not configured. Please contact admin."));

        // Validate dates
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("Start date cannot be after end date");
        }

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Cannot request leave for past dates");
        }

        // Check maximum consecutive leave days
        long daysBetween = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        if (daysBetween > settings.getMaxConsecutiveLeaveDays()) {
            throw new BusinessException("Cannot request more than " + settings.getMaxConsecutiveLeaveDays() +
                    " consecutive leave days");
        }

        // Calculate total leave days based on tenant settings
        double totalDays = calculateTotalLeaveDays(employee.getTenant().getId(),
                request.getStartDate(), request.getEndDate(), request.getLeaveType(), settings);

        if (totalDays <= 0) {
            throw new BusinessException("No working days selected for leave. Selected dates fall on weekends/holidays.");
        }

        // Check leave balance (skip for unpaid leave)
        if (request.getLeaveType() != LeaveType.UNPAID) {
            checkLeaveBalanceWithSettings(employeeId, request.getLeaveType(), totalDays, settings);
        }

        // Check for overlapping leave
        if (leaveRepository.hasOverlappingLeave(employeeId, request.getStartDate(), request.getEndDate())) {
            throw new BusinessException("You already have a pending or approved leave in this date range");
        }

        // Determine if auto-approval is needed
        boolean autoApprove = !settings.getLeaveApprovalRequired() ||
                (settings.getAutoApproveForManager() && currentUser.isManager());

        LeaveStatus initialStatus = autoApprove ? LeaveStatus.APPROVED : LeaveStatus.PENDING;

        LeaveRequest leave = LeaveRequest.builder()
                .tenant(employee.getTenant())
                .employee(employee)
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalDays(totalDays)
                .reason(request.getReason())
                .status(initialStatus)
                .build();

        if (autoApprove) {
            leave.setApprovedBy(currentUser.getId());
            leave.setApprovedByName(currentUser.getFullName());
            leave.setApprovedAt(LocalDateTime.now());
            log.info("Leave request auto-approved for employee: {}", employeeId);
        }

        LeaveRequest saved = leaveRepository.save(leave);

        // If auto-approved, create attendance records
        if (autoApprove) {
            createAttendanceForLeaveDays(saved);
        }

        return convertToResponse(saved);
    }

    /**
     * Check leave balance with tenant settings
     */
    private void checkLeaveBalanceWithSettings(Long employeeId, LeaveType leaveType, double requestedDays,
                                               TenantLeaveSettings settings) {
        int year = LocalDate.now().getYear();
        double usedDays = leaveRepository.getUsedLeaveDays(employeeId, leaveType, year);
        double availableDays = getAvailableDaysForLeaveType(leaveType, settings);
        double remainingDays = availableDays - usedDays;

        if (requestedDays > remainingDays) {
            throw new BusinessException(String.format(
                    "Insufficient %s balance. Available: %.1f days, Requested: %.1f days",
                    leaveType.getDisplayName(), remainingDays, requestedDays));
        }
    }

    /**
     * Get available days for leave type based on tenant settings
     */
    private double getAvailableDaysForLeaveType(LeaveType leaveType, TenantLeaveSettings settings) {
        switch (leaveType) {
            case CASUAL: return settings.getCasualLeavePerYear();
            case SICK: return settings.getSickLeavePerYear();
            case EARNED: return settings.getEarnedLeavePerYear();
            case EMERGENCY: return 3;
            default: return Double.MAX_VALUE;
        }
    }

    // =====================================================
    // GET LEAVES FOR CALENDAR
    // =====================================================

    /**
     * Get approved leaves for calendar view
     */
    public List<LeaveResponseDTO> getApprovedLeavesForCalendar(Long employeeId, Long tenantId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<LeaveRequest> leaves = leaveRepository.findApprovedLeavesInDateRange(tenantId, employeeId, startDate, endDate);

        return leaves.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    // =====================================================
    // APPROVE LEAVE
    // =====================================================

    @Transactional
    public LeaveResponseDTO approveLeave(Long leaveId, Long approverId, String approverName) {
        log.info("Approving leave request: {} by {}", leaveId, approverName);

        LeaveRequest leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException("Only pending leave requests can be approved");
        }

        leave.setStatus(LeaveStatus.APPROVED);
        leave.setApprovedBy(approverId);
        leave.setApprovedByName(approverName);
        leave.setApprovedAt(LocalDateTime.now());

        // Create attendance records for leave days
        createAttendanceForLeaveDays(leave);

        LeaveRequest saved = leaveRepository.save(leave);
        log.info("Leave request {} approved", leaveId);

        return convertToResponse(saved);
    }

    // =====================================================
    // REJECT LEAVE
    // =====================================================

    @Transactional
    public LeaveResponseDTO rejectLeave(Long leaveId, String rejectionReason, Long rejectorId, String rejectorName) {
        log.info("Rejecting leave request: {} by {}", leaveId, rejectorName);

        LeaveRequest leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException("Only pending leave requests can be rejected");
        }

        leave.setStatus(LeaveStatus.REJECTED);
        leave.setRejectionReason(rejectionReason);
        leave.setApprovedBy(rejectorId);
        leave.setApprovedByName(rejectorName);
        leave.setApprovedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRepository.save(leave);
        log.info("Leave request {} rejected", leaveId);

        return convertToResponse(saved);
    }

    // =====================================================
    // CANCEL LEAVE
    // =====================================================

    @Transactional
    public LeaveResponseDTO cancelLeave(Long leaveId, Long employeeId, String cancellationReason) {
        log.info("Cancelling leave request: {} by employee: {}", leaveId, employeeId);

        LeaveRequest leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw new BusinessException("You can only cancel your own leave requests");
        }

        if (leave.getStatus() == LeaveStatus.APPROVED) {
            // Remove attendance records for leave days
            removeAttendanceForLeaveDays(leave);
        }

        leave.setStatus(LeaveStatus.CANCELLED);
        leave.setRejectionReason(cancellationReason);

        LeaveRequest saved = leaveRepository.save(leave);
        log.info("Leave request {} cancelled", leaveId);

        return convertToResponse(saved);
    }

    /**
     * Remove attendance records when leave is cancelled
     */
    private void removeAttendanceForLeaveDays(LeaveRequest leave) {
        LocalDate date = leave.getStartDate();
        while (!date.isAfter(leave.getEndDate())) {
            attendanceRepository.deleteByTenantIdAndEmployeeIdAndAttendanceDate(
                    leave.getTenant().getId(), leave.getEmployee().getId(), date);
            date = date.plusDays(1);
        }
        log.info("Removed attendance records for cancelled leave from {} to {}",
                leave.getStartDate(), leave.getEndDate());
    }

    // =====================================================
    // GET LEAVES FOR EMPLOYEE/TEAM
    // =====================================================

    public List<LeaveResponseDTO> getMyLeaves(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        List<LeaveRequest> leaves = leaveRepository.findByEmployeeIdAndTenantId(employeeId, employee.getTenant().getId());
        return leaves.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public Page<LeaveResponseDTO> getTeamLeaveRequests(Long managerId, Long tenantId, LeaveStatus status, Pageable pageable) {
        log.info("Getting team leave requests for manager: {}", managerId);

        Page<LeaveRequest> leaves;
        if (status != null) {
            leaves = leaveRepository.findTeamLeaveRequestsByStatus(tenantId, managerId, status, pageable);
        } else {
            leaves = leaveRepository.findTeamLeaveRequests(tenantId, managerId, pageable);
        }

        return leaves.map(this::convertToResponse);
    }

    // =====================================================
    // CONVERSION METHODS
    // =====================================================

    private LeaveResponseDTO convertToResponse(LeaveRequest leave) {
        return LeaveResponseDTO.builder()
                .id(leave.getId())
                .employeeId(leave.getEmployee().getId())
                .employeeName(leave.getEmployee().getFullName())
                .employeeCode(leave.getEmployee().getEmployeeCode())
                .leaveType(leave.getLeaveType())
                .leaveTypeDisplay(leave.getLeaveType().getDisplayName())
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .totalDays(leave.getTotalDays())
                .reason(leave.getReason())
                .status(leave.getStatus())
                .statusDisplay(leave.getStatus().getDisplayName())
                .rejectionReason(leave.getRejectionReason())
                .approvedBy(leave.getApprovedBy())
                .approvedByName(leave.getApprovedByName())
                .approvedAt(leave.getApprovedAt())
                .createdAt(leave.getCreatedAt())
                .build();
    }
}