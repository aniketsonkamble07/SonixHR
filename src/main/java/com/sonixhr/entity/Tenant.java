package com.sonixhr.entity;

import com.sonixhr.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants",
        indexes = {
                @Index(name = "idx_tenant_code", columnList = "tenant_code"),
                @Index(name = "idx_plan_type", columnList = "plan_type"),
                @Index(name = "idx_status", columnList = "plan_status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
    private String tenantCode;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(unique = true)
    private String subdomain;

    // Plan details
    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status")
    private PlanStatus planStatus;

    private LocalDateTime trialEndsAt;

    // Limits
    private Integer maxEmployees;
    private Integer maxStorageMb;

    // Contact
    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    private String adminName;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime suspendedAt;

    // Default + lifecycle logic
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.planType == null) {
            this.planType = "basic";
        }

        if (this.planStatus == null) {
            this.planStatus = PlanStatus.TRIAL;
        }

        if (this.planStatus == PlanStatus.TRIAL && this.trialEndsAt == null) {
            this.trialEndsAt = LocalDateTime.now().plusDays(30); // 30-day trial
        }

        if (this.maxEmployees == null) {
            this.maxEmployees = 100;
        }

        if (this.maxStorageMb == null) {
            this.maxStorageMb = 1024;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}