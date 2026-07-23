// dto/subscription/SubscriptionHistoryDTO.java
package com.sonixhr.dto.subscription;

import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TriggerSource;
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
public class SubscriptionHistoryDTO {

    // =====================================================
    // BASIC IDENTIFICATION
    // =====================================================

    private Long id;
    private Long tenantId;
    private String subscriptionId;

    // Plan identification
    private Long planId;
    private String planCode;
    private String planName;
    private String planType; // Plan type as string (e.g., "BASIC", "PREMIUM")

    // Employee information
    private Long employeeId;
    private String employeeName;
    private String employeeEmail;

    // =====================================================
    // STATUS AND EVENT
    // =====================================================

    private PlanStatus planStatus;
    private String eventType;
    private LocalDateTime eventDate;

    // =====================================================
    // DATE RANGES
    // =====================================================

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    // =====================================================
    // FINANCIAL INFORMATION
    // =====================================================

    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String transactionId;
    private String invoiceNumber;

    // =====================================================
    // REASON AND NOTES
    // =====================================================

    private String reason;
    private String notes;

    // =====================================================
    // PLAN CHANGE TRACKING - BEFORE VALUES
    // =====================================================

    private Long previousPlanId;
    private String previousPlanCode;
    private String previousPlanName;
    private BigDecimal previousPrice;
    private Integer previousMaxEmployees;
    private Integer previousValidityMonths;
    private String previousPlanType;

    // =====================================================
    // PLAN CHANGE TRACKING - AFTER VALUES
    // =====================================================

    private BigDecimal newPrice;
    private Integer newMaxEmployees;
    private Integer newValidityMonths;
    private String newPlanType;

    // =====================================================
    // STATUS CHANGE TRACKING
    // =====================================================

    private PlanStatus previousStatus;
    private PlanStatus newStatus;

    // =====================================================
    // SUBSCRIPTION FIELDS
    // =====================================================

    private Boolean isAutoRenew;
    private Integer gracePeriodDays;
    private Integer daysRemaining;
    private String cancellationType;

    // =====================================================
    // DETAILED CHANGE TRACKING
    // =====================================================

    private String fieldChanged;
    private String oldValue;
    private String newValue;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================

    private TriggerSource triggerSource;
    private Long triggeredById;
    private String metadata;
    private LocalDateTime createdAt;
    private String createdBy;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Check if this is an upgrade event
     */
    public boolean isUpgrade() {
        return "UPGRADED".equals(eventType) || "PLAN_UPGRADED".equals(eventType);
    }

    /**
     * Check if this is a downgrade event
     */
    public boolean isDowngrade() {
        return "DOWNGRADED".equals(eventType) || "PLAN_DOWNGRADED".equals(eventType);
    }

    /**
     * Check if this is a renewal event
     */
    public boolean isRenewal() {
        return "RENEWED".equals(eventType);
    }

    /**
     * Check if this is a cancellation event
     */
    public boolean isCancellation() {
        return "CANCELLED".equals(eventType);
    }

    /**
     * Check if this is an expiration event
     */
    public boolean isExpired() {
        return "EXPIRED".equals(eventType);
    }

    /**
     * Check if this is a plan created event
     */
    public boolean isPlanCreated() {
        return "PLAN_CREATED".equals(eventType);
    }

    /**
     * Check if this is a plan updated event
     */
    public boolean isPlanUpdated() {
        return "PLAN_UPDATED".equals(eventType);
    }

    /**
     * Check if this is a plan deleted event
     */
    public boolean isPlanDeleted() {
        return "PLAN_DELETED".equals(eventType);
    }

    /**
     * Check if this is a plan restored event
     */
    public boolean isPlanRestored() {
        return "PLAN_RESTORED".equals(eventType);
    }

    /**
     * Check if this is a plan toggled event
     */
    public boolean isPlanToggled() {
        return "PLAN_TOGGLED".equals(eventType);
    }

    /**
     * Check if this is a subscription activated event
     */
    public boolean isSubscriptionActivated() {
        return "ACTIVATED".equals(eventType);
    }

    /**
     * Check if this is a subscription reactivated event
     */
    public boolean isSubscriptionReactivated() {
        return "REACTIVATED".equals(eventType);
    }

    /**
     * Get the display name for the event type
     */
    public String getEventTypeDisplayName() {
        if (eventType == null) return "Unknown";
        return switch (eventType) {
            case "PLAN_CREATED" -> "Plan Created";
            case "PLAN_UPDATED" -> "Plan Updated";
            case "PLAN_DELETED" -> "Plan Deleted";
            case "PLAN_RESTORED" -> "Plan Restored";
            case "PLAN_TOGGLED" -> "Plan Toggled";
            case "PLAN_UPGRADED" -> "Plan Upgraded";
            case "PLAN_DOWNGRADED" -> "Plan Downgraded";
            case "ACTIVATED" -> "Subscription Activated";
            case "RENEWED" -> "Subscription Renewed";
            case "CANCELLED" -> "Subscription Cancelled";
            case "EXPIRED" -> "Subscription Expired";
            case "REACTIVATED" -> "Subscription Reactivated";
            case "SUSPENDED" -> "Subscription Suspended";
            case "PAUSED" -> "Subscription Paused";
            case "RESUMED" -> "Subscription Resumed";
            default -> eventType;
        };
    }

