package com.sonixhr.controller.attendance;

import com.sonixhr.dto.attendance.*;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.attendance.ManualAttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
 
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
 
@Slf4j
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@SuppressWarnings({"unchecked", "null"})
public class ManualAttendanceController {

    private final ManualAttendanceService attendanceService;
    private final EmployeeRepository employeeRepository;

    // =====================================================
    // MARK ATTENDANCE
    // =====================================================

    @PostMapping("/mark")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_MARK_TEAM', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<ManualAttendanceRecordResponse> markAttendance(
            @Valid @RequestBody ManualAttendanceMarkRequest request,
            @AuthenticationPrincipal Employee currentUser) {

        var record = attendanceService.markAttendanceForDate(
                request.getEmployeeId(),
                request.getAttendanceDate(),
                request.getStatus(),
                request.getReason(),
                request.getOvertimeHours(),
                currentUser
        );

        return ResponseEntity.ok(convertToResponse(record));
    }

    @PostMapping("/team/bulk")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_MARK_TEAM', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<List<ManualAttendanceRecordResponse>> bulkMarkTeamAttendance(
            @Valid @RequestBody ManualBulkAttendanceMarkRequest request,
            @AuthenticationPrincipal Employee currentUser) {

        var records = attendanceService.markTeamAttendance(
                currentUser.getId(),
                request.getAttendanceDate(),
                request.getAttendanceMap(),
                request.getReasonMap(),
                request.getOvertimeMap()
        );

        return ResponseEntity.ok(records.stream().map(this::convertToResponse).toList());
    }

    @PostMapping("/team/quick-mark")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_MARK_TEAM', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<List<ManualAttendanceRecordResponse>> quickMarkTeamAttendance(
            @Valid @RequestBody ManualQuickMarkRequest request,
            @AuthenticationPrincipal Employee currentUser) {

        var records = attendanceService.quickMarkTeamAttendance(currentUser.getId(), request.getStatus());
        return ResponseEntity.ok(records.stream().map(this::convertToResponse).toList());
    }

    // =====================================================
    // OVERTIME
    // =====================================================

    @PostMapping("/overtime")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_EDIT', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<ManualAttendanceRecordResponse> addOvertime(
            @Valid @RequestBody ManualOvertimeRequest request,
            @AuthenticationPrincipal Employee currentUser) {

        var record = attendanceService.addOvertime(
                request.getEmployeeId(),
                request.getDate(),
                request.getOvertimeHours(),
                request.getReason(),
                currentUser
        );

        return ResponseEntity.ok(convertToResponse(record));
    }

    // =====================================================
    // TEAM MANAGEMENT
    // =====================================================

    @GetMapping("/team/members")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_VIEW_TEAM', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<List<ManualTeamMemberAttendanceDTO>> getTeamWithTodayAttendance(
            @AuthenticationPrincipal Employee currentUser) {

        return ResponseEntity.ok(attendanceService.getTeamWithTodayAttendance(currentUser.getId()));
    }

    @GetMapping("/team/search")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_VIEW_TEAM', 'SUPER_ADMIN')")
    public ResponseEntity<List<ManualTeamMemberAttendanceDTO>> searchTeamWithTodayAttendance(
            @RequestParam String searchTerm,
            @AuthenticationPrincipal Employee currentUser) {

        log.info("REST request to search team members with term: {} by manager: {}", searchTerm, currentUser.getId());
        List<ManualTeamMemberAttendanceDTO> results = attendanceService.searchTeamWithTodayAttendance(currentUser.getId(), searchTerm);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/team/summary")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_VIEW_ALL', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<ManualTeamAttendanceSummaryResponse> getTeamAttendanceSummary(
            @AuthenticationPrincipal Employee currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        var summary = attendanceService.getTeamAttendanceSummary(currentUser.getId(), startDate, endDate);
        return ResponseEntity.ok(convertToTeamSummaryResponse(summary));
    }

    // =====================================================
    // EMPLOYEE ATTENDANCE
    // =====================================================

    @GetMapping("/employee/{employeeId}/summary")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_VIEW_ALL', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<ManualAttendanceSummaryResponse> getEmployeeMonthlySummary(
            @PathVariable Long employeeId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal Employee currentUser) {

        var summary = attendanceService.getMonthlySummary(employeeId, year, month, currentUser.getTenantId());
        return ResponseEntity.ok(convertToSummaryResponse(summary));
    }

    @GetMapping("/employee/{employeeId}/calendar")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_VIEW_OWN', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<ManualAttendanceCalendarResponse> getEmployeeCalendar(
            @PathVariable Long employeeId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal Employee currentUser) {

        var calendar = attendanceService.getAttendanceCalendar(employeeId, year, month, currentUser.getTenantId());
        return ResponseEntity.ok(convertToCalendarResponse(employeeId, year, month, calendar));
    }

    // =====================================================
    // DASHBOARD
    // =====================================================

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_VIEW_ALL', 'SUPER_ADMIN')")  // ✅ Updated
    public ResponseEntity<ManualDashboardStatsResponse> getDashboardStats(
            @AuthenticationPrincipal Employee currentUser) {

        var stats = attendanceService.getDashboardStats(currentUser);
        return ResponseEntity.ok(convertToDashboardResponse(stats));
    }

    // =====================================================
    // CONVERSION METHODS
    // =====================================================

    private ManualAttendanceRecordResponse convertToResponse(AttendanceRecord record) {
        return ManualAttendanceRecordResponse.builder()
                .id(record.getId())
                .tenantId(record.getTenant().getId())
                .employeeId(record.getEmployee().getId())
                .employeeName(record.getEmployee().getFullName())
                .employeeCode(record.getEmployee().getEmployeeCode())
                .attendanceDate(record.getAttendanceDate())
                .status(record.getStatus())
                .overtimeHours(record.getOvertimeHours())
                .reason(record.getReason())
                .markedByName(record.getMarkedByName())
                .markedByRole(record.getMarkedByRole())
                .markedAt(record.getMarkedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private ManualAttendanceSummaryResponse convertToSummaryResponse(Map<String, Object> summary) {
        return ManualAttendanceSummaryResponse.builder()
                .employeeId((Long) summary.get("employeeId"))
                .employeeName((String) summary.get("employeeName"))
                .employeeCode((String) summary.get("employeeCode"))
                .hireDate((LocalDate) summary.get("hireDate"))
                .year((Integer) summary.get("year"))
                .month((Integer) summary.get("month"))
                .periodStart((LocalDate) summary.get("periodStart"))
                .periodEnd((LocalDate) summary.get("periodEnd"))
                .totalDaysInMonth((Integer) summary.get("totalDaysInMonth"))
                .present((Long) summary.get("present"))
                .absent((Long) summary.get("absent"))
                .halfDay((Long) summary.get("halfDay"))
                .late((Long) summary.get("late"))
                .onLeave((Long) summary.get("onLeave"))
                .totalOvertimeHours(getAsPrimitiveDouble(summary.get("totalOvertimeHours")))
                .attendanceRate(getAsPrimitiveDouble(summary.get("attendanceRate")))
                .build();
    }

    private ManualDashboardStatsResponse convertToDashboardResponse(Map<String, Object> stats) {
        return ManualDashboardStatsResponse.builder()
                .date((LocalDate) stats.get("date"))
                .totalEmployees((Long) stats.get("totalEmployees"))
                .present((Long) stats.get("present"))
                .absent((Long) stats.get("absent"))
                .onLeave((Long) stats.get("onLeave"))
                .pending((Long) stats.get("pending"))
                .attendancePercentage(getAsPrimitiveDouble(stats.get("attendancePercentage")))
                .build();
    }

    private ManualTeamAttendanceSummaryResponse convertToTeamSummaryResponse(Map<String, Object> summary) {
        return ManualTeamAttendanceSummaryResponse.builder()
                .startDate((LocalDate) summary.get("startDate"))
                .endDate((LocalDate) summary.get("endDate"))
                .totalTeamMembers((Integer) summary.get("totalTeamMembers"))
                .teamTotals((Map<String, Object>) summary.get("teamTotals"))
                .employeeSummaries((Map<String, Map<String, Object>>) summary.get("employeeSummaries"))
                .build();
    }

    private ManualAttendanceCalendarResponse convertToCalendarResponse(Long employeeId, int year, int month,
                                                                       Map<LocalDate, Map<String, Object>> calendar) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        Map<LocalDate, ManualAttendanceCalendarResponse.CalendarDayInfo> convertedCalendar = new LinkedHashMap<>();

        for (var entry : calendar.entrySet()) {
            var dayInfo = entry.getValue();
            convertedCalendar.put(entry.getKey(),
                    ManualAttendanceCalendarResponse.CalendarDayInfo.builder()
                            .date((LocalDate) dayInfo.get("date"))
                            .dayOfWeek((String) dayInfo.get("dayOfWeek"))
                            .status((String) dayInfo.get("status"))
                            .reason((String) dayInfo.get("reason"))
                            .overtimeHours(getAsDouble(dayInfo.get("overtimeHours")))
                            .markedBy((String) dayInfo.get("markedBy"))
                            .isWeekend((Boolean) dayInfo.get("isWeekend"))
                            .build());
        }

        return ManualAttendanceCalendarResponse.builder()
                .employeeId(employeeId)
                .employeeName(employee != null ? employee.getFullName() : null)
                .employeeCode(employee != null ? employee.getEmployeeCode() : null)
                .year(year)
                .month(month)
                .calendar(convertedCalendar)
                .build();
    }

    private double getAsPrimitiveDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Double getAsDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return null;
        }
    }
}