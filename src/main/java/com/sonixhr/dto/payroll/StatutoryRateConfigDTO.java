package com.sonixhr.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatutoryRateConfigDTO {
    private UUID id;

    @NotBlank(message = "Component code is required")
    private String componentCode;

    @NotNull(message = "Rate is required")
    @PositiveOrZero(message = "Rate must be positive or zero")
    private BigDecimal rate;

    @NotBlank(message = "Wage base is required")
    private String wageBase;

    @PositiveOrZero(message = "Ceiling amount must be positive or zero")
    private BigDecimal ceilingAmount;

    @PositiveOrZero(message = "Cap amount must be positive or zero")
    private BigDecimal capAmount;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
