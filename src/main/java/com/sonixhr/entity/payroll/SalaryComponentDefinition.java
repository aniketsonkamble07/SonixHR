package com.sonixhr.entity.payroll;

import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "salary_component_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryComponentDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tenant_payroll_config_id", nullable = false)
    private UUID tenantPayrollConfigId;

    @Column(name = "component_code", nullable = false)
    private String componentCode;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(name = "component_type", nullable = false)
    private String componentType; // "EARNING" or "DEDUCTION"

    @Column(name = "calculation_type", nullable = false)
    private String calculationType; // "FIXED", "PERCENTAGE_OF_CTC", "PERCENTAGE_OF_BASIC", "FORMULA"

    @Column(name = "default_value", precision = 12, scale = 4)
    private BigDecimal defaultValue;

    @Column(name = "formula_expression", columnDefinition = "TEXT")
    private String formulaExpression;

    @Column(name = "evaluation_order", nullable = false)
    private Integer evaluationOrder;

    @Column(name = "is_lop_applicable", nullable = false)
    private boolean isLopApplicable;

    @Column(name = "is_employer_contribution", nullable = false)
    private boolean isEmployerContribution;

    @Column(name = "is_mandatory", nullable = false)
    private boolean isMandatory; // Can't be disabled

    @Column(name = "allow_employee_override", nullable = false)
    private boolean allowEmployeeOverride; // Can employee customize?

    @Column(name = "is_allowed_by_tenant", nullable = false)
    private boolean isAllowedByTenant; // Tenant has approved this component

    @Column(name = "min_value", precision = 12, scale = 2)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 12, scale = 2)
    private BigDecimal maxValue;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @PrePersist
    protected void onCreate() {
        isActive = true;
    }
}