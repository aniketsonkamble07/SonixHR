package com.sonixhr.service.payroll;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@SuppressWarnings("null")
public class PeriodPayData {
    private final Map<String, BigDecimal> componentValues = new LinkedHashMap<>();
    private final Map<String, String> expressions = new LinkedHashMap<>();
    
    private BigDecimal grossEarnings = BigDecimal.ZERO;
    private BigDecimal totalDeductions = BigDecimal.ZERO;
    private BigDecimal lopDays = BigDecimal.ZERO;
    private BigDecimal wagesBase = BigDecimal.ZERO;
    private BigDecimal contributionPeriodGross = BigDecimal.ZERO;
    private BigDecimal overtimeHours = BigDecimal.ZERO;
    private BigDecimal overtimeRate = BigDecimal.ZERO;
    private BigDecimal overtimePay = BigDecimal.ZERO;
    private BigDecimal taxableGrossEarnings = BigDecimal.ZERO;

    private final Map<String, BigDecimal> loanRecoveryBreakdown = new LinkedHashMap<>();
    private BigDecimal reimbursementTotal = BigDecimal.ZERO;
    private final Map<String, BigDecimal> reimbursementBreakdown = new LinkedHashMap<>();

    // NEW FIELDS
    private BigDecimal arrears = BigDecimal.ZERO;
    private BigDecimal bonus = BigDecimal.ZERO;

    // Safe getters
    public Map<String, BigDecimal> getComponentValues() {
        return Collections.unmodifiableMap(componentValues);
    }

    public Map<String, String> getExpressions() {
        return Collections.unmodifiableMap(expressions);
    }

    public Map<String, BigDecimal> getLoanRecoveryBreakdown() {
        return Collections.unmodifiableMap(loanRecoveryBreakdown);
    }

    public Map<String, BigDecimal> getReimbursementBreakdown() {
        return Collections.unmodifiableMap(reimbursementBreakdown);
    }

    // Safe mutation methods
    public void putComponentValue(String code, BigDecimal value) {
        if (value != null) {
            this.componentValues.put(code, value.setScale(2, RoundingMode.HALF_EVEN));
        }
    }

    public void putExpression(String code, String expression) {
        if (expression != null) {
            this.expressions.put(code, expression);
        }
    }

    public void putLoanRecovery(String loanId, BigDecimal amount) {
        if (loanId != null && amount != null) {
            this.loanRecoveryBreakdown.put(loanId, amount.setScale(2, RoundingMode.HALF_EVEN));
        }
    }

    public void putReimbursement(String category, BigDecimal amount) {
        if (category != null && amount != null) {
            this.reimbursementBreakdown.put(category, amount.setScale(2, RoundingMode.HALF_EVEN));
        }
    }

    public void addToComponentValue(String code, BigDecimal value) {
        if (code != null && value != null) {
            this.componentValues.merge(code, value.setScale(2, RoundingMode.HALF_EVEN), BigDecimal::add);
        }
    }

    public void addToLoanRecovery(String loanId, BigDecimal amount) {
        if (loanId != null && amount != null) {
            this.loanRecoveryBreakdown.merge(loanId, amount.setScale(2, RoundingMode.HALF_EVEN), BigDecimal::add);
        }
    }

    public void addToReimbursement(String category, BigDecimal amount) {
        if (category != null && amount != null) {
            this.reimbursementBreakdown.merge(category, amount.setScale(2, RoundingMode.HALF_EVEN), BigDecimal::add);
        }
    }

    // Helper to merge another PeriodPayData into this one
    public void merge(PeriodPayData other) {
        if (other == null) return;

        other.componentValues.forEach((code, val) -> 
            this.componentValues.merge(code, val, BigDecimal::add));
        
        this.expressions.putAll(other.expressions);

        other.loanRecoveryBreakdown.forEach((loanId, amount) -> 
            this.loanRecoveryBreakdown.merge(loanId, amount, BigDecimal::add));

        other.reimbursementBreakdown.forEach((category, amount) -> 
            this.reimbursementBreakdown.merge(category, amount, BigDecimal::add));

        this.grossEarnings = this.grossEarnings.add(other.grossEarnings);
        this.totalDeductions = this.totalDeductions.add(other.totalDeductions);
        this.lopDays = this.lopDays.add(other.lopDays);
        this.taxableGrossEarnings = this.taxableGrossEarnings.add(other.taxableGrossEarnings);
        this.overtimeHours = this.overtimeHours.add(other.overtimeHours);
        this.overtimePay = this.overtimePay.add(other.overtimePay);
        this.reimbursementTotal = this.reimbursementTotal.add(other.reimbursementTotal);

        // NEW: Merge arrears and bonus
        this.arrears = this.arrears.add(other.arrears);
        this.bonus = this.bonus.add(other.bonus);

        this.wagesBase = this.wagesBase.add(other.wagesBase);
        this.contributionPeriodGross = other.contributionPeriodGross;
        this.overtimeRate = other.overtimeRate;
    }
}
