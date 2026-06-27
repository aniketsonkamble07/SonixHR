package com.sonixhr.entity.tenant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.enums.BillingCycle;
import com.sonixhr.enums.PlanStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_subscriptions", indexes = {
        @Index(name = "idx_subscriptions_tenant", columnList = "tenant_id"),
        @Index(name = "idx_subscriptions_plan", columnList = "subscription_plan_id"),
        @Index(name = "idx_subscriptions_status", columnList = "plan_status"),
        @Index(name = "idx_subscriptions_active", columnList = "is_active")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // RELATIONSHIP
    // =====================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // =====================================================
    // PLAN DETAILS
    // =====================================================

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = true)
    private SubscriptionPlan subscriptionPlan;

    public String getPlanType() {
        return this.subscriptionPlan != null ? this.subscriptionPlan.getCode() : null;
    }

    @Column(name = "plan_name", length = 100)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", nullable = false, length = 20)
    @Builder.Default
    private PlanStatus planStatus = PlanStatus.ACTIVE;

    // =====================================================
    // LIMITS
    // =====================================================

    @Positive
    @Column(name = "max_employees")
    @Builder.Default
    private Integer maxEmployees = 100;

    @Positive
    @Column(name = "max_storage_mb")
    @Builder.Default
    private Integer maxStorageMb = 1024;

    // =====================================================
    // SUBSCRIPTION DATES
    // =====================================================

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // =====================================================
    // BILLING
    // =====================================================

    @DecimalMin(value = "0.0", inclusive = true)
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 20)
    @Builder.Default
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_provider_id", length = 255)
    private String paymentProviderId;

    // =====================================================
    // STATUS
    // =====================================================

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // =====================================================
    // AUDIT TIMESTAMPS
    // =====================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // OPTIMISTIC LOCKING
    // =====================================================

    @Version
    private Long version;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public boolean isTrial() {
        return this.subscriptionPlan != null && this.subscriptionPlan.isTrial();
    }

    public boolean isExpired() {
        return endsAt != null &&
                endsAt.isBefore(LocalDateTime.now());
    }

    public void activate() {
        this.planStatus = PlanStatus.ACTIVE;
        this.isActive = true;
        this.startedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.planStatus = PlanStatus.CANCELLED;
        this.isActive = false;
        this.cancelledAt = LocalDateTime.now();
    }

    public void suspend() {
        this.planStatus = PlanStatus.SUSPENDED;
        this.isActive = false;
    }

    public void expire() {
        this.isActive = false;
    }

    public void upgradePlan(SubscriptionPlan newPlan) {
        this.subscriptionPlan = newPlan;
    }
}