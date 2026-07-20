package com.sonixhr.entity.tenant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.CancellationReason;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_subscriptions", indexes = {
        @Index(name = "idx_subscriptions_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_subscriptions_plan_id", columnList = "subscription_plan_id"),
        @Index(name = "idx_subscriptions_status", columnList = "plan_status"),
        @Index(name = "idx_subscriptions_is_active", columnList = "is_active"),
        @Index(name = "idx_subscriptions_ends_at", columnList = "ends_at"),
        @Index(name = "idx_subscriptions_tenant_status", columnList = "tenant_id, plan_status"),
        @Index(name = "idx_sub_billing_period_end", columnList = "billing_period_end"),
        @Index(name = "idx_sub_is_current", columnList = "is_current"),
        @Index(name = "idx_sub_status_period_end", columnList = "plan_status, billing_period_end"),
        @Index(name = "idx_sub_original_id", columnList = "original_subscription_id")
})
@EntityListeners(AuditingEntityListener.class)
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
    // RELATIONSHIPS
    // =====================================================

    @NotNull(message = "Tenant cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subscription_tenant"))
    @JsonIgnore
    private Tenant tenant;

    @NotNull(message = "Subscription plan cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subscription_plan"))
    private SubscriptionPlan subscriptionPlan;

    // =====================================================
    // PLAN DETAILS
    // =====================================================

    @Column(name = "plan_name", length = 100)
    @Size(max = 100, message = "Plan name cannot exceed 100 characters")
    private String planName;

    @NotNull(message = "Plan status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", nullable = false, length = 20)
    @Builder.Default
    private PlanStatus planStatus = PlanStatus.ACTIVE;

    @Column(name = "max_employees")
    private Integer maxEmployees;

    // =====================================================
    // SUBSCRIPTION DATES
    // =====================================================

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * @deprecated Use {@link #getBillingPeriodEnd()} instead.
     */
    @Column(name = "ends_at")
    @Deprecated
    private LocalDateTime endsAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDateTime billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDateTime billingPeriodEnd;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "grace_period_end")
    private LocalDateTime gracePeriodEnd;

    // =====================================================
    // BILLING INFORMATION
    // =====================================================

    @DecimalMin(value = "0.0", inclusive = true, message = "Amount must be zero or positive")
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "payment_method", length = 50)
    @Size(max = 50, message = "Payment method cannot exceed 50 characters")
    private String paymentMethod;

    @Column(name = "payment_provider_id", length = 255)
    @Size(max = 255, message = "Payment provider ID cannot exceed 255 characters")
    private String paymentProviderId;

    @Column(name = "payment_provider_customer_id", length = 255)
    @Size(max = 255, message = "Payment provider customer ID cannot exceed 255 characters")
    private String paymentProviderCustomerId;

    @Column(name = "payment_provider_subscription_id", length = 255)
    @Size(max = 255, message = "Payment provider subscription ID cannot exceed 255 characters")
    private String paymentProviderSubscriptionId;

    @Column(name = "last_payment_amount", precision = 10, scale = 2)
    private BigDecimal lastPaymentAmount;

    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;

    @Column(name = "next_payment_date")
    private LocalDateTime nextPaymentDate;

    // =====================================================
    // STATUS FLAGS
    // =====================================================

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = true;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    @Column(name = "payment_retry_count", nullable = false)
    @Builder.Default
    private Integer paymentRetryCount = 0;

    // =====================================================
    // CANCELLATION & REACTIVATION
    // =====================================================

    @Column(name = "cancellation_date")
    private LocalDateTime cancellationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 50)
    private CancellationReason cancellationReason;

    @Column(name = "cancelled_at_end_of_period", nullable = false)
    @Builder.Default
    private Boolean cancelledAtEndOfPeriod = false;

    @Column(name = "reactivation_date")
    private LocalDateTime reactivationDate;

    @Column(name = "previous_subscription_id")
    private Long previousSubscriptionId;

    @Column(name = "original_subscription_id")
    private Long originalSubscriptionId;

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
    @Column(name = "version")
    private Long version;

    // =====================================================
    // HELPER METHODS & WRAPPERS
    // =====================================================

    public PlanStatus getStatus() {
        return this.planStatus;
    }

    public void setStatus(PlanStatus status) {
        this.planStatus = status;
    }

    @Transient
    public String getPlanType() {
        return this.subscriptionPlan != null ? this.subscriptionPlan.getName() : null;
    }

    public boolean isExpired() {
        return billingPeriodEnd != null && billingPeriodEnd.isBefore(LocalDateTime.now());
    }

    public boolean isActiveSubscription() {
        return Boolean.TRUE.equals(isActive) &&
                planStatus == PlanStatus.ACTIVE &&
                !isExpired();
    }

    public boolean isAboutToExpire(int days) {
        return billingPeriodEnd != null &&
                billingPeriodEnd.isBefore(LocalDateTime.now().plusDays(days)) &&
                billingPeriodEnd.isAfter(LocalDateTime.now());
    }

    public long getDaysUntilExpiry() {
        if (billingPeriodEnd == null)
            return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), billingPeriodEnd);
    }

    // =====================================================
    // STATE MANAGEMENT METHODS
    // =====================================================

    public void activate() {
        this.planStatus = PlanStatus.ACTIVE;
        this.isActive = true;
        this.startedAt = LocalDateTime.now();
        this.billingPeriodStart = this.startedAt;
        if (this.subscriptionPlan != null) {
            int validity = (this.subscriptionPlan.getValidityMonths() != null
                    && this.subscriptionPlan.getValidityMonths() > 0)
                            ? this.subscriptionPlan.getValidityMonths()
                            : 1;
            this.billingPeriodEnd = this.startedAt.plusMonths(validity);
            this.endsAt = this.billingPeriodEnd;
            this.amount = this.subscriptionPlan.getPrice();
            this.planName = this.subscriptionPlan.getName();
            this.lastPaymentAmount = this.amount;
            this.lastPaymentDate = this.startedAt;
            this.nextPaymentDate = this.billingPeriodEnd;
        }
    }

    public void cancel() {
        this.planStatus = PlanStatus.CANCELLED;
        this.isActive = false;
        this.autoRenew = false;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationDate = this.cancelledAt;
    }

    public void suspend() {
        this.planStatus = PlanStatus.SUSPENDED;
        this.isActive = false;
    }

    public void expire() {
        this.planStatus = PlanStatus.EXPIRED;
        this.isActive = false;
        this.endedAt = LocalDateTime.now();
    }

    public void upgradePlan(SubscriptionPlan newPlan) {
        if (newPlan == null) {
            throw new IllegalArgumentException("New plan cannot be null");
        }
        if (this.subscriptionPlan != null && newPlan.getId().equals(this.subscriptionPlan.getId())) {
            return; // Same plan, no change needed
        }

        this.subscriptionPlan = newPlan;
        this.planName = newPlan.getName();
        this.amount = newPlan.getPrice();
        this.startedAt = LocalDateTime.now();
        this.billingPeriodStart = this.startedAt;
        int validity = (newPlan.getValidityMonths() != null && newPlan.getValidityMonths() > 0)
                ? newPlan.getValidityMonths()
                : 1;
        this.billingPeriodEnd = this.startedAt.plusMonths(validity);
        this.endsAt = this.billingPeriodEnd;
        this.planStatus = PlanStatus.ACTIVE;
        this.isActive = true;
        this.cancelledAt = null;
        this.lastPaymentAmount = this.amount;
        this.lastPaymentDate = this.startedAt;
        this.nextPaymentDate = this.billingPeriodEnd;
    }

    public void renew() {
        if (this.subscriptionPlan == null) {
            throw new IllegalStateException("Cannot renew subscription without a plan");
        }

        LocalDateTime now = LocalDateTime.now();
        int months = (this.subscriptionPlan.getValidityMonths() != null
                && this.subscriptionPlan.getValidityMonths() > 0)
                        ? this.subscriptionPlan.getValidityMonths()
                        : 1;

        if (this.billingPeriodEnd != null && this.billingPeriodEnd.isAfter(now)) {
            // Subscription still active - preserve remaining time and stack the new period
            this.billingPeriodEnd = this.billingPeriodEnd.plusMonths(months);
        } else {
            // Subscription already expired - start new period from now
            this.startedAt = now;
            this.billingPeriodStart = now;
            this.billingPeriodEnd = now.plusMonths(months);
        }
        this.endsAt = this.billingPeriodEnd;

        this.planStatus = PlanStatus.ACTIVE;
        this.isActive = true;
        this.cancelledAt = null;
        this.lastPaymentAmount = this.subscriptionPlan.getPrice();
        this.lastPaymentDate = now;
        this.nextPaymentDate = this.billingPeriodEnd;
    }

    public void pause() {
        this.planStatus = PlanStatus.PAUSED;
        this.isActive = false;
    }

    public void resume() {
        if (this.planStatus != PlanStatus.PAUSED && this.planStatus != PlanStatus.FROZEN) {
            throw new IllegalStateException("Only paused/frozen subscriptions can be resumed");
        }
        this.planStatus = PlanStatus.ACTIVE;
        this.isActive = true;
        // Extend end date by remaining days from pause period
        if (this.billingPeriodEnd != null) {
            this.billingPeriodEnd = this.billingPeriodEnd.plusMonths(1); // Add 1 month as grace period
            this.endsAt = this.billingPeriodEnd;
        }
    }

    // =====================================================
    // VALIDATION METHODS
    // =====================================================

    @PrePersist
    @PreUpdate
    private void validateSubscription() {
        // Validate dates
        if (billingPeriodStart != null && billingPeriodEnd != null && billingPeriodStart.isAfter(billingPeriodEnd)) {
            throw new IllegalStateException("Start date cannot be after end date");
        }

        // Set default values
        if (currency == null) {
            currency = "INR";
        }
        if (isActive == null) {
            isActive = true;
        }
        if (autoRenew == null) {
            autoRenew = true;
        }
        if (isCurrent == null) {
            isCurrent = true;
        }
        if (paymentRetryCount == null) {
            paymentRetryCount = 0;
        }
        if (cancelledAtEndOfPeriod == null) {
            cancelledAtEndOfPeriod = false;
        }

        // Sync dates
        if (billingPeriodEnd != null) {
            this.endsAt = billingPeriodEnd;
        } else if (endsAt != null) {
            this.billingPeriodEnd = endsAt;
        }
        if (billingPeriodStart == null && startedAt != null) {
            this.billingPeriodStart = startedAt;
        }

        // Sync amount with plan price if not set
        if (amount == null && subscriptionPlan != null) {
            amount = subscriptionPlan.getPrice();
        }

        // Sync plan name with plan if not set
        if (planName == null && subscriptionPlan != null) {
            planName = subscriptionPlan.getName();
        }
    }

    @Override
    public String toString() {
        return String.format("TenantSubscription{id=%d, tenantId=%d, planName='%s', status='%s', active=%s}",
                id, tenant != null ? tenant.getId() : null, planName, planStatus, isActive);
    }
}