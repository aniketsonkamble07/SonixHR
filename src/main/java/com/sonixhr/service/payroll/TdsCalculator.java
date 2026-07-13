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
import java.util.Map;
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

    // Statutory section limits as fallback
    private static final Map<String, BigDecimal> STATUTORY_SECTION_LIMITS = Map.of(
        "80C", new BigDecimal("150000"),
        "80D", new BigDecimal("25000"),
        "80DD", new BigDecimal("125000"),
        "80E", new BigDecimal("250000"),
        "80G", BigDecimal.valueOf(Long.MAX_VALUE), // No cap, but percentage-based
        "80TTA", new BigDecimal("10000"),
        "80TTB", new BigDecimal("50000")
    );

    public BigDecimal calculateMonthlyTds(
            Employee employee,
            Long tenantId,
            TaxRegime regime,
            BigDecimal currentMonthTaxableGross,
            int month, int year) {
        return calculateMonthlyTds(employee, tenantId, regime, currentMonthTaxableGross, BigDecimal.ZERO, month, year);
    }

    /**
     * Calculates tax on non-recurring income at marginal rate
     */
    public BigDecimal calculateTaxOnNonRecurringIncome(
            BigDecimal nonRecurringAmount,
            BigDecimal projectedAnnualGross,
            TaxRegimeSlabConfig slabConfig) {
        
        if (nonRecurringAmount == null || nonRecurringAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate tax on total projected income
        BigDecimal taxOnProjected = computeSlabTax(projectedAnnualGross, slabConfig);
        
        // Calculate tax on projected income minus non-recurring amount
        BigDecimal baseIncome = projectedAnnualGross.subtract(nonRecurringAmount).max(BigDecimal.ZERO);
        BigDecimal taxOnBase = computeSlabTax(baseIncome, slabConfig);
        
        // Marginal tax on non-recurring amount
        BigDecimal marginalTax = taxOnProjected.subtract(taxOnBase).max(BigDecimal.ZERO);
        
        // Apply rebate and cess to the marginal tax
        return applyRebateAndCess(marginalTax, projectedAnnualGross, slabConfig);
    }

    /**
     * Updated calculateMonthlyTds with non-recurring income handling
     */
    public BigDecimal calculateMonthlyTds(
            Employee employee,
            Long tenantId,
            TaxRegime regime,
            BigDecimal currentMonthTaxableGross,
            BigDecimal nonRecurringGross,
            int month, int year) {

        if (nonRecurringGross == null) {
            nonRecurringGross = BigDecimal.ZERO;
        }

        String financialYear = resolveFinancialYear(month, year);
        LocalDate fyStart = fyStartDate(month, year);

        TaxRegimeSlabConfig slabConfig = taxSlabConfigRepo
                .findByFinancialYearAndRegime(financialYear, regime)
                .orElseThrow(() -> new IllegalStateException(
                        "No tax slab config found for FY " + financialYear + " / " + regime));

        // YTD taxable gross already paid
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
        int remainingMonths = MONTHS_IN_FY - monthsElapsed;
        if (remainingMonths <= 0) remainingMonths = 1;

        BigDecimal regularCurrentMonthGross = currentMonthTaxableGross.subtract(nonRecurringGross).max(BigDecimal.ZERO);

        // Project annual regular income
        BigDecimal projectedAnnualRegularGross = ytdTaxableGross
                .add(regularCurrentMonthGross.multiply(BigDecimal.valueOf(remainingMonths)));

        // Project total annual income including non-recurring
        BigDecimal projectedAnnualTotalGross = projectedAnnualRegularGross.add(nonRecurringGross);

        BigDecimal deductions = resolveDeductions(employee, financialYear, regime, slabConfig);

        // Taxable income for regular income
        BigDecimal taxableRegularIncome = projectedAnnualRegularGross.subtract(deductions).max(BigDecimal.ZERO);
        
        // Tax on regular income
        BigDecimal annualTaxOnRegular = computeSlabTax(taxableRegularIncome, slabConfig);
        annualTaxOnRegular = applyRebateAndCess(annualTaxOnRegular, taxableRegularIncome, slabConfig);

        // Tax on non-recurring income at marginal rate
        BigDecimal taxOnNonRecurring = calculateTaxOnNonRecurringIncome(
                nonRecurringGross, projectedAnnualTotalGross.subtract(deductions), slabConfig);

        BigDecimal totalAnnualTax = annualTaxOnRegular.add(taxOnNonRecurring);

        BigDecimal ytdTdsDeducted = BigDecimal.ZERO;
        if (ytdCutoff.isAfter(fyStart) || ytdCutoff.isEqual(fyStart)) {
            ytdTdsDeducted = payslipItemRepo.sumTdsForEmployeeInFinancialYear(
                    tenantId, employee.getId(), startVal, endVal);
            if (ytdTdsDeducted == null) ytdTdsDeducted = BigDecimal.ZERO;
        }

        BigDecimal remainingTax = totalAnnualTax.subtract(ytdTdsDeducted).max(BigDecimal.ZERO);

        return remainingTax
                .divide(BigDecimal.valueOf(remainingMonths), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyRebateAndCess(BigDecimal tax, BigDecimal taxableIncome, TaxRegimeSlabConfig config) {
        // Apply rebate 87A if applicable
        BigDecimal afterRebate = applyRebate87A(tax, taxableIncome, config);
        // Apply surcharge and cess
        return addCessAndSurcharge(afterRebate, taxableIncome, config);
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
        return tax.setScale(0, RoundingMode.HALF_UP); // Tax rounded to nearest rupee
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
                        
                        BigDecimal cap = sectionRuleRepo.findCap(item.getSection(), regime, financialYear)
                            .orElseGet(() -> STATUTORY_SECTION_LIMITS.get(item.getSection()));

                        // For 80G, need percentage calculation (50% or 100%)
                        if ("80G".equalsIgnoreCase(item.getSection()) && item.getDeclaredAmount() != null) {
                            BigDecimal percentage = new BigDecimal("50");
                            if (item.getSubCategory() != null && item.getSubCategory().contains("100")) {
                                percentage = new BigDecimal("100");
                            }
                            BigDecimal allowedAmount = item.getDeclaredAmount()
                                .multiply(percentage.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
                            cap = allowedAmount.min(cap != null ? cap : allowedAmount);
                        }

                        deductions = deductions.add(cap != null ? approved.min(cap) : approved);
                    }
                }
            }
        }
        return deductions;
    }
}
