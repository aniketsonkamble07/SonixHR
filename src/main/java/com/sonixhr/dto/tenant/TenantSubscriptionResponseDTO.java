package com.sonixhr.dto.tenant;

import com.sonixhr.enums.BillingCycle;
import com.sonixhr.enums.PlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscriptionResponseDTO {
    private Long id;
    private String planType;
    private String planName;
    private PlanStatus planStatus;
    private int maxEmployees;
    private int maxStorageMb;
    private LocalDateTime trialStartedAt;
    private LocalDateTime trialEndsAt;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private BigDecimal amount;
    private String currency;
    private BillingCycle billingCycle;
    private boolean isActive;
    private LocalDateTime createdAt;
}
