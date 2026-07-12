package com.sonixhr.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurchargeSlab {

    @Column(name = "threshold", precision = 12, scale = 2)
    private BigDecimal threshold;

    @Column(name = "rate_percent", precision = 5, scale = 2)
    private BigDecimal ratePercent;
}
