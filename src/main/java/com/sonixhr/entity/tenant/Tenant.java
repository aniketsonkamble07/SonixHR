package com.sonixhr.entity.tenant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_code", unique = true, nullable = false, length = 50)
    private String tenantCode;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(unique = true, nullable = false, length = 100)
    private String subdomain;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

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
    private String planType;

    @Column(name = "max_employees")
    @Builder.Default
    private Integer maxEmployees = 100;

    @Column(name = "max_storage_mb")
    @Builder.Default
    private Integer maxStorageMb = 1024;

    @Column(name = "plan_status", length = 20)
    @Builder.Default
    private String planStatus = "trial";

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "suspended_at")   // ✅ ADD THIS FIELD
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason")
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

    // Helper methods
    public void softDelete() {
        this.status = "deleted";
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void suspend() {
        this.status = "suspended";
        this.isActive = false;
        this.suspendedAt = LocalDateTime.now();
    }

    public void activate() {
        this.status = "active";
        this.isActive = true;
        this.deletedAt = null;
        this.suspendedAt = null;
    }
}