package com.sonixhr.dto.tenant;



import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewSubscriptionRequest {

    @NotNull(message = "Subscription ID is required")
    private Long subscriptionId;

    private Long tenantId;  // Optional - if not provided, get from security context
}