package com.sonixhr.dto.payroll;

import com.sonixhr.enums.payroll.TaxRegime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRegimeSlabConfigDTO {

    private UUID id;

    @NotBlank(message = "Financial year is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Financial year must be in format YYYY-YY")
    private String financialYear;

    @NotNull(message = "Tax regime is required")
    private TaxRegime regime;

    @NotNull(message = "Slabs are required")
    @Valid
    private List<TaxSlabRowDTO> slabs;

    @PositiveOrZero(message = "Standard deduction must be positive or zero")
    private BigDecimal standardDeduction;

    @PositiveOrZero(message = "Rebate limit must be positive or zero")
    private BigDecimal rebateLimit;

    @PositiveOrZero(message = "Rebate max amount must be positive or zero")
    private BigDecimal rebateMaxAmount;

    @NotNull(message = "Cess percent is required")
    @PositiveOrZero(message = "Cess percent must be positive or zero")
    private BigDecimal cessPercent;

    @Valid
    private List<SurchargeSlabDTO> surchargeSlabs;
}
