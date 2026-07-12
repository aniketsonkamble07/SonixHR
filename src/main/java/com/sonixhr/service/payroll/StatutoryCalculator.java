package com.sonixhr.service.payroll;

import com.sonixhr.entity.payroll.Payrun;
import com.sonixhr.entity.payroll.Payslip;
import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.PayrunRepository;
import com.sonixhr.repository.payroll.PayslipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatutoryCalculator {

    private final PayrunRepository payrunRepo;
    private final PayslipRepository payslipRepo;

    public BigDecimal getContributionPeriodStartGross(Long tenantId, Long employeeId, int payrunYear, int payrunMonth,
            BigDecimal currentGross) {
        // ESI periods: April to September (4-9) and October to March (10-3).
        // Find starting month of current contribution period
        int startMonth = (payrunMonth >= 4 && payrunMonth <= 9) ? 4 : 10;
        int startYear  = (payrunMonth < 4) ? payrunYear - 1 : payrunYear;

        if (startMonth == payrunMonth && startYear == payrunYear) {
            return currentGross;
        }

        // Query historical payslip at start of contribution period
        Optional<Payrun> startPayrun = payrunRepo.findByTenantAndMonthAndYear(tenantId, startMonth, startYear);
        if (startPayrun.isPresent()) {
            Optional<Payslip> startingPayslip = payslipRepo.findByPayrunIdAndEmployeeId(
                    startPayrun.get().getId(), employeeId);
            if (startingPayslip.isPresent()) {
                return startingPayslip.get().getGrossEarnings();
            }
        }

        // If no payslip is found (e.g. new joiner mid-period), use the provided fallback gross
        return currentGross;
    }

    public BigDecimal calculatePTAmount(IndianState state, BigDecimal grossEarnings, int month,
            Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState) {
        if (state == null || ptSlabsByState == null) {
            return BigDecimal.ZERO;
        }

        List<StateProfessionalTaxConfig> slabs = ptSlabsByState.get(state);
        if (slabs == null || slabs.isEmpty()) {
            return BigDecimal.ZERO;
        }

        for (StateProfessionalTaxConfig slab : slabs) {
            // Check month-specific rule
            if (slab.getApplicableMonth() != null && slab.getApplicableMonth() != month) {
                continue;
            }
            // Match gross range
            // Lower bound is exclusive if salaryRangeMin > 0, otherwise inclusive (>= 0)
            boolean matchesMin = (slab.getSalaryRangeMin().compareTo(BigDecimal.ZERO) == 0)
                    ? (grossEarnings.compareTo(slab.getSalaryRangeMin()) >= 0)
                    : (grossEarnings.compareTo(slab.getSalaryRangeMin()) > 0);

            boolean matchesMax = (slab.getSalaryRangeMax() == null)
                    || (grossEarnings.compareTo(slab.getSalaryRangeMax()) <= 0);

            if (matchesMin && matchesMax) {
                return slab.getAmount();
            }
        }
        return BigDecimal.ZERO;
    }
}
