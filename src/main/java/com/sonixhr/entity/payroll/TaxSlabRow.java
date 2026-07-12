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
public class TaxSlabRow {

    @Column(name = "from_amount", precision = 12, scale = 2)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", precision = 12, scale = 2)
    private BigDecimal toAmount;

    @Column(name = "rate_percent", precision = 5, scale = 2)
    private BigDecimal ratePercent;
}
