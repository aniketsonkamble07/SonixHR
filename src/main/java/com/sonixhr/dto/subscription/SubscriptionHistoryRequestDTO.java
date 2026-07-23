// dto/subscription/SubscriptionHistoryRequestDTO.java
package com.sonixhr.dto.subscription;

import com.sonixhr.enums.PlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionHistoryRequestDTO {

    // Date range filters
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    // Status filters (using PlanStatus enum)
    private PlanStatus status;

    // Plan filters (using String instead of SubscriptionPlan entity)
    private String planCode;
    private String planName;
    private Long planId;

    // Alias for planCode - used in service layer
    private String planType;

    // Event type filters
    private String eventType;
    private String eventTypeCategory; // PLAN, SUBSCRIPTION, ALL

    // Search and text filters
    private String searchTerm;
    private String reason;
    private String notes;

    // Subscription specific filters
    private Boolean isAutoRenew;
    private String cancellationType;

    // Amount filters
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    // Employee filters
    private Long employeeId;
    private String employeeName;
    private String employeeEmail;

    // Trigger source filters
    private String triggerSource; // USER, SYSTEM, ADMIN

    // Additional filters
    private String createdBy;
    private Boolean hasPriceChange;
    private Boolean hasFeatureChange;
    private Boolean isDeleted;
    private Boolean isActive;

    // Sorting and pagination (handled by Pageable)
    private String sortBy;
    private String sortDirection; // ASC, DESC

    // =====================================================
    // HELPER METHODS FOR VALIDATION
    // =====================================================

    /**
     * Get plan type (alias for planCode)
     */
    public String getPlanType() {
        return planType != null ? planType : planCode;
    }

    /**
     * Check if any filters are applied
     */
    public boolean hasFilters() {
        return startDate != null ||
                endDate != null ||
                status != null ||
                planCode != null ||
                planName != null ||
                planId != null ||
                planType != null ||
                eventType != null ||
                searchTerm != null ||
                reason != null ||
                notes != null ||
                isAutoRenew != null ||
                cancellationType != null ||
                minAmount != null ||
                maxAmount != null ||
                employeeId != null ||
                triggerSource != null ||
                createdBy != null ||
                hasPriceChange != null ||
                hasFeatureChange != null ||
                isDeleted != null ||
                isActive != null;
    }

    /**
     * Check if this is a plan operation query
     */
    public boolean isPlanOperationQuery() {
        return "PLAN".equalsIgnoreCase(eventTypeCategory) ||
                (eventType != null && eventType.startsWith("PLAN_"));
    }

    /**
     * Check if this is a subscription operation query
     */
    public boolean isSubscriptionOperationQuery() {
        return "SUBSCRIPTION".equalsIgnoreCase(eventTypeCategory) ||
                (eventType != null && !eventType.startsWith("PLAN_"));
    }

    /**
     * Validate date range
     */
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !startDate.isAfter(endDate);
    }

    /**
     * Validate amount range
     */
    public boolean isValidAmountRange() {
        if (minAmount == null || maxAmount == null) {
            return true;
        }
        return minAmount.compareTo(maxAmount) <= 0;
    }

    /**
     * Get the effective event type category
     */
    public String getEffectiveEventTypeCategory() {
        if (eventTypeCategory != null && !eventTypeCategory.isEmpty()) {
            return eventTypeCategory.toUpperCase();
        }
        if (eventType != null) {
            return eventType.startsWith("PLAN_") ? "PLAN" : "SUBSCRIPTION";
        }
        return "ALL";
    }

    /**
     * Build search query for repository
     */
    public String buildSearchPattern() {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return null;
        }
        return "%" + searchTerm.trim().toLowerCase() + "%";
    }

    // =====================================================
    // BUILDER WITH DEFAULT VALUES
    // =====================================================

    public static class SubscriptionHistoryRequestDTOBuilder {
        // Custom builder methods can be added here if needed
    }

    /**
     * Create a default request with no filters
     */
    public static SubscriptionHistoryRequestDTO empty() {
        return SubscriptionHistoryRequestDTO.builder().build();
    }

    /**
     * Create a request for plan operations only
     */
    public static SubscriptionHistoryRequestDTO forPlanOperations() {
        return SubscriptionHistoryRequestDTO.builder()
                .eventTypeCategory("PLAN")
                .build();
    }

    /**
     * Create a request for subscription operations only
     */
    public static SubscriptionHistoryRequestDTO forSubscriptionOperations() {
        return SubscriptionHistoryRequestDTO.builder()
                .eventTypeCategory("SUBSCRIPTION")
                .build();
    }

    /**
     * Create a request for a specific plan
     */
    public static SubscriptionHistoryRequestDTO forPlan(Long planId) {
        return SubscriptionHistoryRequestDTO.builder()
                .planId(planId)
                .eventTypeCategory("PLAN")
                .build();
    }

    /**
     * Create a request for a specific subscription
     */
    public static SubscriptionHistoryRequestDTO forSubscription(Long tenantId, String subscriptionId) {
        return SubscriptionHistoryRequestDTO.builder()
                .eventTypeCategory("SUBSCRIPTION")
                .build();
    }

    // =====================================================
    // VALIDATION METHODS
    // =====================================================

    /**
     * Validate all filters
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();

        if (!isValidDateRange()) {
            result.addError("startDate", "Start date must be before end date");
        }

        if (!isValidAmountRange()) {
            result.addError("minAmount", "Minimum amount must be less than or equal to maximum amount");
        }

        if (status != null && !isValidPlanStatus(status)) {
            result.addError("status", "Invalid plan status");
        }

        if (triggerSource != null && !isValidTriggerSource(triggerSource)) {
            result.addError("triggerSource", "Invalid trigger source");
        }

        return result;
    }

    private boolean isValidPlanStatus(PlanStatus status) {
        return status != null;
    }

    private boolean isValidTriggerSource(String triggerSource) {
        return triggerSource == null ||
                triggerSource.equalsIgnoreCase("USER") ||
                triggerSource.equalsIgnoreCase("SYSTEM") ||
                triggerSource.equalsIgnoreCase("ADMIN");
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final Map<String, String> errors = new LinkedHashMap<>();
        private boolean valid = true;

        public void addError(String field, String message) {
            errors.put(field, message);
            valid = false;
        }

        public boolean isValid() {
            return valid;
        }

        public Map<String, String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            if (valid) {
                return null;
            }
            return String.join("; ", errors.values());
        }
    }
}