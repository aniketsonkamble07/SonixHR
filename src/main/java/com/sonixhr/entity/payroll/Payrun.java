package com.sonixhr.entity.payroll;

import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payruns", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payrun_month_year_tenant_version", columnNames = {"tenant_id", "month", "year", "version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payrun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "status", nullable = false)
    private String status; // DRAFT, APPROVED, PAID, SUPERSEDED

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
