package com.sonixhr.dto.subscription;

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
    private Integer maxEmployees;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private BigDecimal amount;
    private String currency;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime billingPeriodStart;
    private LocalDateTime billingPeriodEnd;
    private LocalDateTime gracePeriodEnd;
    private String cancellationReason;
    private Boolean cancelledAtEndOfPeriod;

    // Helper methods
    public boolean isExpired() {
        return planStatus == PlanStatus.EXPIRED || planStatus == PlanStatus.CANCELLED;
    }

    public boolean isActiveSubscription() {
        return planStatus == PlanStatus.ACTIVE && Boolean.TRUE.equals(isActive);
    }
}