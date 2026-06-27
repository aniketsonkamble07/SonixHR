package com.sonixhr.entity.payroll;

import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tenant_payroll_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPayrollConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "lop_basis", nullable = false)
    private LopBasis lopBasis;

    @Column(name = "working_days_per_month")
    private Integer workingDaysPerMonth;

    @Column(name = "enable_pf_capping", nullable = false)
    private boolean enablePfCapping;

    @Column(name = "enable_esi", nullable = false)
    private boolean enableEsi;

    @Column(name = "enable_pt", nullable = false)
    private boolean enablePt;

    @Column(name = "enforce_new_labour_codes", nullable = false)
    private boolean enforceNewLabourCodes;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency;

    @Column(name = "default_tax_regime", nullable = false)
    private String defaultTaxRegime;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Builder.Default
    @Column(name = "enable_overtime", nullable = false)
    private boolean enableOvertime = false;

    @Builder.Default
    @Column(name = "overtime_rate_per_hour", precision = 12, scale = 2)
    private BigDecimal overtimeRatePerHour = BigDecimal.ZERO;

    @PrePersist
    protected void onCreate() {
        isActive = true;
    }
}