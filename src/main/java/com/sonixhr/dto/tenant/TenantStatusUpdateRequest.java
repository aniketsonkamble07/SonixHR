package com.sonixhr.dto.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(active|suspended|deleted)$",
            message = "Status must be 'active', 'suspended', or 'deleted'")
    private String status;

    private String reason;
}