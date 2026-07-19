package com.sonixhr.dto.payroll;

import com.sonixhr.enums.IndianState;
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
public class StateProfessionalTaxConfigDTO {
    private UUID id;

    @NotNull(message = "State code is required")
    private IndianState stateCode;

    @NotNull(message = "Salary range minimum is required")
    @PositiveOrZero(message = "Salary range minimum must be positive or zero")
    private BigDecimal salaryRangeMin;

    @PositiveOrZero(message = "Salary range maximum must be positive or zero")
    private BigDecimal salaryRangeMax;

    private Integer applicableMonth;

    @NotNull(message = "Amount is required")
    @PositiveOrZero(message = "Amount must be positive or zero")
    private BigDecimal amount;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
