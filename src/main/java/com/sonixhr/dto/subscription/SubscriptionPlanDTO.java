// dto/subscription/SubscriptionPlanDTO.java
package com.sonixhr.dto.subscription;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionPlanDTO {

    // =====================================================
    // BASIC IDENTIFICATION
    // =====================================================

    private Long id;

    @NotBlank(message = "Plan code is required")
    @Size(min = 2, max = 50, message = "Plan code must be between 2 and 50 characters")
    private String code;

    @NotBlank(message = "Plan name is required")
    @Size(min = 2, max = 100, message = "Plan name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    // =====================================================
    // PRICING
    // =====================================================

    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be zero or positive")
    @DecimalMax(value = "999999.99", message = "Price cannot exceed 999999.99")
    private BigDecimal price = BigDecimal.ZERO;

    @Min(value = 1, message = "Validity months must be at least 1")
    private int validityMonths;

    @Size(max = 3, message = "Currency code must be 3 characters")
    @Builder.Default
    private String currency = "USD";

    // =====================================================
    // LIMITS
    // =====================================================

    @Positive(message = "Max users must be positive")
    private Integer maxUsers;

    @PositiveOrZero(message = "Max employees must be zero or positive")
    private Integer maxEmployees;

    // =====================================================
    // STATUS FLAGS
    // =====================================================

    @JsonProperty("isActive")
    @JsonAlias({"isActive", "active"})
    @Builder.Default
    private boolean isActive = true;

    @JsonProperty("isPublic")
    @JsonAlias({"isPublic", "public"})
    @Builder.Default
    private boolean isPublic = true;

    @Builder.Default
    private Integer displayOrder = 0;

    @JsonProperty("isCustom")
    @JsonAlias({"isCustom", "custom"})
    @Builder.Default
    private boolean isCustom = false;

    // =====================================================
    // FEATURES
    // =====================================================

    @Builder.Default
    private Set<String> features = new HashSet<>();

    // =====================================================
    // AUDIT FIELDS
    // =====================================================

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // =====================================================
    // STATISTICAL FIELDS (for dashboard/reports)
    // =====================================================

    private Long totalSubscriptions;
    private Long activeSubscriptions;
    private BigDecimal totalRevenue;
    private BigDecimal averageRevenue;

    // =====================================================
    // CHANGE TRACKING FIELDS (for history)
    // =====================================================

    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private String changeDescription;
    private LocalDateTime changeDate;
    private String changedBy;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Check if plan is free
     */
    public boolean isFree() {
        return price != null && price.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if plan is active
     */
    public boolean isActivePlan() {
        return isActive;
    }

    /**
     * Check if plan is deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Get monthly equivalent price
     */
    public BigDecimal getMonthlyEquivalentPrice() {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        int months = validityMonths > 0 ? validityMonths : 1;
        if (months == 1) {
            return price;
        }
        return price.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    /**
     * Get yearly equivalent price
     */
    public BigDecimal getYearlyEquivalentPrice() {
        return getMonthlyEquivalentPrice().multiply(BigDecimal.valueOf(12));
    }

    /**
     * Get daily equivalent price
     */
    public BigDecimal getDailyEquivalentPrice() {
        BigDecimal monthly = getMonthlyEquivalentPrice();
        return monthly.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    }

    /**
     * Get price difference between this and another plan
     */
    public BigDecimal getPriceDifference(SubscriptionPlanDTO other) {
        if (other == null || other.getPrice() == null || this.price == null) {
            return BigDecimal.ZERO;
        }
        return this.price.subtract(other.getPrice());
    }

    /**
     * Get price change percentage between this and another plan
     */
    public BigDecimal getPriceChangePercentage(SubscriptionPlanDTO other) {
        if (other == null || other.getPrice() == null ||
                other.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (this.price == null) {
            return BigDecimal.valueOf(-100);
        }
        BigDecimal diff = this.price.subtract(other.getPrice());
        return diff.divide(other.getPrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Check if this plan has a specific feature
     */
    public boolean hasFeature(String featureCode) {
        if (featureCode == null || features == null) {
            return false;
        }
        return features.stream()
                .anyMatch(f -> f.equalsIgnoreCase(featureCode));
    }

    /**
     * Get the number of features
     */
    public int getFeatureCount() {
        return features != null ? features.size() : 0;
    }

    /**
     * Check if this plan is enterprise level
     */
    public boolean isEnterprise() {
        return (code != null && code.toUpperCase().contains("ENTERPRISE")) ||
                (name != null && name.toUpperCase().contains("ENTERPRISE"));
    }

    /**
     * Check if this plan is premium level
     */
    public boolean isPremium() {
        return (code != null && (code.toUpperCase().contains("PREMIUM") || code.toUpperCase().contains("PRO"))) ||
                (name != null && (name.toUpperCase().contains("PREMIUM") || name.toUpperCase().contains("PRO")));
    }

    /**
     * Get the plan tier/level
     */
    public String getPlanTier() {
        if (isFree()) return "FREE";
        if (isEnterprise()) return "ENTERPRISE";
        if (isPremium()) return "PREMIUM";
        if (code != null && code.toUpperCase().contains("BASIC")) return "BASIC";
        if (isCustom) return "CUSTOM";
        return "STANDARD";
    }

    /**
     * Get the feature level (1-5)
     */
    public int getFeatureLevel() {
        if (isFree()) return 1;
        if (code != null && code.toUpperCase().contains("BASIC")) return 2;
        if (code != null && code.toUpperCase().contains("STANDARD")) return 3;
        if (isPremium()) return 4;
        if (isEnterprise()) return 5;
        return 3;
    }

    /**
     * Check if this plan has higher features than another
     */
    public boolean hasHigherFeaturesThan(SubscriptionPlanDTO other) {
        if (other == null) return true;
        return this.getFeatureLevel() > other.getFeatureLevel();
    }

    /**
     * Get display name with price
     */
    public String getDisplayNameWithPrice() {
        String display = name != null ? name : code;
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            display += " ($" + price + "/" + (validityMonths > 0 ? validityMonths + "mo" : "mo") + ")";
        } else if (price != null && price.compareTo(BigDecimal.ZERO) == 0) {
            display += " (Free)";
        }
        return display;
    }

    /**
     * Get short display name
     */
    public String getShortDisplayName() {
        if (code != null && !code.isEmpty()) {
            return code;
        }
        return name != null ? name : "Plan";
    }

    /**
     * Get status badge color
     */
    public String getStatusBadgeColor() {
        if (deletedAt != null) return "danger";
        if (!isActive) return "secondary";
        if (isPublic) return "success";
        return "warning";
    }

    /**
     * Get status display text
     */
    public String getStatusDisplay() {
        if (deletedAt != null) return "Deleted";
        if (!isActive) return "Inactive";
        if (isPublic) return "Active";
        return "Private";
    }

    /**
     * Create a copy of this DTO
     */
    public SubscriptionPlanDTO copy() {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setId(id);
        dto.setCode(code);
        dto.setName(name);
        dto.setDescription(description);
        dto.setPrice(price);
        dto.setCurrency(currency);
        dto.setValidityMonths(validityMonths);
        dto.setMaxUsers(maxUsers);
        dto.setMaxEmployees(maxEmployees);
        dto.setActive(isActive);
        dto.setPublic(isPublic);
        dto.setDisplayOrder(displayOrder);
        dto.setCustom(isCustom);
        dto.setFeatures(features != null ? new HashSet<>(features) : new HashSet<>());
        dto.setCreatedAt(createdAt);
        dto.setUpdatedAt(updatedAt);
        dto.setDeletedAt(deletedAt);
        dto.setTotalSubscriptions(totalSubscriptions);
        dto.setActiveSubscriptions(activeSubscriptions);
        dto.setTotalRevenue(totalRevenue);
        dto.setAverageRevenue(averageRevenue);
        return dto;
    }

    /**
     * Create a summary of changes between two plans
     */
    public String getChangeSummary(SubscriptionPlanDTO oldPlan) {
        if (oldPlan == null) {
            return "Plan created: " + getDisplayNameWithPrice();
        }

        StringBuilder changes = new StringBuilder();

        if (!code.equals(oldPlan.getCode())) {
            changes.append("Code: ").append(oldPlan.getCode()).append(" → ").append(code).append("; ");
        }
        if (!name.equals(oldPlan.getName())) {
            changes.append("Name: ").append(oldPlan.getName()).append(" → ").append(name).append("; ");
        }
        if (price.compareTo(oldPlan.getPrice()) != 0) {
            changes.append("Price: $").append(oldPlan.getPrice()).append(" → $").append(price).append("; ");
        }
        if (!Objects.equals(maxEmployees, oldPlan.getMaxEmployees())) {
            changes.append("Max Employees: ").append(oldPlan.getMaxEmployees())
                    .append(" → ").append(maxEmployees).append("; ");
        }
        if (isActive != oldPlan.isActive()) {
            changes.append("Status: ").append(oldPlan.isActive() ? "Active" : "Inactive")
                    .append(" → ").append(isActive ? "Active" : "Inactive").append("; ");
        }
        if (!Objects.equals(features, oldPlan.getFeatures())) {
            Set<String> added = new HashSet<>(features);
            added.removeAll(oldPlan.getFeatures());
            Set<String> removed = new HashSet<>(oldPlan.getFeatures());
            removed.removeAll(features);
            if (!added.isEmpty()) {
                changes.append("Added Features: ").append(added).append("; ");
            }
            if (!removed.isEmpty()) {
                changes.append("Removed Features: ").append(removed).append("; ");
            }
        }

        return changes.length() > 0 ? changes.toString() : "No changes detected";
    }

    /**
     * Get the features as a comma-separated string
     */
    public String getFeaturesAsString() {
        if (features == null || features.isEmpty()) {
            return "No features";
        }
        return String.join(", ", features);
    }

    /**
     * Get the features count display
     */
    public String getFeaturesCountDisplay() {
        int count = getFeatureCount();
        return count + " feature" + (count != 1 ? "s" : "");
    }

    /**
     * Check if this plan can be upgraded to another
     */
    public boolean canUpgradeTo(SubscriptionPlanDTO target) {
        if (target == null || target.equals(this)) return false;
        if (this.isEnterprise()) return false;
        if (target.isCustom()) return true;
        return this.getFeatureLevel() < target.getFeatureLevel();
    }

    /**
     * Check if this plan can be downgraded to another
     */
    public boolean canDowngradeTo(SubscriptionPlanDTO target) {
        if (target == null || target.equals(this)) return false;
        if (target.isFree()) return true;
        if (this.isCustom()) return true;
        if (target.isCustom()) return false;
        return this.getFeatureLevel() > target.getFeatureLevel();
    }

    public String getNextTier() {
        String tier = getPlanTier();
        if ("FREE".equals(tier)) return "BASIC";
        if ("BASIC".equals(tier)) return "STANDARD";
        if ("STANDARD".equals(tier)) return "PREMIUM";
        if ("PREMIUM".equals(tier)) return "ENTERPRISE";
        return null;
    }

    /**
     * Get the previous tier down
     */
    public String getPreviousTier() {
        String tier = getPlanTier();
        if ("ENTERPRISE".equals(tier)) return "PREMIUM";
        if ("PREMIUM".equals(tier)) return "STANDARD";
        if ("STANDARD".equals(tier)) return "BASIC";
        if ("BASIC".equals(tier)) return "FREE";
        return null;
    }

    // =====================================================
    // FACTORY METHODS
    // =====================================================

    /**
     * Create a free plan DTO
     */
    public static SubscriptionPlanDTO createFreePlan() {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setCode("FREE");
        dto.setName("Free Plan");
        dto.setDescription("Basic free plan with limited features");
        dto.setPrice(BigDecimal.ZERO);
        dto.setCurrency("USD");
        dto.setValidityMonths(1);
        dto.setMaxEmployees(5);
        dto.setActive(true);
        dto.setPublic(true);
        dto.setDisplayOrder(1);
        dto.setCustom(false);
        dto.setFeatures(new HashSet<>());
        return dto;
    }

    /**
     * Create a basic plan DTO
     */
    public static SubscriptionPlanDTO createBasicPlan() {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setCode("BASIC");
        dto.setName("Basic Plan");
        dto.setDescription("Essential features for small businesses");
        dto.setPrice(BigDecimal.valueOf(29));
        dto.setCurrency("USD");
        dto.setValidityMonths(1);
        dto.setMaxEmployees(25);
        dto.setActive(true);
        dto.setPublic(true);
        dto.setDisplayOrder(2);
        dto.setCustom(false);
        Set<String> features = new HashSet<>();
        features.add("BASIC_ACCESS");
        features.add("EMPLOYEE_MANAGEMENT");
        features.add("BASIC_ATTENDANCE");
        dto.setFeatures(features);
        return dto;
    }

    /**
     * Create a standard plan DTO
     */
    public static SubscriptionPlanDTO createStandardPlan() {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setCode("STANDARD");
        dto.setName("Standard Plan");
        dto.setDescription("Advanced features for growing businesses");
        dto.setPrice(BigDecimal.valueOf(49));
        dto.setCurrency("USD");
        dto.setValidityMonths(1);
        dto.setMaxEmployees(50);
        dto.setActive(true);
        dto.setPublic(true);
        dto.setDisplayOrder(3);
        dto.setCustom(false);
        Set<String> features = new HashSet<>();
        features.add("BASIC_ACCESS");
        features.add("EMPLOYEE_MANAGEMENT");
        features.add("ADVANCED_ATTENDANCE");
        features.add("LEAVE_MANAGEMENT");
        features.add("REPORTING");
        features.add("API_ACCESS");
        dto.setFeatures(features);
        return dto;
    }

    /**
     * Create a premium plan DTO
     */
    public static SubscriptionPlanDTO createPremiumPlan() {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setCode("PREMIUM");
        dto.setName("Premium Plan");
        dto.setDescription("Full feature set for medium businesses");
        dto.setPrice(BigDecimal.valueOf(79));
        dto.setCurrency("USD");
        dto.setValidityMonths(1);
        dto.setMaxEmployees(100);
        dto.setActive(true);
        dto.setPublic(true);
        dto.setDisplayOrder(4);
        dto.setCustom(false);
        Set<String> features = new HashSet<>();
        features.add("BASIC_ACCESS");
        features.add("EMPLOYEE_MANAGEMENT");
        features.add("ADVANCED_ATTENDANCE");
        features.add("PAYROLL");
        features.add("API_ACCESS");
        features.add("SHIFT_MANAGEMENT");
        features.add("PERFORMANCE_TRACKING");
        features.add("BULK_IMPORT_EXPORT");
        dto.setFeatures(features);
        return dto;
    }

    /**
     * Create an enterprise plan DTO
     */
    public static SubscriptionPlanDTO createEnterprisePlan() {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setCode("ENTERPRISE");
        dto.setName("Enterprise Plan");
        dto.setDescription("Complete solution for large organizations");
        dto.setPrice(BigDecimal.valueOf(199));
        dto.setCurrency("USD");
        dto.setValidityMonths(12);
        dto.setMaxEmployees(500);
        dto.setActive(true);
        dto.setPublic(true);
        dto.setDisplayOrder(5);
        dto.setCustom(false);
        Set<String> features = new HashSet<>();
        features.add("BASIC_ACCESS");
        features.add("EMPLOYEE_MANAGEMENT");
        features.add("ADVANCED_ATTENDANCE");
        features.add("PAYROLL");
        features.add("API_ACCESS");
        features.add("WHITE_LABEL");
        features.add("DEDICATED_SUPPORT");
        features.add("CUSTOM_INTEGRATION");
        features.add("UNLIMITED_EMPLOYEES");
        dto.setFeatures(features);
        return dto;
    }

    /**
     * Create a custom plan DTO
     */
    public static SubscriptionPlanDTO createCustomPlan(String code, String name, BigDecimal price, int maxEmployees) {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setCode(code);
        dto.setName(name);
        dto.setDescription("Custom plan tailored to specific needs");
        dto.setPrice(price);
        dto.setCurrency("USD");
        dto.setValidityMonths(1);
        dto.setMaxEmployees(maxEmployees);
        dto.setActive(true);
        dto.setPublic(false);
        dto.setDisplayOrder(99);
        dto.setCustom(true);
        dto.setFeatures(new HashSet<>());
        return dto;
    }

    // =====================================================
    // TO STRING METHOD
    // =====================================================

    @Override
    public String toString() {
        return String.format("SubscriptionPlanDTO{id=%d, code='%s', name='%s', price=%s, active=%s, features=%d}",
                id, code, name, price, isActive, getFeatureCount());
    }
}