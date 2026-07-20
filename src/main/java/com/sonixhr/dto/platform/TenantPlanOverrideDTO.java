package com.sonixhr.dto.platform;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPlanOverrideDTO {

    @NotNull(message = "Plan type is required")
    private String planType;

    @NotNull(message = "Max employees limit is required")
    @Positive(message = "Max employees must be a positive number")
    private Integer maxEmployees;
}
