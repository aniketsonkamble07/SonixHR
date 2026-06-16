package com.sonixhr.service.calendar;

import com.sonixhr.dto.calendar.CalendarDayDTO;
import com.sonixhr.dto.calendar.CalendarMonthDTO;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.PublicHoliday;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.enums.attendance.AttendanceStatus;
import com.sonixhr.enums.calendar.CalendarAttendanceStatus;
import com.sonixhr.enums.calendar.CalendarDayType;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import com.sonixhr.service.attendance.ManualAttendanceService;
import com.sonixhr.service.leave.LeaveService;
import com.sonixhr.service.leave.LeaveConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private final ManualAttendanceService attendanceService;
    private final LeaveService leaveService;
    private final EmployeeRepository employeeRepository;
    private final TenantLeaveSettingsRepository settingsRepository;
    private final PublicHolidayRepository holidayRepository;
    private final LeaveConfigurationService leaveConfigService;

    /**
     * Get employee calendar by combining attendance, leave, and holiday data
     */
    public CalendarMonthDTO getEmployeeCalendar(Long employeeId, Long tenantId, int year, int month) {
        log.info("Getting calendar for employee: {} for {}-{}", employeeId, year, month);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        TenantLeaveSettings settings = settingsRepository.findById(tenantId).orElse(null);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Get data from existing services
        List<AttendanceRecord> attendanceRecords = attendanceService.getEmployeeAttendance(employeeId, startDate, endDate);
        List<LeaveResponseDTO> approvedLeaves = leaveService.getApprovedLeavesForCalendar(employeeId, tenantId, year, month);
        List<PublicHoliday> holidays = holidayRepository.findByTenantIdAndHolidayDateBetween(tenantId, startDate, endDate);

        // Build maps for quick lookup
        Map<LocalDate, AttendanceRecord> attendanceMap = attendanceRecords.stream()
                .collect(Collectors.toMap(AttendanceRecord::getAttendanceDate, a -> a, (a1, a2) -> a1));

        Map<LocalDate, List<LeaveResponseDTO>> leaveMap = new HashMap<>();
        for (LeaveResponseDTO leave : approvedLeaves) {
            LocalDate start = leave.getStartDate();
            LocalDate end = leave.getEndDate();
            LocalDate current = start.isBefore(startDate) ? startDate : start;
            LocalDate limit = end.isAfter(endDate) ? endDate : end;
            while (!current.isAfter(limit)) {
                leaveMap.computeIfAbsent(current, k -> new ArrayList<>()).add(leave);
                current = current.plusDays(1);
            }
        }

        // Filter holidays based on tenant's regional settings
        List<PublicHoliday> filteredHolidays = new ArrayList<>();
        if (settings != null) {
            for (PublicHoliday h : holidays) {
                boolean isNational = "NATIONAL".equalsIgnoreCase(h.getType()) && Boolean.TRUE.equals(settings.getIncludeNationalHolidays());
                boolean isState = Boolean.TRUE.equals(settings.getIncludeStateHolidays()) && 
                                  settings.getState() != null && 
                                  settings.getState().equalsIgnoreCase(h.getRegion());
                if (isNational || isState) {
                    filteredHolidays.add(h);
                }
            }
        } else {
            filteredHolidays = holidays;
        }

        Map<LocalDate, PublicHoliday> holidayMap = filteredHolidays.stream()
                .collect(Collectors.toMap(PublicHoliday::getHolidayDate, h -> h, (h1, h2) -> h1));

        // Build calendar days
        List<CalendarDayDTO> days = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            final LocalDate currentDate = date;

            AttendanceRecord attendance = attendanceMap.get(currentDate);
            List<LeaveResponseDTO> leaves = leaveMap.get(currentDate);
            PublicHoliday holiday = holidayMap.get(currentDate);

            boolean isWeekend = leaveConfigService.isWeekendForEmployee(currentDate, employee, settings);
            boolean isHoliday = holiday != null;

            List<LeaveResponseDTO> activeLeavesForDay = leaves;
            if (leaves != null && !leaves.isEmpty()) {
                boolean countWeekend = settings != null && Boolean.TRUE.equals(settings.getCountWeekendsAsLeave());
                boolean countHoliday = settings != null && Boolean.TRUE.equals(settings.getCountHolidaysAsLeave());

                if ((isWeekend && !countWeekend) || (isHoliday && !countHoliday)) {
                    activeLeavesForDay = Collections.emptyList();
                }
            }

            CalendarDayDTO day = CalendarDayDTO.builder()
                    .date(date)
                    .dayOfMonth(date.getDayOfMonth())
                    .dayOfWeek(date.getDayOfWeek().toString())
                    .dayOfWeekValue(date.getDayOfWeek().getValue())
                    .isWeekend(isWeekend)
                    .isPast(date.isBefore(today))
                    .isToday(date.equals(today))
                    .type(getDayType(attendance, activeLeavesForDay, holiday, isWeekend))
                    .status(getDayStatus(attendance, activeLeavesForDay, holiday, isWeekend))
                    .displayName(getDisplayName(attendance, activeLeavesForDay, holiday, isWeekend))
                    .color(getDayColor(attendance, activeLeavesForDay, holiday, isWeekend))
                    .description(getDescription(attendance, activeLeavesForDay, holiday))
                    .overtimeHours(attendance != null ? attendance.getOvertimeHours() : null)
                    .leaveType(getLeaveType(activeLeavesForDay))
                    .build();

            days.add(day);
        }

        // Calculate summary
        Map<String, Object> summary = calculateSummary(days);

        return CalendarMonthDTO.builder()
                .year(year)
                .month(month)
                .monthName(yearMonth.getMonth().toString())
                .monthDisplayName(yearMonth.getMonth().toString() + " " + year)
                .days(days)
                .summary(summary)
                .build();
    }

    // =====================================================
    // HELPER METHODS FOR DETERMINING DAY TYPE
    // =====================================================

    private CalendarDayType getDayType(AttendanceRecord attendance, List<LeaveResponseDTO> leaves, PublicHoliday holiday, boolean isWeekend) {
        if (holiday != null) {
            return CalendarDayType.HOLIDAY;
        }
        if (leaves != null && !leaves.isEmpty()) {
            return CalendarDayType.LEAVE;
        }
        if (attendance != null) {
            return CalendarDayType.ATTENDANCE;
        }
        if (isWeekend) {
            return CalendarDayType.WEEKEND;
        }
        return CalendarDayType.ABSENT;
    }

    private CalendarAttendanceStatus getDayStatus(AttendanceRecord attendance, List<LeaveResponseDTO> leaves, PublicHoliday holiday, boolean isWeekend) {
        if (holiday != null) {
            return CalendarAttendanceStatus.HOLIDAY;
        }
        if (leaves != null && !leaves.isEmpty()) {
            return CalendarAttendanceStatus.ON_LEAVE;
        }
        if (attendance != null) {
            return mapAttendanceStatus(attendance.getStatus());
        }
        if (isWeekend) {
            return CalendarAttendanceStatus.WEEKEND;
        }
        return CalendarAttendanceStatus.ABSENT;
    }

    private String getDisplayName(AttendanceRecord attendance, List<LeaveResponseDTO> leaves, PublicHoliday holiday, boolean isWeekend) {
        if (holiday != null) {
            return holiday.getName();
        }
        if (leaves != null && !leaves.isEmpty()) {
            return leaves.get(0).getLeaveTypeDisplay();
        }
        if (attendance != null) {
            return attendance.getStatus().getDisplayName();
        }
        if (isWeekend) {
            return "Weekend";
        }
        return "Absent";
    }

    private String getDayColor(AttendanceRecord attendance, List<LeaveResponseDTO> leaves, PublicHoliday holiday, boolean isWeekend) {
        if (holiday != null) {
            return "#9c27b0";
        }
        if (leaves != null && !leaves.isEmpty()) {
            return getLeaveColorFromType(leaves.get(0).getLeaveType());
        }
        if (attendance != null) {
            return getAttendanceColor(attendance.getStatus());
        }
        if (isWeekend) {
            return "#475569";
        }
        return "#9e9e9e";
    }

    private String getDescription(AttendanceRecord attendance, List<LeaveResponseDTO> leaves, PublicHoliday holiday) {
        if (holiday != null) {
            return holiday.getDescription();
        }
        if (leaves != null && !leaves.isEmpty()) {
            return leaves.get(0).getReason();
        }
        if (attendance != null) {
            return attendance.getReason();
        }
        return null;
    }

    private String getLeaveType(List<LeaveResponseDTO> leaves) {
        if (leaves != null && !leaves.isEmpty()) {
            return leaves.get(0).getLeaveType().name();
        }
        return null;
    }

    // =====================================================
    // UTILITY METHODS
    // =====================================================

    private CalendarAttendanceStatus mapAttendanceStatus(AttendanceStatus status) {
        switch (status) {
            case PRESENT: return CalendarAttendanceStatus.PRESENT;
            case ABSENT: return CalendarAttendanceStatus.ABSENT;
            case LATE: return CalendarAttendanceStatus.LATE;
            case HALF_DAY: return CalendarAttendanceStatus.HALF_DAY;
            case ON_LEAVE: return CalendarAttendanceStatus.ON_LEAVE;
            default: return CalendarAttendanceStatus.ABSENT;
        }
    }

    private String getAttendanceColor(AttendanceStatus status) {
        switch (status) {
            case PRESENT: return "#4caf50";
            case ABSENT: return "#f44336";
            case LATE: return "#ff9800";
            case HALF_DAY: return "#2196f3";
            case ON_LEAVE: return "#9c27b0";
            default: return "#9e9e9e";
        }
    }

    private String getLeaveColorFromType(com.sonixhr.enums.leave.LeaveType leaveType) {
        switch (leaveType) {
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

    private Map<String, Object> calculateSummary(List<CalendarDayDTO> days) {
        long present = days.stream().filter(d -> d.getStatus() == CalendarAttendanceStatus.PRESENT).count();
        long absent = days.stream().filter(d -> d.getStatus() == CalendarAttendanceStatus.ABSENT).count();
        long late = days.stream().filter(d -> d.getStatus() == CalendarAttendanceStatus.LATE).count();
        long halfDay = days.stream().filter(d -> d.getStatus() == CalendarAttendanceStatus.HALF_DAY).count();
        long onLeave = days.stream().filter(d -> d.getStatus() == CalendarAttendanceStatus.ON_LEAVE).count();
        long holiday = days.stream().filter(d -> d.getType() == CalendarDayType.HOLIDAY).count();
        long weekend = days.stream().filter(d -> d.getType() == CalendarDayType.WEEKEND).count();

        long totalWorkingDays = days.size() - weekend - holiday;
        long totalPresent = present + late + halfDay;
        double attendanceRate = totalWorkingDays > 0 ? (totalPresent * 100.0 / totalWorkingDays) : 0;

        double totalOvertime = days.stream()
                .filter(d -> d.getOvertimeHours() != null)
                .mapToDouble(CalendarDayDTO::getOvertimeHours)
                .sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("present", present);
        summary.put("absent", absent);
        summary.put("late", late);
        summary.put("halfDay", halfDay);
        summary.put("onLeave", onLeave);
        summary.put("holiday", holiday);
        summary.put("weekend", weekend);
        summary.put("totalDays", days.size());
        summary.put("totalWorkingDays", totalWorkingDays);
        summary.put("totalPresent", totalPresent);
        summary.put("attendanceRate", Math.round(attendanceRate));
        summary.put("totalOvertimeHours", totalOvertime);

        return summary;
    }
}