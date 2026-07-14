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

    public void calculateOvertime(Employee employee, TenantPayrollConfig tenantConfig, 
            LocalDate monthStart, LocalDate monthEnd, PeriodPayData data) {
        if (tenantConfig.isEnableOvertime()) {
            Double otHoursVal = manualAttendanceRepo.getTotalOvertimeByEmployeeAndDateRange(
                    tenantConfig.getTenant().getId(), employee.getId(), monthStart, monthEnd);
            if (otHoursVal != null && otHoursVal > 0) {
                data.setOvertimeHours(BigDecimal.valueOf(otHoursVal));
                
                // Calculate normal hourly rate per Factories Act, 1948
                // Base should be wages (basic + dearness + other statutory elements)
                // NOT gross earnings minus deductions
                BigDecimal wagesBase = data.getWagesBase();
                if (wagesBase == null) {
                    wagesBase = data.getGrossEarnings();
                }
                BigDecimal normalHourlyRate = wagesBase
                    .divide(BigDecimal.valueOf(26 * 8), 6, RoundingMode.HALF_EVEN);
                
                // Overtime rate: tenant-configured rate or 2x normal rate (1.5x minimum per law, but 2x is standard)
                BigDecimal overtimeRate = (tenantConfig.getOvertimeRatePerHour() != null 
                        && tenantConfig.getOvertimeRatePerHour().compareTo(BigDecimal.ZERO) > 0)
                        ? tenantConfig.getOvertimeRatePerHour().setScale(2, RoundingMode.HALF_EVEN)
                        : normalHourlyRate.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_EVEN);
                
                data.setOvertimeRate(overtimeRate);
                
                BigDecimal overtimePay = data.getOvertimeHours().multiply(overtimeRate)
                    .setScale(2, RoundingMode.HALF_EVEN);
                data.setOvertimePay(overtimePay);
                data.setGrossEarnings(data.getGrossEarnings().add(overtimePay));
                data.setTaxableGrossEarnings(data.getTaxableGrossEarnings().add(overtimePay));
            }
        }
    }
}
