package com.sonixhr.service.attendance;

import com.sonixhr.dto.attendance.ManualTeamMemberAttendanceDTO;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.attendance.AttendanceStatus;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class ManualAttendanceService {

    private final ManualAttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    // =====================================================
    // CONFIGURATION
    // =====================================================
    private static final int MAX_PAST_DAYS = 90;
    private static final double MAX_OVERTIME_PER_DAY = 12.0;

    // =====================================================
    // DATE VALIDATION (CRITICAL)
    // =====================================================

    /**
     * Validate attendance date against server date and employee hire date
     * Rules:
     * 1. Cannot mark attendance for future dates
     * 2. Cannot mark attendance before employee's hire date
     * 3. Cannot mark attendance older than 90 days
     */
    private void validateAttendanceDate(LocalDate attendanceDate, Employee employee) {
        LocalDate today = LocalDate.now(Clock.systemUTC());

        // RULE 1: Cannot mark future dates
        if (attendanceDate.isAfter(today)) {
            throw new BusinessException(
                    String.format("Cannot mark attendance for future dates. Today is %s", today)
            );
        }

        // RULE 2: Cannot mark before hire date
        if (employee.getHireDate() != null && attendanceDate.isBefore(employee.getHireDate())) {
            throw new BusinessException(
                    String.format("Cannot mark attendance before hire date: %s. Hire date is %s",
                            attendanceDate, employee.getHireDate())
            );
        }

        // RULE 3: Cannot mark older than 90 days
        LocalDate maxPastDate = today.minusDays(MAX_PAST_DAYS);
        if (attendanceDate.isBefore(maxPastDate)) {
            throw new BusinessException(
                    String.format("Cannot mark attendance older than %d days. Date %s is too old",
                            MAX_PAST_DAYS, attendanceDate)
            );
        }

        // RULE 4: Cannot mark after resignation/last working day
        if (employee.getLastWorkingDate() != null && attendanceDate.isAfter(employee.getLastWorkingDate())) {
            throw new BusinessException(
                    String.format("Cannot mark attendance after last working date: %s", employee.getLastWorkingDate())
            );
        }

        // RULE 5: Cannot mark attendance for resigned (if last working date has passed), terminated, suspended, or invited employees
        boolean isResignedAndLeft = employee.getStatus() == EmployeeStatus.RESIGNED &&
                (employee.getLastWorkingDate() == null || attendanceDate.isAfter(employee.getLastWorkingDate()));

        if (isResignedAndLeft ||
                employee.getStatus() == EmployeeStatus.TERMINATED ||
                employee.getStatus() == EmployeeStatus.SUSPENDED ||
                employee.getStatus() == EmployeeStatus.INVITED) {
            throw new BusinessException(
                    String.format("Cannot mark attendance for an employee with status: %s", employee.getStatus().getDisplayName())
            );
        }
    }

    /**
     * Validate date range for reports
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("Start date cannot be after end date");
        }

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > 365) {
            throw new BusinessException("Date range cannot exceed 365 days");
        }
    }

    /**
     * Validate overtime hours
     */
    private void validateOvertime(Double overtimeHours) {
        if (overtimeHours == null) return;

        if (overtimeHours > MAX_OVERTIME_PER_DAY) {
            throw new BusinessException("Overtime cannot exceed " + MAX_OVERTIME_PER_DAY + " hours per day");
        }
        if (overtimeHours < 0) {
            throw new BusinessException("Overtime cannot be negative");
        }
    }

    // =====================================================
    // AUTHORIZATION CHECKS
    // =====================================================

    private void validateAuthorization(Employee currentUser, Long targetEmployeeId) {
        // Super Admin can mark for anyone
        if (currentUser.isSuperAdmin()) {
            log.info("Super Admin {} marking attendance", currentUser.getEmail());
            return;
        }

        // Check if trying to mark own attendance
        if (currentUser.getId().equals(targetEmployeeId)) {
            throw new BusinessException("Employees cannot mark their own attendance. Only manager or admin can mark.");
        }

        // Check if current user is the manager of target employee
        Employee targetEmployee = employeeRepository.findById(targetEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (targetEmployee.getManager() == null ||
                !targetEmployee.getManager().getId().equals(currentUser.getId())) {
            throw new BusinessException("You can only mark attendance for employees who report to you");
        }

        log.info("Manager {} marking attendance for team member {}",
                currentUser.getEmail(), targetEmployee.getEmail());
    }

    // =====================================================
    // MARK ATTENDANCE (Manager or Admin only)
    // =====================================================

    /**
     * Mark attendance for today
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "attendance", allEntries = true),
        @CacheEvict(value = "calendar", allEntries = true)
    })
    public AttendanceRecord markAttendance(Long targetEmployeeId, AttendanceStatus status,
                                           String reason, Double overtimeHours,
                                           Employee currentUser) {

        log.info("User {} marking attendance for employee: {} as: {}",
                currentUser.getEmail(), targetEmployeeId, status);

        validateAuthorization(currentUser, targetEmployeeId);
        validateOvertime(overtimeHours);

        LocalDate today = LocalDate.now(Clock.systemUTC());

        return markAttendanceForDate(targetEmployeeId, today, status, reason, overtimeHours, currentUser);
    }

    /**
     * Mark attendance for specific date (with strict date validation)
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "attendance", allEntries = true),
        @CacheEvict(value = "calendar", allEntries = true)
    })
    public AttendanceRecord markAttendanceForDate(Long targetEmployeeId, LocalDate date,
                                                  AttendanceStatus status, String reason,
                                                  Double overtimeHours, Employee currentUser) {

        log.info("User {} marking attendance for employee: {} on date: {} as: {}",
                currentUser.getEmail(), targetEmployeeId, date, status);

        validateAuthorization(currentUser, targetEmployeeId);

        // Get target employee first for validation
        Employee targetEmployee = employeeRepository.findById(targetEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        // CRITICAL: Validate date against hire date and future dates
        validateAttendanceDate(date, targetEmployee);
        validateOvertime(overtimeHours);

        if (targetEmployee.getStatus() == EmployeeStatus.ON_LEAVE && status != AttendanceStatus.ON_LEAVE) {
            throw new BusinessException(
                    String.format("Cannot mark employee as %s because their employee status is ON LEAVE", status.getDisplayName())
            );
        }

        Long tenantId = targetEmployee.getTenant().getId();

        // Get or create attendance record
        Optional<AttendanceRecord> existing = attendanceRepository
                .findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, targetEmployeeId, date);

        AttendanceRecord record;
        if (existing.isPresent()) {
            record = existing.get();

            // Prevent modifying old records if needed
            LocalDate today = LocalDate.now(Clock.systemUTC());
            if (date.isBefore(today.minusDays(7))) {
                log.warn("Modifying attendance record older than 7 days: {}", date);
            }

            record.setStatus(status);
            if (reason != null) record.setReason(reason);
            if (overtimeHours != null) record.setOvertimeHours(overtimeHours);
            record.setMarkedBy(currentUser.getId());
            record.setMarkedByName(currentUser.getFullName());
            record.setMarkedByRole(currentUser.isSuperAdmin() ? "SUPER_ADMIN" : "MANAGER");
            record.setUpdatedAt(LocalDateTime.now());

            log.info("Updated attendance for employee: {} on date: {} to status: {}",
                    targetEmployeeId, date, status);
        } else {
            record = AttendanceRecord.builder()
                    .tenant(targetEmployee.getTenant())
                    .employee(targetEmployee)
                    .attendanceDate(date)
                    .status(status)
                    .reason(reason)
                    .overtimeHours(overtimeHours)
                    .markedBy(currentUser.getId())
                    .markedByName(currentUser.getFullName())
                    .markedByRole(currentUser.isSuperAdmin() ? "SUPER_ADMIN" : "MANAGER")
                    .markedAt(LocalDateTime.now())
                    .build();

            log.info("Created new attendance record for employee: {} on date: {} with status: {}",
                    targetEmployeeId, date, status);
        }

        return attendanceRepository.save(record);
    }

    // =====================================================
    // BULK ATTENDANCE MARKING
    // =====================================================

    /**
     * Mark attendance for multiple team members at once
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "attendance", allEntries = true),
        @CacheEvict(value = "calendar", allEntries = true)
    })
    public List<AttendanceRecord> markTeamAttendance(Long managerId, LocalDate date,
                                                     Map<Long, AttendanceStatus> attendanceMap,
                                                     Map<Long, String> reasonMap,
                                                     Map<Long, Double> overtimeMap) {

        log.info("Manager {} marking attendance for {} team members on date: {}",
                managerId, attendanceMap.size(), date);

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));

        List<AttendanceRecord> records = new ArrayList<>();

        for (Map.Entry<Long, AttendanceStatus> entry : attendanceMap.entrySet()) {
            Long employeeId = entry.getKey();
            AttendanceStatus status = entry.getValue();
            String reason = reasonMap != null ? reasonMap.get(employeeId) : null;
            Double overtime = overtimeMap != null ? overtimeMap.get(employeeId) : null;

            // Verify employee is in manager's team
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

            if (employee.getManager() == null || !employee.getManager().getId().equals(managerId)) {
                log.warn("Employee {} is not in manager {} team, skipping", employeeId, managerId);
                continue;
            }

            try {
                AttendanceRecord record = markAttendanceForDate(employeeId, date, status, reason, overtime, manager);
                records.add(record);
            } catch (BusinessException e) {
                log.warn("Could not mark attendance for employee {}: {}", employeeId, e.getMessage());
            }
        }

        log.info("Successfully marked attendance for {} team members", records.size());
        return records;
    }

    /**
     * Quick mark all team members with same status (for today only)
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "attendance", allEntries = true),
        @CacheEvict(value = "calendar", allEntries = true)
    })
    public List<AttendanceRecord> quickMarkTeamAttendance(Long managerId, AttendanceStatus defaultStatus) {
        log.info("Manager {} quick marking all team members as: {}", managerId, defaultStatus);

        LocalDate today = LocalDate.now(Clock.systemUTC());
        List<Employee> teamMembers = getTeamMembers(managerId);

        Map<Long, AttendanceStatus> attendanceMap = new HashMap<>();
        for (Employee employee : teamMembers) {
            attendanceMap.put(employee.getId(), defaultStatus);
        }

        return markTeamAttendance(managerId, today, attendanceMap, null, null);
    }

    // =====================================================
    // OVERTIME MANAGEMENT
    // =====================================================

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "attendance", allEntries = true),
        @CacheEvict(value = "calendar", allEntries = true)
    })
    public AttendanceRecord addOvertime(Long employeeId, LocalDate date, Double overtimeHours,
                                        String reason, Employee currentUser) {

        log.info("User {} adding {} overtime hours for employee: {} on date: {}",
                currentUser.getEmail(), overtimeHours, employeeId, date);

        validateAuthorization(currentUser, employeeId);

        Employee targetEmployee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        validateAttendanceDate(date, targetEmployee);
        validateOvertime(overtimeHours);

        if (targetEmployee.getStatus() == EmployeeStatus.ON_LEAVE) {
            throw new BusinessException("Cannot add overtime for an employee who is currently ON LEAVE");
        }

        Long tenantId = targetEmployee.getTenant().getId();

        Optional<AttendanceRecord> existing = attendanceRepository
                .findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, employeeId, date);

        AttendanceRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            Double currentOvertime = record.getOvertimeHours() != null ? record.getOvertimeHours() : 0;
            record.setOvertimeHours(currentOvertime + overtimeHours);

            String overtimeReason = record.getReason() != null ?
                    record.getReason() + " | Overtime: " + reason :
                    "Overtime: " + reason;
            record.setReason(overtimeReason);
            record.setUpdatedAt(LocalDateTime.now());
        } else {
            record = AttendanceRecord.builder()
                    .tenant(targetEmployee.getTenant())
                    .employee(targetEmployee)
                    .attendanceDate(date)
                    .status(AttendanceStatus.PRESENT)
                    .overtimeHours(overtimeHours)
                    .reason("Overtime: " + reason)
                    .markedBy(currentUser.getId())
                    .markedByName(currentUser.getFullName())
                    .markedByRole(currentUser.isSuperAdmin() ? "SUPER_ADMIN" : "MANAGER")
                    .markedAt(LocalDateTime.now())
                    .build();
        }

        record.setMarkedBy(currentUser.getId());
        record.setMarkedByName(currentUser.getFullName());
        record.setMarkedByRole(currentUser.isSuperAdmin() ? "SUPER_ADMIN" : "MANAGER");

        log.info("Overtime added successfully. Total: {} hours", record.getOvertimeHours());
        return attendanceRepository.save(record);
    }

    // =====================================================
    // TEAM MANAGEMENT
    // =====================================================

    public List<Employee> getTeamMembers(Long managerId) {
        log.info("Getting team members for manager: {}", managerId);

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));

        List<Employee> teamMembers = employeeRepository.findByManagerIdAndTenantId(managerId, manager.getTenant().getId());

        log.info("Found {} team members for manager: {}", teamMembers.size(), manager.getEmail());
        return teamMembers;
    }

    public Page<Employee> getTeamMembersPaginated(Long managerId, Pageable pageable) {
        log.info("Getting paginated team members for manager: {}", managerId);
        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        return employeeRepository.findByManagerIdAndTenantId(managerId, manager.getTenant().getId(), pageable);
    }

    public List<ManualTeamMemberAttendanceDTO> getTeamWithTodayAttendance(Long managerId) {
        log.info("Getting team with today's attendance for manager: {}", managerId);

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        Long tenantId = manager.getTenant().getId();

        List<Employee> teamMembers = employeeRepository.findByManagerIdAndTenantId(managerId, tenantId);
        log.info("Found {} team members for manager: {}", teamMembers.size(), manager.getEmail());

        return buildTeamAttendanceDTOs(teamMembers, tenantId, LocalDate.now(Clock.systemUTC()));
    }

    public List<Employee> searchTeamMembers(Long managerId, String searchTerm) {
        log.info("Searching team members for manager: {} with term: {}", managerId, searchTerm);

        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getTeamMembers(managerId);
        }

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        Long tenantId = manager.getTenant().getId();

        return employeeRepository.searchTeamMembers(managerId, tenantId, searchTerm.toLowerCase());
    }

    public List<ManualTeamMemberAttendanceDTO> searchTeamWithTodayAttendance(Long managerId, String searchTerm) {
        log.info("Searching team with today's attendance for manager: {} with term: {}", managerId, searchTerm);

        List<Employee> teamMembers = searchTeamMembers(managerId, searchTerm);
        if (teamMembers.isEmpty()) {
            return Collections.emptyList();
        }

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        Long tenantId = manager.getTenant().getId();
        return buildTeamAttendanceDTOs(teamMembers, tenantId, LocalDate.now(Clock.systemUTC()));
    }


    // =====================================================
    // VIEW ATTENDANCE
    // =====================================================

    public Page<AttendanceRecord> getTeamAttendance(Employee currentUser, LocalDate startDate,
                                                    LocalDate endDate, Pageable pageable) {

        log.info("Getting team attendance for user: {}", currentUser.getEmail());
        validateDateRange(startDate, endDate);

        Long tenantId = currentUser.getTenantId();
        List<Long> teamIds;

        if (currentUser.isSuperAdmin()) {
            List<Employee> allEmployees = employeeRepository.findByTenant_Id(tenantId);
            teamIds = allEmployees.stream()
                    .filter(e -> e.getHireDate() == null || !e.getHireDate().isAfter(endDate))
                    .map(Employee::getId)
                    .collect(Collectors.toList());
        } else {
            List<Employee> team = employeeRepository.findByManagerIdAndTenantId(currentUser.getId(), tenantId);
            teamIds = team.stream()
                    .filter(e -> e.getHireDate() == null || !e.getHireDate().isAfter(endDate))
                    .map(Employee::getId)
                    .collect(Collectors.toList());
        }

        if (teamIds.isEmpty()) {
            return Page.empty();
        }

        return attendanceRepository.findByTenantIdAndEmployeeIdInAndAttendanceDateBetween(
                tenantId, teamIds, startDate, endDate, pageable);
    }

    public Page<AttendanceRecord> getEmployeeAttendance(Long employeeId, LocalDate startDate,
                                                        LocalDate endDate, Employee currentUser,
                                                        Pageable pageable) {

        log.info("Getting attendance for employee: {} from {} to {}", employeeId, startDate, endDate);
        validateDateRange(startDate, endDate);

        Long tenantId = currentUser.getTenantId();

        Employee targetEmployee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (targetEmployee.getHireDate() != null && startDate.isBefore(targetEmployee.getHireDate())) {
            startDate = targetEmployee.getHireDate();
            log.info("Adjusted start date to hire date: {}", startDate);
        }

        if (!currentUser.isSuperAdmin() && !currentUser.getId().equals(employeeId)) {
            if (targetEmployee.getManager() == null ||
                    !targetEmployee.getManager().getId().equals(currentUser.getId())) {
                throw new BusinessException("You can only view attendance for your team members");
            }
        }

        return attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDateBetween(
                tenantId, employeeId, startDate, endDate, pageable);
    }

    @Cacheable(value = "attendance", key = "'today:' + #tenantId + ':' + #employeeId", unless = "#result == null")
    public AttendanceRecord getTodayAttendance(Long employeeId, Long tenantId) {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        return attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, employeeId, today)
                .orElse(null);
    }

    // =====================================================
    // SUMMARY & REPORTS
    // =====================================================

    @Cacheable(value = "attendance", key = "'summary:' + #tenantId + ':' + #employeeId + ':' + #year + ':' + #month", unless = "#result == null")
    public Map<String, Object> getMonthlySummary(Long employeeId, int year, int month, Long tenantId) {

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (employee.getHireDate() != null && startDate.isBefore(employee.getHireDate())) {
            startDate = employee.getHireDate();
        }

        LocalDate today = LocalDate.now(Clock.systemUTC());
        if (startDate.isAfter(today)) {
            Map<String, Object> emptySummary = new HashMap<>();
            emptySummary.put("message", "Cannot view attendance for future months");
            emptySummary.put("employeeId", employeeId);
            emptySummary.put("year", year);
            emptySummary.put("month", month);
            return emptySummary;
        }

        if (endDate.isAfter(today)) {
            endDate = today;
        }

        List<AttendanceRecord> records = attendanceRepository
                .findByTenantIdAndEmployeeIdAndAttendanceDateBetween(tenantId, employeeId, startDate, endDate);

        Map<AttendanceStatus, Long> statusCount = records.stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getStatus, Collectors.counting()));

        double totalOvertime = records.stream()
                .filter(r -> r.getOvertimeHours() != null)
                .mapToDouble(AttendanceRecord::getOvertimeHours)
                .sum();

        long totalWorkingDays = endDate.getDayOfMonth();
        long totalPresent = statusCount.getOrDefault(AttendanceStatus.PRESENT, 0L) +
                statusCount.getOrDefault(AttendanceStatus.LATE, 0L);

        Map<String, Object> summary = new HashMap<>();
        summary.put("employeeId", employeeId);
        summary.put("employeeName", employee.getFullName());
        summary.put("employeeCode", employee.getEmployeeCode());
        summary.put("hireDate", employee.getHireDate());
        summary.put("year", year);
        summary.put("month", month);
        summary.put("periodStart", startDate);
        summary.put("periodEnd", endDate);
        summary.put("totalDaysInMonth", totalWorkingDays);
        summary.put("present", statusCount.getOrDefault(AttendanceStatus.PRESENT, 0L));
        summary.put("absent", statusCount.getOrDefault(AttendanceStatus.ABSENT, 0L));
        summary.put("halfDay", statusCount.getOrDefault(AttendanceStatus.HALF_DAY, 0L));
        summary.put("late", statusCount.getOrDefault(AttendanceStatus.LATE, 0L));
        summary.put("onLeave", statusCount.getOrDefault(AttendanceStatus.ON_LEAVE, 0L));
        summary.put("totalOvertimeHours", totalOvertime);
        summary.put("attendanceRate", totalWorkingDays > 0 ? (totalPresent * 100.0 / totalWorkingDays) : 0);

        return summary;
    }
// =====================================================
// TEAM ATTENDANCE SUMMARY
// =====================================================

    /**
     * Get team attendance summary for a manager
     */
    public Map<String, Object> getTeamAttendanceSummary(Long managerId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting team attendance summary for manager: {} from {} to {}", managerId, startDate, endDate);

        // Validate date range
        validateDateRange(startDate, endDate);

        // Get manager
        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        Long tenantId = manager.getTenant().getId();

        // Get manager's team members
        List<Employee> teamMembers = employeeRepository.findByManagerIdAndTenantId(managerId, tenantId);
        log.info("Found {} team members for manager: {}", teamMembers.size(), manager.getEmail());

        List<Long> teamIds = teamMembers.stream()
                .map(Employee::getId)
                .collect(Collectors.toList());

        if (teamIds.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("message", "No team members found");
            emptyResponse.put("totalTeamMembers", 0);
            emptyResponse.put("startDate", startDate);
            emptyResponse.put("endDate", endDate);
            return emptyResponse;
        }

        // Get all attendance records for team
        List<AttendanceRecord> allAttendance = attendanceRepository
                .findByTenantIdAndEmployeeIdInAndAttendanceDateBetween(tenantId, teamIds, startDate, endDate);

        // Group attendance records by employee ID to avoid O(N * M) scanning in loop
        Map<Long, List<AttendanceRecord>> recordsByEmployee = allAttendance.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        // Calculate employee summaries
        Map<String, Object> employeeSummaries = new LinkedHashMap<>();
        Map<String, Object> teamTotals = new HashMap<>();
        teamTotals.put("present", 0L);
        teamTotals.put("absent", 0L);
        teamTotals.put("halfDay", 0L);
        teamTotals.put("late", 0L);
        teamTotals.put("onLeave", 0L);
        teamTotals.put("totalOvertime", 0.0);

        for (Employee employee : teamMembers) {
            List<AttendanceRecord> employeeRecords = recordsByEmployee.getOrDefault(employee.getId(), Collections.emptyList());

            long present = employeeRecords.stream()
                    .filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
            long absent = employeeRecords.stream()
                    .filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
            long halfDay = employeeRecords.stream()
                    .filter(r -> r.getStatus() == AttendanceStatus.HALF_DAY).count();
            long late = employeeRecords.stream()
                    .filter(r -> r.getStatus() == AttendanceStatus.LATE).count();
            long onLeave = employeeRecords.stream()
                    .filter(r -> r.getStatus() == AttendanceStatus.ON_LEAVE).count();

            double overtime = employeeRecords.stream()
                    .filter(r -> r.getOvertimeHours() != null)
                    .mapToDouble(AttendanceRecord::getOvertimeHours)
                    .sum();

            Map<String, Object> summary = new HashMap<>();
            summary.put("employeeId", employee.getId());
            summary.put("employeeName", employee.getFullName());
            summary.put("employeeCode", employee.getEmployeeCode());
            summary.put("present", present);
            summary.put("absent", absent);
            summary.put("halfDay", halfDay);
            summary.put("late", late);
            summary.put("onLeave", onLeave);
            summary.put("overtimeHours", overtime);

            employeeSummaries.put(employee.getEmployeeCode(), summary);

            // Update team totals
            teamTotals.put("present", (long) teamTotals.get("present") + present);
            teamTotals.put("absent", (long) teamTotals.get("absent") + absent);
            teamTotals.put("halfDay", (long) teamTotals.get("halfDay") + halfDay);
            teamTotals.put("late", (long) teamTotals.get("late") + late);
            teamTotals.put("onLeave", (long) teamTotals.get("onLeave") + onLeave);
            teamTotals.put("totalOvertime", (double) teamTotals.get("totalOvertime") + overtime);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("totalTeamMembers", teamMembers.size());
        response.put("teamTotals", teamTotals);
        response.put("employeeSummaries", employeeSummaries);

        return response;
    }

    // =====================================================
// ATTENDANCE CALENDAR
// =====================================================

    /**
     * Get attendance calendar view for an employee
     * Returns a map of dates with attendance status for the entire month
     */
    @Cacheable(value = "attendance", key = "'calendar:' + #tenantId + ':' + #employeeId + ':' + #year + ':' + #month", unless = "#result == null || #result.isEmpty()")
    public Map<LocalDate, Map<String, Object>> getAttendanceCalendar(Long employeeId, int year, int month, Long tenantId) {
        log.info("Getting attendance calendar for employee: {} for {}-{}", employeeId, year, month);

        // Get employee to verify existence and hire date
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // Adjust start date if employee was hired after month start
        if (employee.getHireDate() != null && startDate.isBefore(employee.getHireDate())) {
            startDate = employee.getHireDate();
        }

        // Don't show future months
        LocalDate today = LocalDate.now(Clock.systemUTC());
        if (startDate.isAfter(today)) {
            return new LinkedHashMap<>();
        }

        if (endDate.isAfter(today)) {
            endDate = today;
        }

        // Get all attendance records for the employee in the date range
        List<AttendanceRecord> records = attendanceRepository
                .findByTenantIdAndEmployeeIdAndAttendanceDateBetween(tenantId, employeeId, startDate, endDate);

        // Create calendar map
        Map<LocalDate, Map<String, Object>> calendar = new LinkedHashMap<>();

        // Initialize all dates in the range
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, Object> dayInfo = new HashMap<>();
            dayInfo.put("date", date);
            dayInfo.put("dayOfWeek", date.getDayOfWeek().toString());
            dayInfo.put("dayOfWeekValue", date.getDayOfWeek().getValue());
            dayInfo.put("status", "NOT_MARKED");
            dayInfo.put("statusCode", "N/A");
            dayInfo.put("reason", null);
            dayInfo.put("overtimeHours", 0.0);
            dayInfo.put("markedBy", null);
            dayInfo.put("isWeekend", isWeekend(date));
            dayInfo.put("isHoliday", false); // You can implement holiday check if needed
            calendar.put(date, dayInfo);
        }

        // Fill in attendance data
        for (AttendanceRecord record : records) {
            Map<String, Object> dayInfo = calendar.get(record.getAttendanceDate());
            if (dayInfo != null) {
                dayInfo.put("status", record.getStatus().toString());
                dayInfo.put("statusCode", getStatusCode(record.getStatus()));
                dayInfo.put("reason", record.getReason());
                dayInfo.put("overtimeHours", record.getOvertimeHours() != null ? record.getOvertimeHours() : 0.0);
                dayInfo.put("markedBy", record.getMarkedByName());
            }
        }

        return calendar;
    }

    /**
     * Get status code for display (P, A, H, L, LV)
     */
    private String getStatusCode(AttendanceStatus status) {
        switch (status) {
            case PRESENT: return "P";
            case ABSENT: return "A";
            case HALF_DAY: return "H";
            case LATE: return "L";
            case ON_LEAVE: return "LV";
            default: return "N/A";
        }
    }



    @Cacheable(value = "attendance", key = "'stats:' + #currentUser.tenantId + ':' + #currentUser.id", unless = "#result == null")
    public Map<String, Object> getDashboardStats(Employee currentUser) {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        Long tenantId = currentUser.getTenantId();

        long totalEmployees;
        List<Object[]> summary;

        if (currentUser.isSuperAdmin()) {
            totalEmployees = employeeRepository.countByTenantIdAndHireDateBeforeOrNull(tenantId, today);
            summary = attendanceRepository.getTodayAttendanceSummary(tenantId, today);
        } else {
            List<Long> teamIds = employeeRepository.findEmployeeIdsByManagerIdAndTenantIdAndHireDateBeforeOrNull(
                    currentUser.getId(), tenantId, today);
            totalEmployees = teamIds.size();
            if (teamIds.isEmpty()) {
                summary = Collections.emptyList();
            } else {
                summary = attendanceRepository.getTodayAttendanceSummaryForEmployees(tenantId, teamIds, today);
            }
        }

        Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
        if (summary != null) {
            for (Object[] row : summary) {
                if (row != null && row.length >= 2 && row[0] instanceof AttendanceStatus) {
                    counts.put((AttendanceStatus) row[0], (Long) row[1]);
                }
            }
        }

        long presentToday = counts.getOrDefault(AttendanceStatus.PRESENT, 0L)
                + counts.getOrDefault(AttendanceStatus.LATE, 0L)
                + counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L);
        long absentToday = counts.getOrDefault(AttendanceStatus.ABSENT, 0L);
        long onLeaveToday = counts.getOrDefault(AttendanceStatus.ON_LEAVE, 0L);

        Map<String, Object> stats = new HashMap<>();
        stats.put("date", today);
        stats.put("totalEmployees", totalEmployees);
        stats.put("present", presentToday);
        stats.put("absent", absentToday);
        stats.put("onLeave", onLeaveToday);
        stats.put("pending", totalEmployees - (presentToday + absentToday + onLeaveToday));
        stats.put("attendancePercentage", totalEmployees > 0 ? (presentToday * 100 / totalEmployees) : 0);

        return stats;
    }
    public List<AttendanceRecord> getEmployeeAttendance(Long employeeId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting attendance for employee: {} from {} to {}", employeeId, startDate, endDate);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        Long tenantId = employee.getTenant().getId();

        // This method already has all your conditions (future dates, hire date, etc.)
        return attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDateBetween(
                tenantId, employeeId, startDate, endDate);
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private List<ManualTeamMemberAttendanceDTO> buildTeamAttendanceDTOs(
            List<Employee> teamMembers, Long tenantId, LocalDate today) {
        List<Long> employeeIds = teamMembers.stream()
                .filter(e -> e.getHireDate() == null || !e.getHireDate().isAfter(today))
                .map(Employee::getId)
                .collect(Collectors.toList());

        Map<Long, AttendanceRecord> attendanceMap = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            List<AttendanceRecord> records = attendanceRepository
                    .findByTenantIdAndEmployeeIdInAndAttendanceDateBetween(tenantId, employeeIds, today, today);
            for (AttendanceRecord record : records) {
                attendanceMap.put(record.getEmployee().getId(), record);
            }
        }

        List<ManualTeamMemberAttendanceDTO> teamAttendance = new ArrayList<>();
        for (Employee employee : teamMembers) {
            if (employee.getHireDate() != null && employee.getHireDate().isAfter(today)) {
                continue;
            }
            Optional<AttendanceRecord> attendance = Optional.ofNullable(attendanceMap.get(employee.getId()));
            teamAttendance.add(ManualTeamMemberAttendanceDTO.builder()
                    .employeeId(employee.getId())
                    .employeeCode(employee.getEmployeeCode())
                    .employeeName(employee.getFullName())
                    .email(employee.getEmail())
                    .position(employee.getPosition())
                    .profilePicture(employee.getProfilePictureUrl())
                    .hireDate(employee.getHireDate())
                    .todayStatus(attendance.map(AttendanceRecord::getStatus).orElse(null))
                    .todayOvertime(attendance.map(AttendanceRecord::getOvertimeHours).orElse(null))
                    .todayReason(attendance.map(AttendanceRecord::getReason).orElse(null))
                    .isMarked(attendance.isPresent())
                    .build());
        }
        return teamAttendance;
    }

    private boolean isWeekend(LocalDate date) {
        String day = date.getDayOfWeek().toString();
        return day.equals("SATURDAY") || day.equals("SUNDAY");
    }
}