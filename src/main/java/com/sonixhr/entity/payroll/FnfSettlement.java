package com.sonixhr.entity.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fnf_settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FnfSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "termination_date", nullable = false)
    private LocalDate terminationDate;

    @Column(name = "last_drawn_basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal lastDrawnBasic;

    @Column(name = "gratuity_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal gratuityAmount;

    @Column(name = "gratuity_exempt", nullable = false, precision = 12, scale = 2)
    private BigDecimal gratuityExempt;

    @Column(name = "gratuity_taxable", nullable = false, precision = 12, scale = 2)
    private BigDecimal gratuityTaxable;

    @Column(name = "encashment_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal encashmentAmount;

    @Column(name = "encashment_exempt", nullable = false, precision = 12, scale = 2)
    private BigDecimal encashmentExempt;

    @Column(name = "encashment_taxable", nullable = false, precision = 12, scale = 2)
    private BigDecimal encashmentTaxable;

    @Column(name = "prorated_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal proratedSalary;

    @Column(name = "loan_recovery", nullable = false, precision = 12, scale = 2)
    private BigDecimal loanRecovery;

    @Column(name = "total_tds", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTds;

    @Column(name = "net_settlement", nullable = false, precision = 12, scale = 2)
    private BigDecimal netSettlement;

    @Column(name = "status", nullable = false)
    private String status; // DRAFT, APPROVED, PAID

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "fnfSettlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FnfSettlementItem> items = new ArrayList<>();
}
