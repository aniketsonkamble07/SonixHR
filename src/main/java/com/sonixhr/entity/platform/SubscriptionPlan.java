package com.sonixhr.entity.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.sonixhr.entity.tenant.TenantSubscription;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "subscription_plans", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_name", columnNames = "name"),
        @UniqueConstraint(name = "uk_plan_code", columnNames = "code")
}, indexes = {
        @Index(name = "idx_plan_is_active", columnList = "is_active"),
        @Index(name = "idx_plan_price", columnList = "price"),
        @Index(name = "idx_plan_code", columnList = "code")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // BASIC PLAN INFORMATION
    // =====================================================

    @NotBlank(message = "Plan code cannot be blank")
    @Size(min = 2, max = 50, message = "Plan code must be between 2 and 50 characters")
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank(message = "Plan name cannot be blank")
    @Size(min = 2, max = 100, message = "Plan name must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 500, message = "Plan description cannot exceed 500 characters")
    @Column(length = 500)
    private String description;

    // =====================================================
    // PRICING
    // =====================================================

    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be zero or positive")
    @DecimalMax(value = "999999.99", message = "Price cannot exceed 999999.99")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @NotNull(message = "Validity months cannot be null")
    @Positive(message = "Validity months must be positive")
    @Column(name = "validity_months", nullable = false)
    @Builder.Default
    private Integer validityMonths = 1;

    @Size(max = 3, message = "Currency code must be 3 characters")
    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    // =====================================================
    // PLAN FEATURES
    // =====================================================

    @Column(name = "max_users")
    @Positive(message = "Max users must be positive")
    private Integer maxUsers;

    @Column(name = "max_employees")
    @PositiveOrZero(message = "Max employees must be zero or positive")
    private Integer maxEmployees;

    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private Boolean isCustom = false;

    // =====================================================
    // STATUS
    // =====================================================

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    // =====================================================
    // AUDIT
    // =====================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // =====================================================
    // RELATIONSHIPS
    // =====================================================

    @JsonIgnore
    @OneToMany(mappedBy = "subscriptionPlan", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TenantSubscription> subscriptions = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "subscriptionPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<PlanFeature> planFeatures = new HashSet<>();

    // =====================================================
    // FEATURE MANAGEMENT METHODS
    // =====================================================

    /**
     * Check if the plan has a specific feature
     */
    public boolean hasFeature(String featureCode) {
        if (featureCode == null || this.planFeatures == null) {
            return false;
        }
        return this.planFeatures.stream()
                .filter(PlanFeature::isEnabled)
                .anyMatch(f -> featureCode.equalsIgnoreCase(f.getFeatureCode()));
    }

    /**
     * Get all enabled feature codes
     */
    public Set<String> getEnabledFeatureCodes() {
        if (this.planFeatures == null) {
            return new HashSet<>();
        }
        return this.planFeatures.stream()
                .filter(PlanFeature::isEnabled)
                .map(PlanFeature::getFeatureCode)
                .collect(Collectors.toSet());
    }

    /**
     * Get all feature codes (including disabled)
     */
    public Set<String> getAllFeatureCodes() {
        if (this.planFeatures == null) {
            return new HashSet<>();
        }
        return this.planFeatures.stream()
                .map(PlanFeature::getFeatureCode)
                .collect(Collectors.toSet());
    }

    /**
     * Add a feature to the plan
     */
    public void addFeature(String featureCode, String description) {
        if (this.planFeatures == null) {
            this.planFeatures = new HashSet<>();
        }

        // Check if feature already exists
        boolean exists = this.planFeatures.stream()
                .anyMatch(f -> f.getFeatureCode().equalsIgnoreCase(featureCode));

        if (!exists) {
            PlanFeature feature = PlanFeature.builder()
                    .subscriptionPlan(this)
                    .featureCode(featureCode)
                    .description(description)
                    .isEnabled(true)
                    .build();
            this.planFeatures.add(feature);
        }
    }

    /**
     * Remove a feature from the plan
     */
    public void removeFeature(String featureCode) {
        if (this.planFeatures == null) {
            return;
        }
        this.planFeatures.removeIf(f -> f.getFeatureCode().equalsIgnoreCase(featureCode));
    }

    /**
     * Enable a feature
     */
    public void enableFeature(String featureCode) {
        if (this.planFeatures == null) {
            return;
        }
        this.planFeatures.stream()
                .filter(f -> f.getFeatureCode().equalsIgnoreCase(featureCode))
                .findFirst()
                .ifPresent(PlanFeature::enable);
    }

    /**
     * Disable a feature
     */
    public void disableFeature(String featureCode) {
        if (this.planFeatures == null) {
            return;
        }
        this.planFeatures.stream()
                .filter(f -> f.getFeatureCode().equalsIgnoreCase(featureCode))
                .findFirst()
                .ifPresent(PlanFeature::disable);
    }

    /**
     * Replace all features with a new set
     */
    public void setFeatures(Set<String> featureCodes) {
        if (this.planFeatures == null) {
            this.planFeatures = new HashSet<>();
        }

        // Clear existing features
        this.planFeatures.clear();

        // Add new features
        if (featureCodes != null) {
            for (String featureCode : featureCodes) {
                PlanFeature feature = PlanFeature.builder()
                        .subscriptionPlan(this)
                        .featureCode(featureCode)
                        .isEnabled(true)
                        .build();
                this.planFeatures.add(feature);
            }
        }
    }

    /**
     * Get features as a list of strings (for DTO conversion)
     */
    @Transient
    public Set<String> getFeatures() {
        return getEnabledFeatureCodes();
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public boolean isFree() {
        return this.price != null && this.price.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.isActive = false;
    }

    public void restore() {
        this.deletedAt = null;
        this.isActive = true;
    }

    @Transient
    public BigDecimal getMonthlyEquivalentPrice() {
        if (this.price == null) {
            return BigDecimal.ZERO;
        }
        int months = (this.validityMonths == null || this.validityMonths <= 0) ? 1 : this.validityMonths;
        if (months == 1) {
            return this.price;
        }
        return this.price.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    // =====================================================
    // VALIDATION
    // =====================================================

    @PrePersist
    @PreUpdate
    private void validatePlan() {
        // Set defaults
        if (this.validityMonths == null || this.validityMonths <= 0) {
            this.validityMonths = 1;
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.isPublic == null) {
            this.isPublic = true;
        }
        if (this.currency == null) {
            this.currency = "USD";
        }
        if (this.displayOrder == null) {
            this.displayOrder = 0;
        }
        if (this.isCustom == null) {
            this.isCustom = false;
        }
        if (this.price == null) {
            this.price = BigDecimal.ZERO;
        }
        this.price = this.price.setScale(2, RoundingMode.HALF_UP);

        // Generate code from name if code is not set
        if (this.code == null || this.code.trim().isEmpty()) {
            this.code = generateCodeFromName(this.name);
        }
    }

    private String generateCodeFromName(String name) {
        if (name == null) return "PLAN_" + System.currentTimeMillis();
        return name.toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    // =====================================================
    // GETTERS AND SETTERS
    // =====================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getValidityMonths() {
        return validityMonths;
    }

    public void setValidityMonths(Integer validityMonths) {
        this.validityMonths = validityMonths;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Integer getMaxEmployees() {
        return maxEmployees;
    }

    public void setMaxEmployees(Integer maxEmployees) {
        this.maxEmployees = maxEmployees;
    }

    public Boolean getIsCustom() {
        return isCustom;
    }

    public void setIsCustom(Boolean isCustom) {
        this.isCustom = isCustom;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Set<TenantSubscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Set<TenantSubscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Set<PlanFeature> getPlanFeatures() {
        return planFeatures;
    }

    public void setPlanFeatures(Set<PlanFeature> planFeatures) {
        this.planFeatures = planFeatures;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SubscriptionPlan that))
            return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return String.format("SubscriptionPlan{id=%d, code='%s', name='%s', price=%s, active=%s, features=%d}",
                id, code, name, price, isActive,
                planFeatures != null ? planFeatures.size() : 0);
    }
}