package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.payroll.PayrollComponent;
import com.sonixhr.repository.payroll.EmployeeSalaryComponentRepository;
import com.sonixhr.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryComponentCalculator {

    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepo;
    private final FormulaService formulaService;
    private final StatutoryCalculator statutoryCalculator;

    public PeriodPayData computePeriodPayData(
            EmployeeSalaryProfile profile,
            LocalDate periodStart, LocalDate periodEnd, int totalDaysInMonth,
            TenantPayrollConfig tenantConfig,
            List<TenantSalaryStructure> orderedStructure,
            List<StatutoryRateConfig> statutoryRates,
            Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState,
            BigDecimal lopDays, int month, int year,
            Map<String, String> customComponentTypes,
            Map<String, String> customComponentNames) {

        PeriodPayData data = new PeriodPayData();
        data.setLopDays(lopDays);

        Employee employee = profile.getEmployee();

        // 1. Active calendar days for this period
        int activeDays = (int) java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        if (activeDays < 0) activeDays = 0;

        // 2. Proration Factor (active days / total calendar days in month)
        BigDecimal prorationFactor = BigDecimal.valueOf(activeDays)
                .divide(BigDecimal.valueOf(totalDaysInMonth), 6, RoundingMode.HALF_UP);

        // LOP daily wage denominator
        BigDecimal workingDays = BigDecimal.valueOf(totalDaysInMonth);
        if (LopBasis.WORKING_DAYS.equals(tenantConfig.getLopBasis())
                && tenantConfig.getWorkingDaysPerMonth() != null) {
            workingDays = BigDecimal.valueOf(tenantConfig.getWorkingDaysPerMonth());
        }

        // 3. Setup evaluation variables with BigDecimal
        Map<String, Object> variables = new HashMap<>();
        BigDecimal monthlyCtc = profile.getMonthlyCtc();
        BigDecimal activeCtc  = monthlyCtc.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);

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

        // Check which employer contributions are present in the structure
        boolean hasEpfEr = orderedStructure.stream().anyMatch(s -> "EPF_ER".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEpsEr = orderedStructure.stream().anyMatch(s -> "EPS_ER".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEdli  = orderedStructure.stream().anyMatch(s -> "EDLI".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEsiEr = orderedStructure.stream().anyMatch(s -> "ESI_ER".equalsIgnoreCase(s.getComponentCode()));

        // 4. First pass: Calculate Base Allowances (before proration and LOP)
        Map<String, BigDecimal> baseAllowances = new LinkedHashMap<>();
        BigDecimal otherBaseAllowancesSum = BigDecimal.ZERO;

        for (TenantSalaryStructure item : orderedStructure) {
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(item.getComponentCode(), customComponentTypes))) {
                BigDecimal baseVal = BigDecimal.ZERO;
                if ("SPECIAL_ALLOWANCE".equalsIgnoreCase(item.getComponentCode())) {
                    // Compute employer contributions to determine SPECIAL_ALLOWANCE remainder
                    BigDecimal basicVal    = baseAllowances.getOrDefault("BASIC", BigDecimal.ZERO);
                    BigDecimal wagesBaseVal = basicVal;
                    if (tenantConfig.isEnforceNewLabourCodes()) {
                        BigDecimal floorVal = monthlyCtc.multiply(BigDecimal.valueOf(0.50))
                                .setScale(2, RoundingMode.HALF_UP);
                        wagesBaseVal = basicVal.max(floorVal);
                    }

                    double epfErRate   = variables.containsKey("EPF_ER_RATE") ? ((BigDecimal) variables.get("EPF_ER_RATE")).doubleValue() : 0.12;
                    double epsErRate   = variables.containsKey("EPS_ER_RATE") ? ((BigDecimal) variables.get("EPS_ER_RATE")).doubleValue() : 0.0833;
                    double epsErCap    = variables.containsKey("EPS_ER_CAP") ? ((BigDecimal) variables.get("EPS_ER_CAP")).doubleValue() : 1250.0;
                    double edliRate    = variables.containsKey("EDLI_RATE") ? ((BigDecimal) variables.get("EDLI_RATE")).doubleValue() : 0.005;
                    double edliCeiling = variables.containsKey("EDLI_CEILING") ? ((BigDecimal) variables.get("EDLI_CEILING")).doubleValue() : 15000.0;

                    BigDecimal epsEr = BigDecimal.ZERO;
                    if (hasEpsEr) {
                        BigDecimal epsErVal = wagesBaseVal.multiply(BigDecimal.valueOf(epsErRate));
                        epsEr = BigDecimal.valueOf(Math.min(Math.round(epsErVal.doubleValue()), epsErCap))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    BigDecimal epfEr = BigDecimal.ZERO;
                    if (hasEpfEr) {
                        BigDecimal epfErVal = wagesBaseVal.multiply(BigDecimal.valueOf(epfErRate))
                                .setScale(2, RoundingMode.HALF_UP);
                        epfEr = epfErVal.subtract(epsEr).max(BigDecimal.ZERO);
                    }

                    BigDecimal edli = BigDecimal.ZERO;
                    if (hasEdli) {
                        BigDecimal edliLimit = wagesBaseVal.min(BigDecimal.valueOf(edliCeiling));
                        edli = edliLimit.multiply(BigDecimal.valueOf(edliRate)).setScale(2, RoundingMode.HALF_UP);
                    }

                    BigDecimal esiEr = BigDecimal.ZERO;
                    if (hasEsiEr && tenantConfig.isEnableEsi()) {
                        BigDecimal cpGross = statutoryCalculator.getContributionPeriodStartGross(
                                tenantConfig.getTenant().getId(), employee.getId(), year, month, monthlyCtc);
                        if (cpGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
                            double esiErRate = variables.containsKey("ESI_ER_RATE") ? ((BigDecimal) variables.get("ESI_ER_RATE")).doubleValue() : 0.0325;
                            esiEr = wagesBaseVal.multiply(BigDecimal.valueOf(esiErRate))
                                    .setScale(2, RoundingMode.HALF_UP);
                        }
                    }

                    BigDecimal employerContributionsSum = epfEr.add(epsEr).add(edli).add(esiEr);
                    baseVal = monthlyCtc.subtract(otherBaseAllowancesSum).subtract(employerContributionsSum)
                            .max(BigDecimal.ZERO);
                } else {
                    baseVal = calculateComponentValue(item, overrides, variables);
                    otherBaseAllowancesSum = otherBaseAllowancesSum.add(baseVal);
                }
                baseAllowances.put(item.getComponentCode(), baseVal);
                variables.put(item.getComponentCode(), baseVal);
            }
        }

        // 5. Second pass: Apply Proration and LOP deductions to base values
        for (TenantSalaryStructure item : orderedStructure) {
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(item.getComponentCode(), customComponentTypes))) {
                BigDecimal baseVal    = baseAllowances.getOrDefault(item.getComponentCode(), BigDecimal.ZERO);
                BigDecimal proratedVal = baseVal.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);

                if (lopDays.compareTo(BigDecimal.ZERO) > 0 && isLopApplicable(item.getComponentCode(), customComponentTypes)) {
                    BigDecimal dailyWage    = baseVal.divide(workingDays, 6, RoundingMode.HALF_UP);
                    BigDecimal lopDeduction = dailyWage.multiply(lopDays).setScale(2, RoundingMode.HALF_UP);
                    proratedVal = proratedVal.subtract(lopDeduction).max(BigDecimal.ZERO);
                }

                data.getComponentValues().put(item.getComponentCode(), proratedVal);
                data.getExpressions().put(item.getComponentCode(), getFormulaExpression(item, overrides));
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
            BigDecimal floor = activeCtc.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP);
            data.setWagesBase(basic.max(floor));
        }
        variables.put("WAGES_BASE", data.getWagesBase());
        variables.put("GROSS", data.getGrossEarnings());

        data.setContributionPeriodGross(statutoryCalculator.getContributionPeriodStartGross(
                tenantConfig.getTenant().getId(), employee.getId(), year, month, monthlyCtc));
        variables.put("CONTRIBUTION_PERIOD_GROSS", data.getContributionPeriodGross());

        // 7. Deductions pass
        for (TenantSalaryStructure item : orderedStructure) {
            if ("DEDUCTION".equalsIgnoreCase(getComponentType(item.getComponentCode(), customComponentTypes))) {
                BigDecimal val = BigDecimal.ZERO;
                String code = item.getComponentCode();

                if ("ESI_EE".equalsIgnoreCase(code) || "ESI_ER".equalsIgnoreCase(code)) {
                    if (tenantConfig.isEnableEsi()
                            && data.getContributionPeriodGross().compareTo(BigDecimal.valueOf(21000)) <= 0) {
                        val = calculateComponentValue(item, overrides, variables);
                    }
                } else if ("PT_DEDUCTION".equalsIgnoreCase(code) || "PT".equalsIgnoreCase(code)) {
                    if (tenantConfig.isEnablePt()) {
                        String workCountry = employee.getWorkCountry();
                        if (workCountry != null && !"IN".equalsIgnoreCase(workCountry)) {
                            val = BigDecimal.ZERO;
                        } else {
                            IndianState ptState = employee.getWorkState();
                            if (ptState == null && employee.getTenant() != null) {
                                ptState = employee.getTenant().getState();
                            }
                            if (ptState == null) {
                                ptState = employee.getState();
                            }
                            if (ptState == null) {
                                log.warn("Employee ID {} missing state — PT skipped", employee.getId());
                                throw new BusinessException("PT_STATE_MISSING",
                                        "Employee is missing a valid work state or personal state configuration for Professional Tax calculation");
                            }
                            val = statutoryCalculator.calculatePTAmount(ptState, data.getGrossEarnings(), month, ptSlabsByState);
                        }
                    }
                } else if ("EPF_EE".equalsIgnoreCase(code)) {
                    if (tenantConfig.isEnablePfCapping()) {
                        val = calculateComponentValue(item, overrides, variables);
                    } else {
                        double epfEeRate = variables.containsKey("EPF_EE_RATE") ? ((BigDecimal) variables.get("EPF_EE_RATE")).doubleValue() : 0.12;
                        val = data.getWagesBase().multiply(BigDecimal.valueOf(epfEeRate))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    val = calculateComponentValue(item, overrides, variables);
                }

                data.getComponentValues().put(code, val);
                data.getExpressions().put(code, getFormulaExpression(item, overrides));
                variables.put(code, val);
                if (!isEmployerContribution(code)) {
                    data.setTotalDeductions(data.getTotalDeductions().add(val));
                }
            }
        }

        return data;
    }

    public BigDecimal calculateComponentValue(TenantSalaryStructure structure,
            List<EmployeeSalaryComponent> overrides,
            Map<String, Object> variables) {
        
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
                    return ctc.multiply(pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                            .setScale(2, RoundingMode.HALF_UP);
                } else if ("PERCENTAGE_OF_BASIC".equalsIgnoreCase(structure.getCalculationType())) {
                    BigDecimal basic = (BigDecimal) variables.getOrDefault("BASIC", BigDecimal.ZERO);
                    return basic.multiply(pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            if (compOverride.getOverrideFormula() != null && !compOverride.getOverrideFormula().isEmpty()) {
                return formulaService.evaluate(compOverride.getOverrideFormula(), variables);
            }
            return compOverride.getOverrideValue();
        }

        // Otherwise evaluate tenant salary structure rules
        if ("FIXED".equalsIgnoreCase(structure.getCalculationType())) {
            return structure.getValue();
        } else if ("PERCENTAGE_OF_CTC".equalsIgnoreCase(structure.getCalculationType())) {
            BigDecimal ctc = (BigDecimal) variables.getOrDefault("CTC", BigDecimal.ZERO);
            return ctc.multiply(structure.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
        } else if ("PERCENTAGE_OF_BASIC".equalsIgnoreCase(structure.getCalculationType())) {
            BigDecimal basic = (BigDecimal) variables.getOrDefault("BASIC", BigDecimal.ZERO);
            return basic.multiply(structure.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
        } else if ("FORMULA".equalsIgnoreCase(structure.getCalculationType())) {
            return formulaService.evaluate(getFormulaForComponent(structure.getComponentCode()), variables);
        }

        return BigDecimal.ZERO;
    }

    public String getFormulaExpression(TenantSalaryStructure structure, List<EmployeeSalaryComponent> overrides) {
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
            return getFormulaForComponent(structure.getComponentCode());
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

    public boolean isLopApplicable(String componentCode, Map<String, String> customComponentTypes) {
        return "ALLOWANCE".equals(getComponentType(componentCode, customComponentTypes));
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
