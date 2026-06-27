package com.sonixhr.dto.payroll;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request/Response object for employee component override")
public class EmployeeComponentOverrideDTO {

    @NotBlank(message = "Component code is required")
    @Schema(description = "Component code", example = "BASIC", requiredMode = Schema.RequiredMode.REQUIRED)
    private String componentCode;

    @Schema(description = "Override type - VALUE or FORMULA", example = "VALUE", allowableValues = {"VALUE", "FORMULA"})
    private String overrideType;

    @Schema(description = "Override value (for VALUE type)", example = "50000.00")
    private BigDecimal overrideValue;

    @Schema(description = "Override formula (for FORMULA type)", example = "CTC * 0.5")
    private String overrideFormula;

    @Schema(description = "Is enabled", example = "true")
    private Boolean isEnabled;

    // Helper method for safe boolean access
    public boolean isEnabled() {
        return isEnabled != null && isEnabled;
    }
}