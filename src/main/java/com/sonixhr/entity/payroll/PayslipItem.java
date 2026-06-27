package com.sonixhr.entity.payroll;

import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payslip_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayslipItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "payslip_id", nullable = false)
    private UUID payslipId;

    @Column(name = "component_code", nullable = false)
    private String componentCode;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(name = "type", nullable = false)
    private String type; // ALLOWANCE, DEDUCTION

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "expression_used")
    private String expressionUsed; // Snapshot of evaluated formula string

    @Lob
    @Column(name = "resolved_variables", columnDefinition = "TEXT")
    private String resolvedVariables; // JSON dump of inputs (e.g. {BASIC_DA: 15000, EPF_RATE: 0.12}) for auditing
}
