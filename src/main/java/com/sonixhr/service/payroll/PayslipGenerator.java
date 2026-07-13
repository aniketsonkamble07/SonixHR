package com.sonixhr.service.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.repository.payroll.PayslipItemRepository;
import com.sonixhr.repository.payroll.PayslipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayslipGenerator {

    private final PayslipRepository payslipRepo;
    private final PayslipItemRepository payslipItemRepo;
    private final ObjectMapper objectMapper;
    private final SalaryComponentCalculator componentCalculator;

    public void persistPayslip(
            Payrun payrun,
            Employee employee,
            TenantPayrollConfig tenantConfig,
            PeriodPayData data,
            List<TenantSalaryStructure> orderedStructure,
            Map<String, String> customComponentTypes,
            Map<String, String> customComponentNames) {

        BigDecimal netPay = data.getGrossEarnings()
                .subtract(data.getTotalDeductions())
                .add(data.getReimbursementTotal())
                .setScale(2, RoundingMode.HALF_UP);

        Payslip payslip = Payslip.builder()
                .tenant(tenantConfig.getTenant())
                .payrunId(payrun.getId())
                .employee(employee)
                .grossEarnings(data.getGrossEarnings())
                .totalDeductions(data.getTotalDeductions())
                .netPay(netPay)
                .lopDays(data.getLopDays())
                .wagesBase(data.getWagesBase())
                .contributionPeriodGross(data.getContributionPeriodGross())
                .taxableGrossEarnings(data.getTaxableGrossEarnings())
                .build();
        payslip = payslipRepo.save(payslip);

        // Persist one PayslipItem per salary structure component
        for (TenantSalaryStructure item : orderedStructure) {
            BigDecimal amount     = data.getComponentValues().getOrDefault(item.getComponentCode(), BigDecimal.ZERO);
            String     expression = data.getExpressions().getOrDefault(item.getComponentCode(), "");

            Map<String, Object> auditVars = new HashMap<>();
            auditVars.put("WAGES_BASE", data.getWagesBase());
            auditVars.put("GROSS",      data.getGrossEarnings());
            auditVars.put("value",      amount);

            String resolvedVarsJson = "";
            try {
                resolvedVarsJson = objectMapper.writeValueAsString(auditVars);
            } catch (Exception ignored) {}

            payslipItemRepo.save(PayslipItem.builder()
                    .tenant(tenantConfig.getTenant())
                    .payslipId(payslip.getId())
                    .componentCode(item.getComponentCode())
                    .componentName(componentCalculator.getComponentName(item.getComponentCode(), customComponentNames))
                    .type(componentCalculator.getComponentType(item.getComponentCode(), customComponentTypes))
                    .amount(amount)
                    .expressionUsed(expression)
                    .resolvedVariables(resolvedVarsJson)
                    .build());
        }

        // Persist overtime item if overtime was earned this month
        if (tenantConfig.isEnableOvertime() && data.getOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> auditVars = new HashMap<>();
            auditVars.put("overtimeHours",       data.getOvertimeHours());
            auditVars.put("overtimeRatePerHour",  data.getOvertimeRate());
            auditVars.put("value",                data.getOvertimePay());

            String resolvedVarsJson = "";
            try {
                resolvedVarsJson = objectMapper.writeValueAsString(auditVars);
            } catch (Exception ignored) {}

            payslipItemRepo.save(PayslipItem.builder()
                    .tenant(tenantConfig.getTenant())
                    .payslipId(payslip.getId())
                    .componentCode("OVERTIME")
                    .componentName("Overtime Payment")
                    .type("ALLOWANCE")
                    .amount(data.getOvertimePay())
                    .expressionUsed(data.getOvertimeHours() + " hrs * " + data.getOvertimeRate() + "/hr")
                    .resolvedVariables(resolvedVarsJson)
                    .build());
        }

        // Persist loan EMI items
        for (Map.Entry<String, BigDecimal> entry : data.getLoanRecoveryBreakdown().entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> auditVars = new HashMap<>();
                auditVars.put("loanId", entry.getKey());
                auditVars.put("value", entry.getValue());

                String resolvedVarsJson = "";
                try {
                    resolvedVarsJson = objectMapper.writeValueAsString(auditVars);
                } catch (Exception ignored) {}

                payslipItemRepo.save(PayslipItem.builder()
                        .tenant(tenantConfig.getTenant())
                        .payslipId(payslip.getId())
                        .componentCode("LOAN_EMI")
                        .componentName("Loan/Advance Recovery")
                        .type("DEDUCTION")
                        .amount(entry.getValue())
                        .expressionUsed("Derived balance recovery")
                        .resolvedVariables(resolvedVarsJson)
                        .build());
            }
        }

        // Persist reimbursement items
        for (Map.Entry<String, BigDecimal> entry : data.getReimbursementBreakdown().entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                payslipItemRepo.save(PayslipItem.builder()
                        .tenant(tenantConfig.getTenant())
                        .payslipId(payslip.getId())
                        .componentCode(entry.getKey())
                        .componentName(entry.getKey().replace("_", " "))
                        .type("REIMBURSEMENT")
                        .amount(entry.getValue())
                        .expressionUsed("Approved reimbursement claim")
                        .resolvedVariables("{}")
                        .build());
            }
        }
    }
}
