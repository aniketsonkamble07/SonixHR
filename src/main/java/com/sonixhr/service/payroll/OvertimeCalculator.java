package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.TenantPayrollConfig;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class OvertimeCalculator {

    private final ManualAttendanceRepository manualAttendanceRepo;

    public void calculateOvertime(Employee employee, TenantPayrollConfig tenantConfig, LocalDate monthStart, LocalDate monthEnd, PeriodPayData data) {
        if (tenantConfig.isEnableOvertime()) {
            Double otHoursVal = manualAttendanceRepo.getTotalOvertimeByEmployeeAndDateRange(
                    tenantConfig.getTenant().getId(), employee.getId(), monthStart, monthEnd);
            if (otHoursVal != null && otHoursVal > 0) {
                data.setOvertimeHours(BigDecimal.valueOf(otHoursVal));
                data.setOvertimeRate(tenantConfig.getOvertimeRatePerHour() != null
                        ? tenantConfig.getOvertimeRatePerHour() : BigDecimal.ZERO);
                BigDecimal overtimePay = data.getOvertimeHours().multiply(data.getOvertimeRate())
                        .setScale(2, RoundingMode.HALF_UP);
                data.setOvertimePay(overtimePay);
                data.setGrossEarnings(data.getGrossEarnings().add(overtimePay));
                data.setTaxableGrossEarnings(data.getTaxableGrossEarnings().add(overtimePay));
            }
        }
    }
}
