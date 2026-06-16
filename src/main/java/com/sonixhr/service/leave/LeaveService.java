package com.sonixhr.service.leave;

import com.sonixhr.dto.leave.LeaveRequestDTO;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.service.EmailService;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.entity.leave.PublicHoliday;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.enums.attendance.AttendanceStatus;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.LeaveType;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.LeavePoliciesNotConfiguredException;
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
    private final LeaveConfigurationService leaveConfigService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // =====================================================
    // PUBLIC HOLIDAY CHECK
    // =====================================================

    /**
     * Get holiday dates for the tenant within the specified date range
     */
    private Set<LocalDate> getHolidayDatesInRange(Long tenantId, LocalDate startDate, LocalDate endDate, TenantLeaveSettings settings) {
        if (settings == null) {
            return Collections.emptySet();
        }

        if (!settings.getIncludeNationalHolidays() && !settings.getIncludeStateHolidays()) {
            return Collections.emptySet();
        }

        List<PublicHoliday> holidays = holidayRepository.findByTenantIdAndHolidayDateBetween(tenantId, startDate, endDate);
        Set<LocalDate> holidayDates = new HashSet<>();
        for (PublicHoliday h : holidays) {
            boolean isNational = "NATIONAL".equalsIgnoreCase(h.getType()) && settings.getIncludeNationalHolidays();
            boolean isState = settings.getIncludeStateHolidays() && 
                              settings.getState() != null && 
                              settings.getState().equalsIgnoreCase(h.getRegion());
            if (isNational || isState) {
                holidayDates.add(h.getHolidayDate());
            }
        }
        return holidayDates;
    }

    // =====================================================
    // LEAVE DAYS CALCULATION (Excluding Weekends/Holidays)
    // =====================================================

    /**
     * Calculate total leave days excluding weekends and holidays based on settings
     */
    private double calculateTotalLeaveDays(Employee employee, LocalDate startDate, LocalDate endDate,
                                           LeaveType leaveType, TenantLeaveSettings settings) {
        double totalDays = 0;
        LocalDate date = startDate;

        Set<LocalDate> holidayDates = getHolidayDatesInRange(employee.getTenant().getId(), startDate, endDate, settings);

        while (!date.isAfter(endDate)) {
            boolean isWeekend = leaveConfigService.isWeekendForEmployee(date, employee, settings);
            boolean isHoliday = holidayDates.contains(date);

            // Decide whether to count this day
            boolean countDay = true;

            if (isWeekend && (settings.getCountWeekendsAsLeave() == null || !settings.getCountWeekendsAsLeave())) {
                countDay = false;
            }

            if (isHoliday && (settings.getCountHolidaysAsLeave() == null || !settings.getCountHolidaysAsLeave())) {
                countDay = false;
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

        Set<LocalDate> holidayDates = getHolidayDatesInRange(leave.getTenant().getId(), leave.getStartDate(), leave.getEndDate(), settings);

        List<AttendanceRecord> existingRecords = attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDateBetween(
                leave.getTenant().getId(), leave.getEmployee().getId(), leave.getStartDate(), leave.getEndDate());

        Set<LocalDate> existingDates = existingRecords.stream()
                .map(AttendanceRecord::getAttendanceDate)
                .collect(Collectors.toSet());

        while (!date.isAfter(leave.getEndDate())) {
            boolean isWeekend = leaveConfigService.isWeekendForEmployee(date, leave.getEmployee(), settings);
            boolean isHoliday = holidayDates.contains(date);

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

            if (!existingDates.contains(date)) {
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
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        TenantLeaveSettings settings = leaveConfigService.getTenantSettings(tenantId);

        int year = LocalDate.now().getYear();
        Map<String, Object> balance = new LinkedHashMap<>();

        Map<String, Object> leavePolicies = settings.getLeavePolicies();
        if (leavePolicies == null) {
            leavePolicies = TenantLeaveSettings.createDefaultPolicies();
        }

        double totalUsed = 0.0;
        double totalAvailable = 0.0;

        for (LeaveType leaveType : LeaveType.values()) {
            Object policyObj = leavePolicies.get(leaveType.name());
            boolean allowed = false;

            if (policyObj instanceof Map) {
                Map<?, ?> policy = (Map<?, ?>) policyObj;
                allowed = Boolean.TRUE.equals(policy.get("allowed"));

                // Gender eligibility filter for the balance screen
                Object genderEligibilityObj = policy.get("genderEligibility");
                if (genderEligibilityObj != null) {
                    String eligibility = genderEligibilityObj.toString().trim().toUpperCase();
                    if (!"ALL".equals(eligibility)) {
                        com.sonixhr.enums.Gender empGender = employee.getGender();
                        if (empGender == null || !empGender.name().equals(eligibility)) {
                            allowed = false; // Exclude if gender doesn't match
                        }
                    }
                }
            }

            if (!allowed) {
                continue;
            }

            double used = leaveRepository.getUsedLeaveDays(employeeId, leaveType, year);
            double availableDays = getAvailableDaysForLeaveType(employee, leaveType, year, settings);

            if (!leaveType.hasLimit()) {
                balance.put(leaveType.name(), Map.of(
                        "total", "Unlimited",
                        "used", used,
                        "remaining", "Unlimited",
                        "color", getLeaveColor(leaveType)
                ));
            } else {
                balance.put(leaveType.name(), Map.of(
                        "total", availableDays,
                        "used", used,
                        "remaining", Math.max(0, availableDays - used),
                        "color", getLeaveColor(leaveType)
                ));
                totalUsed += used;
                totalAvailable += availableDays;
            }
        }

        // Add summary
        balance.put("summary", Map.of(
                "totalUsed", totalUsed,
                "totalAvailable", totalAvailable,
                "remaining", Math.max(0.0, totalAvailable - totalUsed),
                "utilizationPercentage", totalAvailable > 0 ? (totalUsed / totalAvailable) * 100 : 0,
                "policiesConfigured", settings.getPoliciesConfigured() != null && settings.getPoliciesConfigured()
        ));

        return balance;
    }

    private String getLeaveColor(LeaveType type) {
        switch (type) {
            case CASUAL: return "#4caf50";
            case SICK: return "#2196f3";
            case EARNED: return "#ff9800";
            case EMERGENCY: return "#f44336";
            case MATERNITY: return "#e91e63";
            case PATERNITY: return "#00bcd4";
            case COMPENSATORY: return "#9c27b0";
            case UNPAID:
            default: return "#9e9e9e";
        }
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

        TenantLeaveSettings settings = leaveConfigService.getTenantSettings(employee.getTenant().getId());

        // First-time configuration check
        if (settings.getPoliciesConfigured() == null || !settings.getPoliciesConfigured()) {
            throw new LeavePoliciesNotConfiguredException("Leave policies have not been configured for your company yet. Please ask your administrator to configure leave settings first.");
        }

        // Policy validation
        Map<String, Object> leavePolicies = settings.getLeavePolicies();
        if (leavePolicies == null) {
            leavePolicies = TenantLeaveSettings.createDefaultPolicies();
        }

        Object policyObj = leavePolicies.get(request.getLeaveType().name());
        if (policyObj instanceof Map) {
            Map<?, ?> policy = (Map<?, ?>) policyObj;

            // 1. Allowed Check
            if (!Boolean.TRUE.equals(policy.get("allowed"))) {
                throw new BusinessException(request.getLeaveType().getDisplayName() + " is not enabled for this tenant");
            }

            // 2. Gender Eligibility Check
            Object genderEligibilityObj = policy.get("genderEligibility");
            if (genderEligibilityObj != null) {
                String eligibility = genderEligibilityObj.toString().trim().toUpperCase();
                if (!"ALL".equals(eligibility)) {
                    com.sonixhr.enums.Gender empGender = employee.getGender();
                    if (empGender == null || !empGender.name().equals(eligibility)) {
                        throw new BusinessException(String.format("Employee is not eligible for %s based on gender", request.getLeaveType().getDisplayName()));
                    }
                }
            }

            // 3. Minimum Service Period Check
            Object minServiceObj = policy.get("minimumServiceMonths");
            if (minServiceObj instanceof Number) {
                int minServiceMonths = ((Number) minServiceObj).intValue();
                long serviceMonths = employee.getHireDate() != null 
                        ? ChronoUnit.MONTHS.between(employee.getHireDate(), LocalDate.now()) 
                        : 0;
                if (serviceMonths < minServiceMonths) {
                    throw new BusinessException(String.format("Employee does not meet the minimum service requirement of %d months for %s. Current service: %d months", 
                            minServiceMonths, request.getLeaveType().getDisplayName(), serviceMonths));
                }
            }
        } else {
            throw new BusinessException(request.getLeaveType().getDisplayName() + " is not configured for this tenant");
        }

        // Validate dates
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("Start date cannot be after end date");
        }

        if (request.getStartDate().isBefore(LocalDate.now())) {
            // Allow retroactive leave application for SICK or EMERGENCY types, or if the requester is manager/admin.
            // For other types, restrict to a maximum 30 days buffer.
            boolean isRetroactiveAllowed = request.getLeaveType() == LeaveType.SICK 
                    || request.getLeaveType() == LeaveType.EMERGENCY
                    || currentUser.isManager()
                    || currentUser.isSuperAdmin();
            
            if (!isRetroactiveAllowed) {
                if (request.getStartDate().isBefore(LocalDate.now().minusDays(30))) {
                    throw new BusinessException("Cannot request leave older than 30 days in the past");
                }
            }
        }

        // Calculate total leave days based on tenant settings
        double totalDays = calculateTotalLeaveDays(employee,
                request.getStartDate(), request.getEndDate(), request.getLeaveType(), settings);

        if (totalDays <= 0) {
            throw new BusinessException("No working days selected for leave. Selected dates fall on weekends/holidays.");
        }

        // Check maximum consecutive leave days
        if (settings.getMaxConsecutiveLeaveDays() != null && totalDays > settings.getMaxConsecutiveLeaveDays()) {
            throw new BusinessException("Cannot request more than " + settings.getMaxConsecutiveLeaveDays() +
                    " consecutive leave days");
        }

        // Check leave balance (skip for unpaid/unlimited leaves)
        if (request.getLeaveType().hasLimit()) {
            checkLeaveBalanceWithSettings(employee, employee.getTenant().getId(), request.getLeaveType(),
                    request.getStartDate(), request.getEndDate(), settings);
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
     * Check leave balance with tenant settings (resilient to year-crossing requests)
     */
    private void checkLeaveBalanceWithSettings(Employee employee, Long tenantId, LeaveType leaveType, 
                                               LocalDate startDate, LocalDate endDate, TenantLeaveSettings settings) {
        int startYear = startDate.getYear();
        int endYear = endDate.getYear();

        for (int year = startYear; year <= endYear; year++) {
            double requestedDaysInYear = calculateLeaveDaysInYear(employee, tenantId, startDate, endDate, year, leaveType, settings);
            if (requestedDaysInYear > 0) {
                double usedDays = leaveRepository.getUsedLeaveDays(employee.getId(), leaveType, year);
                double availableDays = getAvailableDaysForLeaveType(employee, leaveType, year, settings);
                double remainingDays = availableDays - usedDays;

                if (requestedDaysInYear > remainingDays) {
                    throw new BusinessException(String.format(
                            "Insufficient %s balance for year %d. Available: %.1f days, Requested: %.1f days",
                            leaveType.getDisplayName(), year, remainingDays, requestedDaysInYear));
                }
            }
        }
    }

    private double calculateLeaveDaysInYear(Employee employee, Long tenantId, LocalDate startDate, LocalDate endDate,
                                            int year, LeaveType leaveType, TenantLeaveSettings settings) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        
        LocalDate start = startDate.isBefore(yearStart) ? yearStart : startDate;
        LocalDate end = endDate.isAfter(yearEnd) ? yearEnd : endDate;
        
        if (start.isAfter(end)) {
            return 0;
        }
        
        return calculateTotalLeaveDays(employee, start, end, leaveType, settings);
    }

    /**
     * Get available days for leave type based on tenant settings
     */
    private double getAvailableDaysForLeaveType(Employee employee, LeaveType leaveType, int year, TenantLeaveSettings settings) {
        double baseDays = 0;
        boolean carryForward = false;
        double maxCarryForwardDays = 0;
        boolean foundPolicy = false;

        if (settings != null && settings.getLeavePolicies() != null && settings.getLeavePolicies().containsKey(leaveType.name())) {
            Object policyObj = settings.getLeavePolicies().get(leaveType.name());
            if (policyObj instanceof Map) {
                Map<?, ?> policy = (Map<?, ?>) policyObj;
                Object days = policy.get("daysPerYear");
                if (days instanceof Number) {
                    baseDays = ((Number) days).doubleValue();
                }
                carryForward = Boolean.TRUE.equals(policy.get("carryForward"));
                Object maxCf = policy.get("maxCarryForwardDays");
                if (maxCf instanceof Number) {
                    maxCarryForwardDays = ((Number) maxCf).doubleValue();
                }
                foundPolicy = true;
            }
        }

        if (!foundPolicy) {
            switch (leaveType) {
                case CASUAL: baseDays = settings.getCasualLeavePerYear(); break;
                case SICK: baseDays = settings.getSickLeavePerYear(); break;
                case EARNED: baseDays = settings.getEarnedLeavePerYear(); break;
                case EMERGENCY: baseDays = settings.getEmergencyLeavePerYear() != null ? settings.getEmergencyLeavePerYear() : leaveType.getDefaultDaysPerYear(); break;
                case MATERNITY: baseDays = settings.getMaternityLeavePerYear() != null ? settings.getMaternityLeavePerYear() : leaveType.getDefaultDaysPerYear(); break;
                case PATERNITY: baseDays = settings.getPaternityLeavePerYear() != null ? settings.getPaternityLeavePerYear() : leaveType.getDefaultDaysPerYear(); break;
                case UNPAID: baseDays = settings.getUnpaidLeavePerYear() != null ? settings.getUnpaidLeavePerYear() : leaveType.getDefaultDaysPerYear(); break;
                case COMPENSATORY: baseDays = settings.getCompensatoryLeavePerYear() != null ? settings.getCompensatoryLeavePerYear() : leaveType.getDefaultDaysPerYear(); break;
                default:
                    if (leaveType.hasLimit()) {
                        baseDays = leaveType.getDefaultDaysPerYear();
                    } else {
                        return Double.MAX_VALUE;
                    }
            }
        }

        // Apply carry forward logic if enabled
        if (carryForward && employee != null && employee.getHireDate() != null && employee.getHireDate().getYear() < year) {
            int prevYear = year - 1;
            double prevUsed = leaveRepository.getUsedLeaveDays(employee.getId(), leaveType, prevYear);
            double prevAvailable = getAvailableDaysForLeaveType(employee, leaveType, prevYear, settings);
            double prevRemaining = Math.max(0.0, prevAvailable - prevUsed);
            double carriedOver = Math.min(prevRemaining, maxCarryForwardDays);
            return baseDays + carriedOver;
        }

        return baseDays;
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

        Employee approver = employeeRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("Approver employee not found"));

        if (!approver.getTenantId().equals(leave.getTenant().getId())) {
            throw new BusinessException("Access denied: Approver belongs to a different tenant");
        }

        Employee requester = leave.getEmployee();
        boolean isDirectManager = requester.getManager() != null && requester.getManager().getId().equals(approverId);
        boolean hasApproveAny = approver.hasPermission("LEAVE_APPROVE_ANY");
        boolean hasApproveDept = approver.hasPermission("LEAVE_APPROVE_DEPARTMENT") &&
                requester.getDepartment() != null &&
                approver.getDepartment() != null &&
                requester.getDepartment().getId().equals(approver.getDepartment().getId());

        if (!isDirectManager && !hasApproveAny && !hasApproveDept) {
            throw new BusinessException("You are not authorized to approve this leave request");
        }

        leave.setStatus(LeaveStatus.APPROVED);
        leave.setApprovedBy(approverId);
        leave.setApprovedByName(approverName);
        leave.setApprovedAt(LocalDateTime.now());

        // Create attendance records for leave days
        createAttendanceForLeaveDays(leave);

        LeaveRequest saved = leaveRepository.save(leave);
        log.info("Leave request {} approved", leaveId);

        try {
            emailService.sendLeaveStatusNotification(
                    leave.getEmployee().getEmail(),
                    leave.getEmployee().getFullName(),
                    leave.getLeaveType().getDisplayName(),
                    leave.getStartDate().toString(),
                    leave.getEndDate().toString(),
                    "APPROVED",
                    approverName
            );

            notificationService.sendNotification(
                    leave.getEmployee(),
                    "Leave Request Approved",
                    String.format("Your leave request for %s (%s to %s) has been approved by %s.",
                            leave.getLeaveType().getDisplayName(), leave.getStartDate(), leave.getEndDate(), approverName),
                    "LEAVE_STATUS"
            );
        } catch (Exception e) {
            log.error("Failed to send leave approval notifications", e);
        }

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

        Employee rejector = employeeRepository.findById(rejectorId)
                .orElseThrow(() -> new ResourceNotFoundException("Rejector employee not found"));

        if (!rejector.getTenantId().equals(leave.getTenant().getId())) {
            throw new BusinessException("Access denied: Rejector belongs to a different tenant");
        }

        Employee requester = leave.getEmployee();
        boolean isDirectManager = requester.getManager() != null && requester.getManager().getId().equals(rejectorId);
        boolean hasApproveAny = rejector.hasPermission("LEAVE_APPROVE_ANY");
        boolean hasApproveDept = rejector.hasPermission("LEAVE_APPROVE_DEPARTMENT") &&
                requester.getDepartment() != null &&
                rejector.getDepartment() != null &&
                requester.getDepartment().getId().equals(rejector.getDepartment().getId());

        if (!isDirectManager && !hasApproveAny && !hasApproveDept) {
            throw new BusinessException("You are not authorized to reject this leave request");
        }

        leave.setStatus(LeaveStatus.REJECTED);
        leave.setRejectionReason(rejectionReason);
        leave.setApprovedBy(rejectorId);
        leave.setApprovedByName(rejectorName);
        leave.setApprovedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRepository.save(leave);
        log.info("Leave request {} rejected", leaveId);

        try {
            emailService.sendLeaveStatusNotification(
                    leave.getEmployee().getEmail(),
                    leave.getEmployee().getFullName(),
                    leave.getLeaveType().getDisplayName(),
                    leave.getStartDate().toString(),
                    leave.getEndDate().toString(),
                    "REJECTED",
                    rejectorName
            );

            notificationService.sendNotification(
                    leave.getEmployee(),
                    "Leave Request Rejected",
                    String.format("Your leave request for %s (%s to %s) has been rejected by %s. Reason: %s",
                            leave.getLeaveType().getDisplayName(), leave.getStartDate(), leave.getEndDate(), rejectorName, rejectionReason),
                    "LEAVE_STATUS"
            );
        } catch (Exception e) {
            log.error("Failed to send leave rejection notifications", e);
        }

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