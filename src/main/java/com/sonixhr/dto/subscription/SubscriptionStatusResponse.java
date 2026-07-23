// dto/subscription/SubscriptionStatusResponse.java
package com.sonixhr.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponse {
    private boolean active;
    private String status;
    private String message;
    private boolean canAccessBilling;
    private boolean canAccessSupport;
    private String allowedActions;
}