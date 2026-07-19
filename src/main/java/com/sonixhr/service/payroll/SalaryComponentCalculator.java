package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.payroll.PayrollComponent;
import com.sonixhr.repository.payroll.EmployeeSalaryComponentRepository;
import com.sonixhr.repository.payroll.SalaryComponentDefinitionRepository;
import com.sonixhr.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryComponentCalculator {

    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepo;
    private final FormulaService formulaService;
    private final StatutoryCalculator statutoryCalculator;
    private final SalaryComponentDefinitionRepository componentDefinitionRepo;

    private static final BigDecimal SPECIAL_ALLOWANCE_PERCENTAGE = new BigDecimal("0.10");

    public PeriodPayData computePeriodPayData(
            EmployeeSalaryProfile profile,
            LocalDate periodStart, LocalDate periodEnd, int totalDaysInMonth,
            TenantPayrollConfig tenantConfig,
            List<TenantSalaryStructure> orderedStructure,
            List<StatutoryRateConfig> statutoryRates,
            Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState,
            BigDecimal lopDays, int month, int year,
            Map<String, String> customComponentTypes,
            Map<String, String> customComponentNames,
            Map<String, String> customComponentFormulas) {

        PeriodPayData data = new PeriodPayData();
        data.setLopDays(lopDays);

        Employee employee = profile.getEmployee();

        // 1. Active calendar days for this period
        int activeDays = (int) java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        if (activeDays < 0) activeDays = 0;

        // 2. Proration Factor (active days / total calendar days in month) using banker's rounding
        BigDecimal prorationFactor = BigDecimal.valueOf(activeDays)
                .divide(BigDecimal.valueOf(totalDaysInMonth), 6, RoundingMode.HALF_EVEN);

        // Standard LOP days basis (26 days) as per industry guidelines
        BigDecimal workingDays = BigDecimal.valueOf(26);

        // 3. Setup evaluation variables with BigDecimal
        Map<String, Object> variables = new HashMap<>();
        BigDecimal monthlyCtc = profile.getMonthlyCtc();
        BigDecimal activeCtc  = monthlyCtc.multiply(prorationFactor).setScale(2, RoundingMode.HALF_EVEN);

        variables.put("CTC", monthlyCtc);
        variables.put("LOP_DAYS", lopDays);
        variables.put("ACTIVE_DAYS", BigDecimal.valueOf(activeDays));
        variables.put("TOTAL_DAYS", BigDecimal.valueOf(totalDaysInMonth));

        // Inject statutory rates into variables as BigDecimal
        for (StatutoryRateConfig rateConfig : statutoryRates) {
            variables.put(rateConfig.getComponentCode() + "_RATE", rateConfig.getRate());
            if (rateConfig.getCeilingAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CEILING", rateConfig.getCeilingAmount());
            }
            if (rateConfig.getCapAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CAP", rateConfig.getCapAmount());
            }
        }

        List<EmployeeSalaryComponent> overrides =
                employeeSalaryComponentRepo.findBySalaryProfileId(profile.getId());

        // 4. First pass: Calculate Base Allowances (before proration and LOP)
        Map<String, Boolean> lopApplicabilityMap = buildLopApplicabilityMap(tenantConfig.getTenant().getId());

        Map<String, BigDecimal> baseAllowances = new LinkedHashMap<>();
        BigDecimal sumOfOtherAllowances = BigDecimal.ZERO;

        for (TenantSalaryStructure item : orderedStructure) {
            String code = item.getComponentCode();
            if ("SPECIAL_ALLOWANCE".equalsIgnoreCase(code)) {
                continue; // Skip - calculate in second stage
            }
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(code, customComponentTypes))) {
                BigDecimal val = calculateComponentValue(item, overrides, variables, customComponentFormulas);
                baseAllowances.put(code, val);
                sumOfOtherAllowances = sumOfOtherAllowances.add(val);
                variables.put(code, val);
            }
        }

        BigDecimal specialAllowance = BigDecimal.ZERO;
        for (TenantSalaryStructure item : orderedStructure) {
            if ("SPECIAL_ALLOWANCE".equalsIgnoreCase(item.getComponentCode())) {
                // Calculate SPECIAL_ALLOWANCE as 10% of CTC using banker's rounding
                specialAllowance = monthlyCtc.multiply(SPECIAL_ALLOWANCE_PERCENTAGE)
                    .setScale(2, RoundingMode.HALF_EVEN);

                // CRITICAL: Ensure CTC = sum of all components
                BigDecimal sumOfAllComponents = baseAllowances.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(specialAllowance);

               // If there's a mismatch, log warning and adjust only if mismatch is minimal (rounding issues)
                if (sumOfAllComponents.compareTo(monthlyCtc) != 0) {
                    BigDecimal adjustment = monthlyCtc.subtract(sumOfAllComponents);
                   BigDecimal adjustmentAbs = adjustment.abs();
                    
                   // Allow adjustment only if due to rounding (less than ₹1)
                   if (adjustmentAbs.compareTo(BigDecimal.ONE) < 0) {
                       specialAllowance = specialAllowance.add(adjustment);
                       log.debug("Adjusted SPECIAL_ALLOWANCE by {} to balance CTC (rounding correction)", adjustment);
                   } else {
                       // Significant mismatch indicates configuration error
                       log.error("CTC mismatch for employee profile: configured components sum={}, expected CTC={}. Difference: {}", 
                           sumOfAllComponents, monthlyCtc, adjustment);
                       throw new BusinessException("CTC_MISMATCH", 
                           String.format("Salary structure components do not reconcile to CTC. Difference: ₹%.2f. Review salary structure configuration.", adjustment.doubleValue()));
                   }
               }
           }
        }
        baseAllowances.put("SPECIAL_ALLOWANCE", specialAllowance);
        variables.put("SPECIAL_ALLOWANCE", specialAllowance);

        // 5. Second pass: Apply Proration and LOP deductions to base values
        for (TenantSalaryStructure item : orderedStructure) {
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(item.getComponentCode(), customComponentTypes))) {
                BigDecimal baseVal    = baseAllowances.getOrDefault(item.getComponentCode(), BigDecimal.ZERO);
                BigDecimal proratedVal = baseVal.multiply(prorationFactor).setScale(2, RoundingMode.HALF_EVEN);

                if (lopDays.compareTo(BigDecimal.ZERO) > 0 && isLopApplicable(item.getComponentCode(), lopApplicabilityMap)) {
                    BigDecimal dailyWage    = baseVal.divide(workingDays, 6, RoundingMode.HALF_EVEN);
                    BigDecimal lopDeduction = dailyWage.multiply(lopDays).setScale(2, RoundingMode.HALF_EVEN);
                    proratedVal = proratedVal.subtract(lopDeduction).max(BigDecimal.ZERO);
                }
                data.putComponentValue(item.getComponentCode(), proratedVal);
                data.putExpression(item.getComponentCode(), getFormulaExpression(item, overrides, customComponentFormulas));
                variables.put(item.getComponentCode(), proratedVal);
                data.setGrossEarnings(data.getGrossEarnings().add(proratedVal));
                if (item.isTaxable()) {
                    data.setTaxableGrossEarnings(data.getTaxableGrossEarnings().add(proratedVal));
                }
            }
        }

        // Switch CTC to prorated (active) value for deduction calculations
        variables.put("CTC", activeCtc);

        // 6. Calculate Wages Base (Code on Wages 2019 Rule)
        BigDecimal basic = data.getComponentValues().getOrDefault("BASIC", BigDecimal.ZERO);
        data.setWagesBase(basic);
        if (tenantConfig.isEnforceNewLabourCodes()) {
            BigDecimal floor = activeCtc.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_EVEN);
            data.setWagesBase(basic.max(floor));
        }
        variables.put("WAGES_BASE", data.getWagesBase());
        variables.put("GROSS", data.getGrossEarnings());

        data.setContributionPeriodGross(statutoryCalculator.getContributionPeriodStartGross(
                tenantConfig.getTenant().getId(), employee.getId(), year, month, monthlyCtc));
        variables.put("CONTRIBUTION_PERIOD_GROSS", data.getContributionPeriodGross());

        // Get ESI ceiling from statutory rates with dynamic configuration
        BigDecimal esiCeiling = BigDecimal.valueOf(21000); // Default fallback
        for (StatutoryRateConfig rateConfig : statutoryRates) {
            if ("ESI_ER".equalsIgnoreCase(rateConfig.getComponentCode())) {
                if (rateConfig.getCeilingAmount() != null) {
                    esiCeiling = rateConfig.getCeilingAmount();
                    break;
                }
            }
        }

        // 7. Deductions pass
        for (TenantSalaryStructure item : orderedStructure) {
            if ("DEDUCTION".equalsIgnoreCase(getComponentType(item.getComponentCode(), customComponentTypes))) {
                BigDecimal val = BigDecimal.ZERO;
                String code = item.getComponentCode();

                if ("ESI_EE".equalsIgnoreCase(code) || "ESI_ER".equalsIgnoreCase(code)) {
                     // Handled post-merge in EmployeePayrunProcessor
                     continue;
                 } else if ("PT_DEDUCTION".equalsIgnoreCase(code) || "PT".equalsIgnoreCase(code)) {
                     // Handled post-merge in EmployeePayrunProcessor
                     continue;
                 } else if ("EPF_EE".equalsIgnoreCase(code)) {
                    // When capping is enabled, apply formula-based calculation from structure
                    // When capping is disabled, apply simple rate calculation
                    if (tenantConfig.isEnablePfCapping()) {
                        val = calculateComponentValue(item, overrides, variables, customComponentFormulas);
                    } else {
                        // Uncapped: simple EPF_EE rate * wages base
                        double epfEeRate = variables.containsKey("EPF_EE_RATE") ? ((BigDecimal) variables.get("EPF_EE_RATE")).doubleValue() : 0.12;
                        val = data.getWagesBase().multiply(BigDecimal.valueOf(epfEeRate))
                                .setScale(2, RoundingMode.HALF_EVEN);
                    }
                } else {
                    val = calculateComponentValue(item, overrides, variables, customComponentFormulas);
                }
                data.putComponentValue(code, val);
                data.putExpression(code, getFormulaExpression(item, overrides, customComponentFormulas));
                variables.put(code, val);
                if (!isEmployerContribution(code)) {
                    data.setTotalDeductions(data.getTotalDeductions().add(val));
                }
            }
        }

        return data;
    }

    private Map<String, Boolean> buildLopApplicabilityMap(Long tenantId) {
        List<SalaryComponentDefinition> definitions = componentDefinitionRepo
                .findAllowedByTenantAndDate(tenantId, LocalDate.now());
        
        return definitions.stream()
                .collect(Collectors.toMap(
                        d -> d.getComponentCode().toUpperCase(),
                        SalaryComponentDefinition::isLopApplicable,
                        (a, b) -> a
                ));
    }

    public BigDecimal calculateComponentValue(TenantSalaryStructure structure,
            List<EmployeeSalaryComponent> overrides,
            Map<String, Object> variables,
            Map<String, String> customComponentFormulas) {
        
        Optional<EmployeeSalaryComponent> override = overrides.stream()
                .filter(o -> o.getComponentCode().equals(structure.getComponentCode()))
                .findFirst();

        if (override.isPresent()) {
            EmployeeSalaryComponent compOverride = override.get();
            if ("PERCENTAGE".equalsIgnoreCase(compOverride.getOverrideType())) {
                BigDecimal pct = compOverride.getOverrideValue() != null ? compOverride.getOverrideValue()
                        : BigDecimal.ZERO;
                if ("PERCENTAGE_OF_CTC".equalsIgnoreCase(structure.getCalculationType())) {
                    BigDecimal ctc = (BigDecimal) variables.getOrDefault("CTC", BigDecimal.ZERO);
                    return ctc.multiply(pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_EVEN))
                            .setScale(2, RoundingMode.HALF_EVEN);
                } else if ("PERCENTAGE_OF_BASIC".equalsIgnoreCase(structure.getCalculationType())) {
                    BigDecimal basic = (BigDecimal) variables.getOrDefault("BASIC", BigDecimal.ZERO);
                    return basic.multiply(pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_EVEN))
                            .setScale(2, RoundingMode.HALF_EVEN);
                } else {
                    throw new BusinessException("INVALID_OVERRIDE_TYPE", 
                            "Percentage override is only allowed for components with PERCENTAGE_OF_BASIC or PERCENTAGE_OF_CTC calculation types");
                }
            }
            if (compOverride.getOverrideFormula() != null && !compOverride.getOverrideFormula().isEmpty()) {
                return formulaService.evaluate(compOverride.getOverrideFormula(), variables);
            }
            return compOverride.getOverrideValue();
        }

        // Guard against null values
        if (structure.getValue() == null) {
            log.warn("Structure value is null for component: {}", structure.getComponentCode());
            return BigDecimal.ZERO;
        }

        // Otherwise evaluate tenant salary structure rules
        if ("FIXED".equalsIgnoreCase(structure.getCalculationType())) {
            return structure.getValue();
        } else if ("PERCENTAGE_OF_CTC".equalsIgnoreCase(structure.getCalculationType())) {
            BigDecimal ctc = (BigDecimal) variables.getOrDefault("CTC", BigDecimal.ZERO);
            return ctc.multiply(structure.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_EVEN))
                    .setScale(2, RoundingMode.HALF_EVEN);
        } else if ("PERCENTAGE_OF_BASIC".equalsIgnoreCase(structure.getCalculationType())) {
            BigDecimal basic = (BigDecimal) variables.getOrDefault("BASIC", BigDecimal.ZERO);
            return basic.multiply(structure.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_EVEN))
                    .setScale(2, RoundingMode.HALF_EVEN);
        } else if ("FORMULA".equalsIgnoreCase(structure.getCalculationType())) {
            String formula = customComponentFormulas != null ? customComponentFormulas.get(structure.getComponentCode().toUpperCase()) : null;
            if (formula == null || formula.trim().isEmpty()) {
                formula = getFormulaForComponent(structure.getComponentCode());
            }
            return formulaService.evaluate(formula, variables);
        }

        return BigDecimal.ZERO;
    }

    public String getFormulaExpression(TenantSalaryStructure structure, List<EmployeeSalaryComponent> overrides, Map<String, String> customComponentFormulas) {
        Optional<EmployeeSalaryComponent> override = overrides.stream()
                .filter(o -> o.getComponentCode().equals(structure.getComponentCode()))
                .findFirst();
        if (override.isPresent()) {
            if (override.get().getOverrideFormula() != null) {
                return override.get().getOverrideFormula();
            }
            return "FIXED: " + override.get().getOverrideValue();
        }
        if ("FORMULA".equalsIgnoreCase(structure.getCalculationType())) {
            String formula = customComponentFormulas != null ? customComponentFormulas.get(structure.getComponentCode().toUpperCase()) : null;
            if (formula == null || formula.trim().isEmpty()) {
                formula = getFormulaForComponent(structure.getComponentCode());
            }
            return formula;
        }
        return structure.getCalculationType() + " : " + structure.getValue();
    }

    public String getFormulaForComponent(String componentCode) {
        PayrollComponent component = PayrollComponent.fromCode(componentCode);
        if (component != null && component.getDefaultFormula() != null) {
            return component.getDefaultFormula();
        }
        return "0.00";
    }

    public String getComponentType(String componentCode, Map<String, String> customComponentTypes) {
        PayrollComponent component = PayrollComponent.fromCode(componentCode);
        if (component != null) {
            return component.getType();
        }
        if (customComponentTypes != null && customComponentTypes.containsKey(componentCode.toUpperCase())) {
            String type = customComponentTypes.get(componentCode.toUpperCase());
            return "EARNING".equalsIgnoreCase(type) ? "ALLOWANCE" : "DEDUCTION";
        }
        return "ALLOWANCE"; // Default fallback
    }

    public String getComponentName(String componentCode, Map<String, String> customComponentNames) {
        PayrollComponent component = PayrollComponent.fromCode(componentCode);
        if (component != null) {
            return component.getName();
        }
        if (customComponentNames != null && customComponentNames.containsKey(componentCode.toUpperCase())) {
            return customComponentNames.get(componentCode.toUpperCase());
        }
        return componentCode;
    }

    public boolean isLopApplicable(String componentCode, Map<String, Boolean> lopApplicabilityMap) {
        if (lopApplicabilityMap == null) {
            // Fallback to type-based check
            return "ALLOWANCE".equals(getComponentType(componentCode, null));
        }
        return lopApplicabilityMap.getOrDefault(componentCode.toUpperCase(), false);
    }

    public boolean isEmployerContribution(String componentCode) {
        PayrollComponent component = PayrollComponent.fromCode(componentCode);
        if (component != null) {
            return "EPF_ER".equals(component.name()) || "EPS_ER".equals(component.name()) ||
                   "EDLI".equals(component.name()) || "ESI_ER".equals(component.name());
        }
        return false;
    }
}
