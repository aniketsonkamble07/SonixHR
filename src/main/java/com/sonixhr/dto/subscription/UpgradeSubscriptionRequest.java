package com.sonixhr.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeSubscriptionRequest {

    @NotBlank(message = "Plan type is required")
    private String planType;  // Will receive "STANDARD", "BASIC", "PRO", "PREMIUM"

    private Long tenantId;  // Optional - if not provided, get from security context
}
