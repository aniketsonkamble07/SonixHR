package com.sonixhr.controller.attendance;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.service.attendance.ManualAttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/manual-attendance")
@RequiredArgsConstructor
public class ManualAttendanceController {

    private final ManualAttendanceService attendanceService;

    // =====================================================
    // MANAGER MARKS TEAM MEMBER ATTENDANCE
    // =====================================================

    @PostMapping("/team/{employeeId}")
    @PreAuthorize("hasPermission('ATTENDANCE_MARK_TEAM')")
    public ResponseEntity<AttendanceRecord> markTeamAttendance(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkInTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkOutTime,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Manager {} marking attendance for employee {} on {}",
                currentEmployee.getEmail(), employeeId, attendanceDate);

        Long tenantId = currentEmployee.getTenantId();
        Long managerId = currentEmployee.getId();

        // Validate check-out time is after check-in time if both provided
        if (checkOutTime != null && checkOutTime.isBefore(checkInTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Check-out time must be after check-in time");
        }

        // Validate attendance date is not in the future
        if (attendanceDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Attendance cannot be marked for future dates");
        }

        try {
            AttendanceRecord response = attendanceService.markAttendanceByManager(
                    managerId, employeeId, attendanceDate, checkInTime, checkOutTime, reason, tenantId, managerId);

            log.info("Successfully marked attendance for employee {} on {}", employeeId, attendanceDate);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to mark attendance for employee {}: {}", employeeId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to mark attendance: " + e.getMessage());
        }
    }

    // =====================================================
    // SUPER ADMIN MARKS ANY EMPLOYEE ATTENDANCE
    // =====================================================

    @PostMapping("/admin/{employeeId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AttendanceRecord> markAttendanceByAdmin(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkInTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkOutTime,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Admin {} marking attendance for employee {} on {}",
                currentEmployee.getEmail(), employeeId, attendanceDate);

        Long tenantId = currentEmployee.getTenantId();
        Long adminUserId = currentEmployee.getId();
        String adminName = currentEmployee.getFullName();

        // Validate check-out time is after check-in time if both provided
        if (checkOutTime != null && checkOutTime.isBefore(checkInTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Check-out time must be after check-in time");
        }

        // Validate attendance date is not in the future
        if (attendanceDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Attendance cannot be marked for future dates");
        }

        try {
            AttendanceRecord response = attendanceService.markAttendanceByAdmin(
                    employeeId, attendanceDate, checkInTime, checkOutTime, reason, tenantId, adminUserId, adminName);

            log.info("Successfully marked attendance for employee {} by admin {}", employeeId, adminName);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to mark attendance for employee {}: {}", employeeId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to mark attendance: " + e.getMessage());
        }
    }

    // =====================================================
    // GET TEAM ATTENDANCE (MANAGER)
    // =====================================================

    @GetMapping("/team")
    @PreAuthorize("hasPermission('ATTENDANCE_VIEW_TEAM')")
    public ResponseEntity<Page<AttendanceRecord>> getTeamAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "attendanceDate") Pageable pageable,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Manager {} retrieving team attendance from {} to {}",
                currentEmployee.getEmail(), startDate, endDate);

        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        // Validate date range is not too large (max 90 days)
        if (startDate.until(endDate).getDays() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range cannot exceed 90 days");
        }

        Page<AttendanceRecord> response = attendanceService.getTeamAttendance(
                currentEmployee.getId(), startDate, endDate, pageable);

        log.info("Retrieved {} attendance records for manager's team", response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET MY ATTENDANCE (EMPLOYEE)
    // =====================================================

    @GetMapping("/my")
    @PreAuthorize("hasPermission('ATTENDANCE_VIEW_SELF')")
    public ResponseEntity<Page<AttendanceRecord>> getMyAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "attendanceDate") Pageable pageable,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Employee {} retrieving own attendance from {} to {}",
                currentEmployee.getEmail(), startDate, endDate);

        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        // Validate date range is not too large (max 90 days)
        if (startDate.until(endDate).getDays() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range cannot exceed 90 days");
        }

        Page<AttendanceRecord> response = attendanceService.getEmployeeAttendance(
                currentEmployee.getId(), startDate, endDate, pageable);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ALL ATTENDANCE (SUPER ADMIN)
    // =====================================================

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<AttendanceRecord>> getAllAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long departmentId,
            @PageableDefault(size = 20, sort = "attendanceDate") Pageable pageable,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Super admin {} retrieving all attendance from {} to {}",
                currentEmployee.getEmail(), startDate, endDate);

        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        // Validate date range is not too large (max 90 days)
        if (startDate.until(endDate).getDays() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range cannot exceed 90 days");
        }

        Long tenantId = currentEmployee.getTenantId();
        Page<AttendanceRecord> response = attendanceService.getAllAttendance(
                tenantId, startDate, endDate, departmentId, pageable);

        log.info("Retrieved {} attendance records", response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEE ATTENDANCE BY ID (MANAGER/ADMIN)
    // =====================================================

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasPermission('ATTENDANCE_VIEW_EMPLOYEE')")
    public ResponseEntity<Page<AttendanceRecord>> getEmployeeAttendanceById(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "attendanceDate") Pageable pageable,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("User {} retrieving attendance for employee {} from {} to {}",
                currentEmployee.getEmail(), employeeId, startDate, endDate);

        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        // Validate date range is not too large (max 90 days)
        if (startDate.until(endDate).getDays() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range cannot exceed 90 days");
        }

        // Security check: Ensure current employee has permission to view this employee's attendance
        boolean isAdmin = currentEmployee.hasPermission("ATTENDANCE_VIEW_ALL");
        boolean isViewingSelf = currentEmployee.getId().equals(employeeId);

        if (!isAdmin && !isViewingSelf) {
            log.warn("Access denied: Employee {} attempted to view attendance of employee {}",
                    currentEmployee.getId(), employeeId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: You can only view your own attendance");
        }

        Page<AttendanceRecord> response = attendanceService.getEmployeeAttendance(
                employeeId, startDate, endDate, pageable);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPDATE ATTENDANCE
    // =====================================================

    @PutMapping("/{attendanceId}")
    @PreAuthorize("hasPermission('ATTENDANCE_UPDATE')")
    public ResponseEntity<AttendanceRecord> updateAttendance(
            @PathVariable Long attendanceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkInTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkOutTime,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("User {} updating attendance record {}", currentEmployee.getEmail(), attendanceId);

        Long managerId = currentEmployee.getId();

        // Validate check-out time is after check-in time if both provided
        if (checkOutTime != null && checkOutTime.isBefore(checkInTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Check-out time must be after check-in time");
        }

        // Check if user is Super Admin or has admin permissions
        boolean isAdmin = currentEmployee.hasPermission("ATTENDANCE_ADMIN");

        try {
            AttendanceRecord response = attendanceService.updateAttendance(
                    attendanceId, managerId, checkInTime, checkOutTime, reason, isAdmin);

            log.info("Successfully updated attendance record {}", attendanceId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update attendance record {}: {}", attendanceId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update attendance: " + e.getMessage());
        }
    }

    // =====================================================
    // GET MONTHLY ATTENDANCE SUMMARY
    // =====================================================

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMonthlyAttendanceSummary(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Employee {} retrieving attendance summary for {}-{}",
                currentEmployee.getEmail(), year, month);

        // Validate year and month
        if (year < 2000 || year > LocalDate.now().getYear() + 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }

        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month");
        }

        try {
            Map<String, Object> summary = attendanceService.getAttendanceSummary(
                    currentEmployee.getId(), year, month);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to retrieve attendance summary: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve attendance summary");
        }
    }

    // =====================================================
    // DELETE ATTENDANCE RECORD (ADMIN ONLY)
    // =====================================================

    @DeleteMapping("/{attendanceId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteAttendanceRecord(
            @PathVariable Long attendanceId,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Admin {} deleting attendance record {}", currentEmployee.getEmail(), attendanceId);

        try {
            attendanceService.deleteAttendanceRecord(attendanceId, currentEmployee.getId());
            log.info("Successfully deleted attendance record {}", attendanceId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete attendance record {}: {}", attendanceId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete attendance record");
        }
    }

    // =====================================================
    // GET TODAY'S ATTENDANCE (EMPLOYEE)
    // =====================================================

    @GetMapping("/today")
    @PreAuthorize("hasPermission('ATTENDANCE_VIEW_SELF')")
    public ResponseEntity<AttendanceRecord> getTodayAttendance(
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Employee {} retrieving today's attendance", currentEmployee.getEmail());

        try {
            AttendanceRecord record = attendanceService.getTodayAttendance(currentEmployee.getId());

            if (record == null) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(record);
        } catch (Exception e) {
            log.error("Failed to retrieve today's attendance: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve today's attendance");
        }
    }

    // =====================================================
    // GET ATTENDANCE BY MONTH (EMPLOYEE)
    // =====================================================

    @GetMapping("/calendar")
    @PreAuthorize("hasPermission('ATTENDANCE_VIEW_SELF')")
    public ResponseEntity<Map<LocalDate, AttendanceRecord>> getAttendanceCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("Employee {} retrieving attendance calendar for {}-{}",
                currentEmployee.getEmail(), year, month);

        // Validate year and month
        if (year < 2000 || year > LocalDate.now().getYear() + 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }

        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month");
        }

        try {
            Map<LocalDate, AttendanceRecord> calendar = attendanceService.getAttendanceCalendar(
                    currentEmployee.getId(), year, month);

            return ResponseEntity.ok(calendar);
        } catch (Exception e) {
            log.error("Failed to retrieve attendance calendar: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve attendance calendar");
        }
    }
}