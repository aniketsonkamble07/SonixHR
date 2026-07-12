package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.entity.leave.PublicHoliday;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.enums.leave.LeaveType;
import com.sonixhr.repository.leave.LeaveRequestRepository;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import com.sonixhr.service.leave.LeaveConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveCalculator {

    private final LeaveRequestRepository leaveRequestRepo;
    private final TenantLeaveSettingsRepository tenantLeaveSettingsRepo;
    private final PublicHolidayRepository publicHolidayRepo;
    private final LeaveConfigurationService leaveConfigService;

    public Map<Long, BigDecimal> calculateUnpaidLeaveDaysForTenant(Long tenantId, int month, int year) {
        Map<Long, BigDecimal> employeeLopDays = new HashMap<>();

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd   = YearMonth.of(year, month).atEndOfMonth();

        // Fetch all approved unpaid leaves overlapping this month
        List<LeaveRequest> unpaidLeaves = leaveRequestRepo
                .findAllApprovedLeavesInDateRange(tenantId, monthStart, monthEnd).stream()
                .filter(l -> l.getLeaveType() == LeaveType.UNPAID)
                .collect(Collectors.toList());

        // Fetch Tenant Leave Settings
        TenantLeaveSettings settings = tenantLeaveSettingsRepo.findById(tenantId).orElse(null);

        for (LeaveRequest leave : unpaidLeaves) {
            Employee employee = leave.getEmployee();
            Long employeeId   = employee.getId();

            // Calculate overlap dates in this month
            LocalDate overlapStart = leave.getStartDate().isBefore(monthStart) ? monthStart : leave.getStartDate();
            LocalDate overlapEnd   = leave.getEndDate().isAfter(monthEnd) ? monthEnd : leave.getEndDate();

            // Pre-compute holiday dates for the overlap range — needed for both half-day and full-day checks
            Set<LocalDate> holidayDates = new HashSet<>();
            if (settings != null && (settings.getIncludeNationalHolidays() || settings.getIncludeStateHolidays())) {
                List<PublicHoliday> holidays = publicHolidayRepo
                        .findByTenantIdAndHolidayDateBetween(tenantId, overlapStart, overlapEnd);
                for (PublicHoliday h : holidays) {
                    boolean isNational = "NATIONAL".equalsIgnoreCase(h.getType())
                            && settings.getIncludeNationalHolidays();
                    boolean isState = settings.getIncludeStateHolidays() &&
                            ((settings.getState() != null
                                    && settings.getState().name().equalsIgnoreCase(h.getRegion())) ||
                             (settings.getStateText() != null
                                    && settings.getStateText().equalsIgnoreCase(h.getRegion())));
                    if (isNational || isState) {
                        holidayDates.add(h.getHolidayDate());
                    }
                }
            }

            BigDecimal leaveDays;
            if (Boolean.TRUE.equals(leave.getIsHalfDay())) {
                LocalDate halfDayDate = overlapStart; // for a half-day leave, startDate == endDate
                boolean isWeekend = leaveConfigService.isWeekendForEmployee(halfDayDate, employee, settings);
                boolean isHoliday = holidayDates.contains(halfDayDate);
                boolean skipDay   = (isWeekend  && (settings == null || settings.getCountWeekendsAsLeave() == null
                                               || !settings.getCountWeekendsAsLeave()))
                                 || (isHoliday && (settings == null || settings.getCountHolidaysAsLeave() == null
                                               || !settings.getCountHolidaysAsLeave()));
                leaveDays = skipDay ? BigDecimal.ZERO : BigDecimal.valueOf(0.5);
            } else {
                double days = 0;
                LocalDate date = overlapStart;

                while (!date.isAfter(overlapEnd)) {
                    boolean isWeekend = leaveConfigService.isWeekendForEmployee(date, employee, settings);
                    boolean isHoliday = holidayDates.contains(date);

                    boolean countDay = true;
                    if (isWeekend && (settings == null || settings.getCountWeekendsAsLeave() == null
                            || !settings.getCountWeekendsAsLeave())) {
                        countDay = false;
                    }
                    if (isHoliday && (settings == null || settings.getCountHolidaysAsLeave() == null
                            || !settings.getCountHolidaysAsLeave())) {
                        countDay = false;
                    }

                    if (countDay) {
                        days++;
                    }
                    date = date.plusDays(1);
                }
                leaveDays = BigDecimal.valueOf(days);
            }

            BigDecimal currentSum = employeeLopDays.getOrDefault(employeeId, BigDecimal.ZERO);
            employeeLopDays.put(employeeId, currentSum.add(leaveDays));
        }

        return employeeLopDays;
    }
}
