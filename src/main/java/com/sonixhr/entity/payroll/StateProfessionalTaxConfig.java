package com.sonixhr.entity.payroll;

import com.sonixhr.enums.IndianState;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "platform_state_pt_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateProfessionalTaxConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "state_code", nullable = false, length = 50)
    @jakarta.persistence.Convert(converter = com.sonixhr.enums.IndianStateConverter.class)
    private IndianState stateCode;

    @Column(name = "salary_range_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal salaryRangeMin;

    @Column(name = "salary_range_max", precision = 12, scale = 2)
    private BigDecimal salaryRangeMax; // Null = no upper limit

    @Column(name = "applicable_month")
    private Integer applicableMonth; // Null = applies to all months. Set to 2 for Maharashtra's Feb ₹300 rule.

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // e.g., 200.00 or 300.00

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;
}
