package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.payroll.TaxRegime;
import com.sonixhr.enums.payroll.DeclarationStatus;
import com.sonixhr.repository.payroll.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TdsCalculator {

    private static final int MONTHS_IN_FY = 12;

    private final TaxRegimeSlabConfigRepository taxSlabConfigRepo;
    private final TaxDeclarationRepository taxDeclarationRepo;
    private final TaxDeclarationSectionRuleRepository sectionRuleRepo;
    private final PayslipRepository payslipRepo;
    private final PayslipItemRepository payslipItemRepo;

    /**
     * Computes the TDS to deduct for the current month using the projected-annual-tax method.
     */
    public BigDecimal calculateMonthlyTds(
            Employee employee,
            Long tenantId,
            TaxRegime regime,
            BigDecimal currentMonthTaxableGross,
            int month, int year) {

        String financialYear = resolveFinancialYear(month, year);
        LocalDate fyStart = fyStartDate(month, year);

        TaxRegimeSlabConfig slabConfig = taxSlabConfigRepo
                .findByFinancialYearAndRegime(financialYear, regime)
                .orElseThrow(() -> new IllegalStateException(
                        "No tax slab config found for FY " + financialYear + " / " + regime));

        // YTD taxable gross already paid (persisted payslips this FY, excludes current month)
        LocalDate ytdCutoff = LocalDate.of(year, month, 1).minusDays(1);
        
        int startVal = fyStart.getYear() * 12 + fyStart.getMonthValue();
        int endVal = ytdCutoff.getYear() * 12 + ytdCutoff.getMonthValue();
        
        BigDecimal ytdTaxableGross = BigDecimal.ZERO;
        if (ytdCutoff.isAfter(fyStart) || ytdCutoff.isEqual(fyStart)) {
            ytdTaxableGross = payslipRepo.sumTaxableGrossForEmployeeInFinancialYear(
                    tenantId, employee.getId(), startVal, endVal);
            if (ytdTaxableGross == null) ytdTaxableGross = BigDecimal.ZERO;
        }

        int monthsElapsed = monthsElapsed(fyStart, month, year);
        int remainingMonths = MONTHS_IN_FY - monthsElapsed; // includes current month
        if (remainingMonths <= 0) remainingMonths = 1;

        BigDecimal projectedAnnualGross = ytdTaxableGross
                .add(currentMonthTaxableGross.multiply(BigDecimal.valueOf(remainingMonths)));

        BigDecimal deductions = resolveDeductions(employee, financialYear, regime, slabConfig);

        BigDecimal taxableIncome = projectedAnnualGross.subtract(deductions).max(BigDecimal.ZERO);

        BigDecimal annualTax = computeSlabTax(taxableIncome, slabConfig);
        annualTax = applyRebate87A(annualTax, taxableIncome, slabConfig);
        annualTax = addCessAndSurcharge(annualTax, taxableIncome, slabConfig);

        BigDecimal ytdTdsDeducted = BigDecimal.ZERO;
        if (ytdCutoff.isAfter(fyStart) || ytdCutoff.isEqual(fyStart)) {
            ytdTdsDeducted = payslipItemRepo.sumTdsForEmployeeInFinancialYear(
                    tenantId, employee.getId(), startVal, endVal);
            if (ytdTdsDeducted == null) ytdTdsDeducted = BigDecimal.ZERO;
        }

        BigDecimal remainingTax = annualTax.subtract(ytdTdsDeducted).max(BigDecimal.ZERO);

        return remainingTax
                .divide(BigDecimal.valueOf(remainingMonths), 2, RoundingMode.HALF_UP);
    }

    private String resolveFinancialYear(int month, int year) {
        // Indian FY: April(4) to March(3)
        int fyStartYear = (month >= 4) ? year : year - 1;
        int fyEndYearShort = (fyStartYear + 1) % 100;
        return fyStartYear + "-" + String.format("%02d", fyEndYearShort);
    }

    private LocalDate fyStartDate(int month, int year) {
        int fyStartYear = (month >= 4) ? year : year - 1;
        return LocalDate.of(fyStartYear, 4, 1);
    }

    private int monthsElapsed(LocalDate fyStart, int month, int year) {
        LocalDate current = LocalDate.of(year, month, 1);
        return (int) java.time.temporal.ChronoUnit.MONTHS.between(fyStart, current);
    }

    private BigDecimal computeSlabTax(BigDecimal taxableIncome, TaxRegimeSlabConfig config) {
        BigDecimal tax = BigDecimal.ZERO;
        if (config.getSlabs() == null || config.getSlabs().isEmpty()) {
            return tax;
        }
        for (TaxSlabRow slab : config.getSlabs()) {
            if (taxableIncome.compareTo(slab.getFromAmount()) <= 0) break;
            BigDecimal slabTop = slab.getToAmount() != null ? slab.getToAmount() : taxableIncome;
            BigDecimal taxableInSlab = taxableIncome.min(slabTop)
                    .subtract(slab.getFromAmount())
                    .max(BigDecimal.ZERO);
            BigDecimal rate = slab.getRatePercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            tax = tax.add(taxableInSlab.multiply(rate));
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyRebate87A(BigDecimal tax, BigDecimal taxableIncome, TaxRegimeSlabConfig config) {
        if (config.getRebateLimit() == null) {
            return tax;
        }
        if (taxableIncome.compareTo(config.getRebateLimit()) <= 0) {
            BigDecimal maxRebate = config.getRebateMaxAmount() != null ? config.getRebateMaxAmount() : BigDecimal.ZERO;
            BigDecimal rebate = tax.min(maxRebate);
            return tax.subtract(rebate).max(BigDecimal.ZERO);
        }
        // Marginal relief zone: tax payable shouldn't exceed the excess income
        BigDecimal excess = taxableIncome.subtract(config.getRebateLimit());
        if (tax.compareTo(excess) > 0) {
            return excess;
        }
        return tax;
    }

    private BigDecimal addCessAndSurcharge(BigDecimal tax, BigDecimal taxableIncome, TaxRegimeSlabConfig config) {
        BigDecimal surchargeRate = BigDecimal.ZERO;
        if (config.getSurchargeSlabs() != null) {
            for (SurchargeSlab s : config.getSurchargeSlabs()) {
                if (taxableIncome.compareTo(s.getThreshold()) > 0) {
                    surchargeRate = s.getRatePercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                }
            }
        }
        BigDecimal surcharge = tax.multiply(surchargeRate);
        BigDecimal taxPlusSurcharge = tax.add(surcharge);
        BigDecimal cessPercent = config.getCessPercent() != null ? config.getCessPercent() : BigDecimal.ZERO;
        BigDecimal cessRate = cessPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal cess = taxPlusSurcharge.multiply(cessRate);
        return taxPlusSurcharge.add(cess).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDeductions(Employee employee, String financialYear, TaxRegime regime,
            TaxRegimeSlabConfig slabConfig) {

        BigDecimal deductions = slabConfig.getStandardDeduction() != null
                ? slabConfig.getStandardDeduction() : BigDecimal.ZERO;

        if (regime == TaxRegime.OLD_REGIME) {
            Optional<TaxDeclaration> declOpt = taxDeclarationRepo
                    .findByEmployeeIdAndFinancialYearAndStatus(
                            employee.getId(), financialYear, DeclarationStatus.VERIFIED);

            if (declOpt.isPresent()) {
                List<TaxDeclarationLineItem> items = declOpt.get().getLineItems();
                if (items != null) {
                    for (TaxDeclarationLineItem item : items) {
                        BigDecimal approved = item.getApprovedAmount() != null
                                ? item.getApprovedAmount() : item.getDeclaredAmount();
                        if (approved == null) approved = BigDecimal.ZERO;
                        BigDecimal cap = sectionRuleRepo
                                .findCap(item.getSection(), regime, financialYear)
                                .orElse(null);
                        deductions = deductions.add(cap != null ? approved.min(cap) : approved);
                    }
                }
            }
        }
        return deductions;
    }
}
