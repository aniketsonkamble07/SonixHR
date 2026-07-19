package com.sonixhr.dto.payroll;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxSlabRowDTO {
    @NotNull(message = "Slab from amount is required")
    @PositiveOrZero(message = "Slab from amount must be positive or zero")
    private BigDecimal fromAmount;

    @PositiveOrZero(message = "Slab to amount must be positive or zero")
    private BigDecimal toAmount;

    @NotNull(message = "Slab rate percent is required")
    @PositiveOrZero(message = "Slab rate percent must be positive or zero")
    private BigDecimal ratePercent;
}
