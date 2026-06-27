package com.sonixhr.entity.payroll;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "platform_statutory_rate_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatutoryRateConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "component_code", nullable = false)
    private String componentCode; // e.g., "EPF_EE", "EPF_ER", "EPS_ER", "EDLI", "ESI_EE", "ESI_ER"

    @Column(name = "rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal rate; // e.g., 0.1200 for 12%, 0.0075 for 0.75%

    @Column(name = "wage_base", nullable = false)
    private String wageBase; // e.g., "WAGES_BASE" or "GROSS"

    @Column(name = "ceiling_amount", precision = 12, scale = 2)
    private BigDecimal ceilingAmount; // e.g., 15000.00 for EPF, 21000.00 for ESI

    @Column(name = "cap_amount", precision = 12, scale = 2)
    private BigDecimal capAmount; // e.g., 1800.00 for EPF employee contribution, 1250.00 for EPS

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo; // Null indicates active current rate
}
