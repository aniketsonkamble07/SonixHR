package com.sonixhr.entity.tenant;

import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.TenantDataStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tenants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_code", columnNames = "tenant_code")
}, indexes = {
        @Index(name = "idx_tenant_code", columnList = "tenant_code"),
        @Index(name = "idx_tenant_status", columnList = "status"),
        @Index(name = "idx_tenant_is_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_code", unique = true, nullable = false, length = 50)
    private String tenantCode;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "admin_email", nullable = false, length = 255)
    private String adminEmail;

    @Column(name = "admin_name", nullable = false, length = 200)
    private String adminName;

    @Column(name = "admin_phone", length = 20)
    private String adminPhone;

    @Column(name = "office_address", length = 500)
    private String officeAddress;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    @jakarta.persistence.Convert(converter = com.sonixhr.enums.IndianStateConverter.class)
    private IndianState state;

    @Column(name = "state_text", length = 150)
    private String stateText;

    @Column(name = "country", length = 50)
    private String country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id")
    private SubscriptionPlan subscriptionPlan;

    public String getPlanType() {
        return this.subscriptionPlan != null ? this.subscriptionPlan.getName().toLowerCase() : null;
    }

    @Column(name = "max_employees")
    @Builder.Default
    private Integer maxEmployees = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", length = 20)
    @Builder.Default
    private PlanStatus planStatus = PlanStatus.ACTIVE;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "suspended", nullable = false)
    @Builder.Default
    private boolean suspended = false;

    @Column(name = "legal_hold", nullable = false)
    @Builder.Default
    private boolean legalHold = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archive_warning_notified_at")
    private LocalDateTime archiveWarningNotifiedAt;

    @Column(name = "final_reminder_sent_at")
    private LocalDateTime finalReminderSentAt;

    @Column(name = "expiration_notified_at")
    private LocalDateTime expirationNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_status", nullable = false, length = 50)
    @Builder.Default
    private TenantDataStatus dataStatus = TenantDataStatus.RETAINED;

    @Column(name = "deleted_by_admin_id")
    private Long deletedByAdminId;

    @Transient
    public LocalDateTime getDeletionEligibleAt() {
        return expiredAt != null ? expiredAt.plusYears(1) : null;
    }

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Builder.Default
    private Long version = 0L;

    @Column(name = "api_logging_enabled")
    @Builder.Default
    private Boolean apiLoggingEnabled = true;

    public boolean isApiLoggingEnabled() {
        return apiLoggingEnabled == null || apiLoggingEnabled;
    }

    // ==================== Helper Methods ====================

    public void softDelete() {
        this.status = UserStatus.DELETED;
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void suspend(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.isActive = false;
        this.suspendedAt = LocalDateTime.now();
        this.suspensionReason = reason;
        this.suspended = true;
    }

    public void expire(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.isActive = false;
        this.planStatus = PlanStatus.EXPIRED;
        this.suspendedAt = LocalDateTime.now();
        this.suspensionReason = reason;
        if (this.expiredAt == null) {
            this.expiredAt = LocalDateTime.now();
            this.dataStatus = TenantDataStatus.RETAINED;
            this.archivedAt = null;
        }
    }

    public void resetSubscriptionLifecycle() {
        this.expiredAt = null;
        this.archivedAt = null;
        this.archiveWarningNotifiedAt = null;
        this.finalReminderSentAt = null;
        this.expirationNotifiedAt = null;
        this.dataStatus = TenantDataStatus.RETAINED;
        this.suspendedAt = null;
        this.suspensionReason = null;
        this.status = UserStatus.ACTIVE;
        this.isActive = true;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.isActive = true;
        this.deletedAt = null;
        this.suspendedAt = null;
        this.suspensionReason = null;
        this.suspended = false;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.isActive = false;
    }

    public boolean isSuspended() {
        return this.suspended;
    }

    public boolean isDeleted() {
        return this.status == UserStatus.DELETED;
    }

    public boolean isExpired() {
        return this.endsAt != null && this.endsAt.isBefore(LocalDateTime.now());
    }

    public int getDaysLeft() {
        if (this.endsAt == null) {
            return 0;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), this.endsAt);
    }

    // Convenience method for template compatibility
    public Boolean getIsActive() {
        return isActive;
    }

    // JPA Lifecycle Validation
    @PrePersist
    @PreUpdate
    private void validate() {
        if (tenantCode == null || tenantCode.isEmpty()) {
            throw new IllegalStateException("Tenant code is required");
        }
        if (companyName == null || companyName.isEmpty()) {
            throw new IllegalStateException("Company name is required");
        }
        if (adminEmail == null || !adminEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalStateException("Valid admin email is required");
        }
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Tenant tenant = (Tenant) o;
        return id != null && Objects.equals(id, tenant.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Tenant{" +
                "id=" + id +
                ", tenantCode='" + tenantCode + '\'' +
                ", companyName='" + companyName + '\'' +
                ", status=" + status +
                ", isActive=" + isActive +
                ", planType=" + getPlanType() +
                ", createdAt=" + createdAt +
                '}';
    }


}