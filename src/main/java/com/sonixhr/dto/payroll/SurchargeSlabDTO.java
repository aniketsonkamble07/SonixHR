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
public class SurchargeSlabDTO {
    @NotNull(message = "Surcharge threshold is required")
    @PositiveOrZero(message = "Surcharge threshold must be positive or zero")
    private BigDecimal threshold;

    @NotNull(message = "Surcharge rate percent is required")
    @PositiveOrZero(message = "Surcharge rate percent must be positive or zero")
    private BigDecimal ratePercent;
}
