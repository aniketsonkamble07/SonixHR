package com.sonixhr.entity.tenant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tenants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenant_code", columnNames = "tenant_code"),
                @UniqueConstraint(name = "uk_tenant_subdomain", columnNames = "subdomain")
        },
        indexes = {
                @Index(name = "idx_tenant_code", columnList = "tenant_code"),
                @Index(name = "idx_tenant_subdomain", columnList = "subdomain"),
                @Index(name = "idx_tenant_status", columnList = "status"),
                @Index(name = "idx_tenant_is_active", columnList = "is_active")
        })
@Getter
@Setter
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

    @Column(unique = true, nullable = false, length = 100)
    private String subdomain;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "admin_email", nullable = false, length = 255)
    private String adminEmail;

    @Column(name = "admin_name", nullable = false, length = 200)
    private String adminName;

    @Column(name = "admin_phone", length = 20)
    private String adminPhone;

    @Column(name = "plan_type", length = 20)
    @Builder.Default
    private String planType = "TRIAL";

    @Column(name = "max_employees")
    @Builder.Default
    private Integer maxEmployees = 100;



    @Column(name = "plan_status", length = 20)
    @Builder.Default
    private String planStatus = "ACTIVE";

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Builder.Default
    private Long version = 0L;

    // ==================== Helper Methods ====================

    public void softDelete() {
        this.status = "DELETED";
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void suspend(String reason) {
        this.status = "SUSPENDED";
        this.isActive = false;
        this.suspendedAt = LocalDateTime.now();
        this.suspensionReason = reason;
    }

    public void activate() {
        this.status = "ACTIVE";
        this.isActive = true;
        this.deletedAt = null;
        this.suspendedAt = null;
        this.suspensionReason = null;
    }

    public boolean isSuspended() {
        return "SUSPENDED".equals(this.status) || (this.suspendedAt != null && this.suspendedAt.isBefore(LocalDateTime.now()));
    }

    public boolean isDeleted() {
        return "DELETED".equals(this.status) || this.deletedAt != null;
    }

    public boolean isTrialActive() {
        return "TRIAL".equals(this.planType) &&
                this.trialEndsAt != null &&
                this.trialEndsAt.isAfter(LocalDateTime.now());
    }

    public boolean isTrialExpired() {
        return "TRIAL".equals(this.planType) &&
                this.trialEndsAt != null &&
                this.trialEndsAt.isBefore(LocalDateTime.now());
    }

    public int getDaysLeftInTrial() {
        if (this.trialEndsAt == null || !"TRIAL".equals(this.planType)) {
            return 0;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), this.trialEndsAt);
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
                ", subdomain='" + subdomain + '\'' +
                ", status='" + status + '\'' +
                ", isActive=" + isActive +
                ", planType='" + planType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}