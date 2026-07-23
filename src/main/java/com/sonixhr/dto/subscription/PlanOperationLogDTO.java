// dto/subscription/PlanOperationLogDTO.java
package com.sonixhr.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanOperationLogDTO {
    private Long id;
    private Long tenantId;
    private Long planId;
    private String planCode;
    private String planName;
    private String eventType;
    private LocalDateTime eventDate;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private String notes;

    // Before/After values
    private Long previousPlanId;
    private String previousPlanCode;
    private String previousPlanName;
    private BigDecimal previousPrice;
    private BigDecimal newPrice;
    private Integer previousMaxEmployees;
    private Integer newMaxEmployees;
    private Integer previousValidityMonths;
    private Integer newValidityMonths;
    private Boolean previousIsActive;
    private Boolean newIsActive;

    // Change tracking
    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private Map<String, Object> changes;

    // Audit fields
    private String triggerSource;
    private Long triggeredById;
    private String createdBy;
    private LocalDateTime createdAt;
}