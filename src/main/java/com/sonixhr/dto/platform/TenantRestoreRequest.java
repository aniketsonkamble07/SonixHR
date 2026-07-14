package com.sonixhr.dto.platform;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRestoreRequest {

    @NotNull(message = "Plan ID is required")
    private Long planId;

    private String notes;
}
