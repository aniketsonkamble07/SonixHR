package com.sonixhr.entity.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee_salary_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSalaryProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "version", nullable = false)
    private Integer version; // For historical tracking

    @Column(name = "monthly_ctc", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyCtc;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "tax_regime", nullable = false)
    private String taxRegime;

    @Enumerated(EnumType.STRING)
    @Column(name = "lop_basis_override")
    private LopBasis lopBasisOverride;

    @Column(name = "working_days_override")
    private Integer workingDaysOverride;

    @Column(name = "promotion_reason")
    private String promotionReason; // e.g., "PROMOTION", "ANNUAL_INCREMENT", "NEW_HIRE"

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "arrears_paid", nullable = false)
    @Builder.Default
    private boolean arrearsPaid = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        isActive = true;
        if (version == null) {
            version = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}