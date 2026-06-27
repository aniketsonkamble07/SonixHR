package com.sonixhr.entity.payroll;

import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tenant_salary_structures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSalaryStructure {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tenant_payroll_config_id", nullable = false)
    private UUID tenantPayrollConfigId;

    @Column(name = "component_code", nullable = false)
    private String componentCode; // e.g., "BASIC", "HRA", "LTA", "SPECIAL_ALLOWANCE"

    @Column(name = "calculation_type", nullable = false)
    private String calculationType; // PERCENTAGE_OF_CTC, PERCENTAGE_OF_BASIC, FIXED, FORMULA

    @Column(name = "value", nullable = false, precision = 12, scale = 4)
    private BigDecimal value; // e.g., 50.00 (for 50% of CTC or Basic) or flat fixed amount

    @Column(name = "evaluation_order", nullable = false)
    private Integer evaluationOrder; // Defines topological calculation sequence. SPECIAL_ALLOWANCE must be last.

    @Column(name = "is_part_of_pf_wages", nullable = false)
    private boolean isPartOfPfWages;

    @Column(name = "is_part_of_esi_wages", nullable = false)
    private boolean isPartOfEsiWages;

    @Column(name = "is_taxable", nullable = false)
    private boolean isTaxable;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