    /**
     * Get the color for the event type (for UI)
     */
    public String getEventTypeColor() {
        if (eventType == null) return "secondary";
        return switch (eventType) {
            case "PLAN_CREATED", "ACTIVATED", "REACTIVATED", "RENEWED" -> "success";
            case "PLAN_UPDATED", "PLAN_TOGGLED", "RESUMED" -> "info";
            case "PLAN_DELETED", "CANCELLED", "EXPIRED", "SUSPENDED" -> "danger";
            case "PLAN_RESTORED" -> "warning";
            case "PLAN_UPGRADED" -> "primary";
            case "PLAN_DOWNGRADED" -> "secondary";
            case "PAUSED" -> "warning";
            default -> "secondary";
        };
    }

    /**
     * Get the icon for the event type (for UI)
     */
    public String getEventTypeIcon() {
        if (eventType == null) return "fa-info-circle";
        return switch (eventType) {
            case "PLAN_CREATED", "ACTIVATED" -> "fa-plus-circle";
            case "PLAN_UPDATED" -> "fa-edit";
            case "PLAN_DELETED", "CANCELLED" -> "fa-trash";
            case "PLAN_RESTORED" -> "fa-undo";
            case "PLAN_TOGGLED" -> "fa-toggle-on";
            case "PLAN_UPGRADED" -> "fa-arrow-up";
            case "PLAN_DOWNGRADED" -> "fa-arrow-down";
            case "RENEWED" -> "fa-sync";
            case "EXPIRED" -> "fa-clock";
            case "REACTIVATED" -> "fa-play";
            case "SUSPENDED" -> "fa-pause";
            case "PAUSED" -> "fa-pause-circle";
            case "RESUMED" -> "fa-play-circle";
            default -> "fa-info-circle";
        };
    }

    /**
     * Check if there was a price change
     */
    public boolean hasPriceChanged() {
        return previousPrice != null && newPrice != null &&
                previousPrice.compareTo(newPrice) != 0;
    }

    /**
     * Get the price difference
     */
    public BigDecimal getPriceDifference() {
        if (previousPrice == null || newPrice == null) {
            return BigDecimal.ZERO;
        }
        return newPrice.subtract(previousPrice);
    }

    /**
     * Get the price difference as percentage
     */
    public BigDecimal getPriceChangePercentage() {
        if (previousPrice == null || newPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = newPrice.subtract(previousPrice);
        return diff.divide(previousPrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Check if there was a change in max employees
     */
    public boolean hasMaxEmployeesChanged() {
        return previousMaxEmployees != null && newMaxEmployees != null &&
                !previousMaxEmployees.equals(newMaxEmployees);
    }

    /**
     * Get the change in max employees
     */
    public int getMaxEmployeesDifference() {
        if (previousMaxEmployees == null || newMaxEmployees == null) {
            return 0;
        }
        return newMaxEmployees - previousMaxEmployees;
    }

    /**
     * Check if this is a plan operation (vs subscription operation)
     */
    public boolean isPlanOperation() {
        return eventType != null && eventType.startsWith("PLAN_");
    }

    /**
     * Check if this is a subscription operation
     */
    public boolean isSubscriptionOperation() {
        return eventType != null && !eventType.startsWith("PLAN_");
    }

    /**
     * Get formatted change description
     */
    public String getChangeDescription() {
        if (fieldChanged == null) {
            return getEventTypeDisplayName();
        }

        StringBuilder description = new StringBuilder();
        description.append(fieldChanged);

        if (oldValue != null && newValue != null) {
            description.append(": ");
            description.append(oldValue);
            description.append(" → ");
            description.append(newValue);
        }

        return description.toString();
    }

    /**
     * Get the plan display name
     */
    public String getPlanDisplayName() {
        if (planName != null) return planName;
        if (planCode != null) return planCode;
        if (planType != null) return planType;
        return "Unknown Plan";
    }

    /**
     * Get the previous plan display name
     */
    public String getPreviousPlanDisplayName() {
        if (previousPlanName != null) return previousPlanName;
        if (previousPlanCode != null) return previousPlanCode;
        if (previousPlanType != null) return previousPlanType;
        return "Unknown";
    }

    /**
     * Check if this event has changes
     */
    public boolean hasChanges() {
        return fieldChanged != null ||
                hasPriceChanged() ||
                hasMaxEmployeesChanged() ||
                previousPlanId != null && !previousPlanId.equals(planId);
    }

    /**
     * Get a summary of the event
     */
    public String getEventSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(getEventTypeDisplayName());

        if (hasChanges()) {
            summary.append(" - ");
            if (hasPriceChanged()) {
                summary.append("Price: $").append(previousPrice).append(" → $").append(newPrice);
            }
            if (hasMaxEmployeesChanged()) {
                if (hasPriceChanged()) summary.append(", ");
                summary.append("Employees: ").append(previousMaxEmployees).append(" → ").append(newMaxEmployees);
            }
        }

        return summary.toString();
    }

    /**
     * Get formatted amount with currency
     */
    public String getFormattedAmount() {
        if (amount == null) return "0.00";
        return currency != null ? currency + " " + amount : "$" + amount;
    }

    /**
     * Get formatted previous amount with currency
     */
    public String getFormattedPreviousPrice() {
        if (previousPrice == null) return "0.00";
        return currency != null ? currency + " " + previousPrice : "$" + previousPrice;
    }

    /**
     * Get formatted new amount with currency
     */
    public String getFormattedNewPrice() {
        if (newPrice == null) return "0.00";
        return currency != null ? currency + " " + newPrice : "$" + newPrice;
    }
}