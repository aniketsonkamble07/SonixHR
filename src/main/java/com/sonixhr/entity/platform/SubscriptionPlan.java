package com.sonixhr.entity.platform;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plans", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_code", columnNames = "code")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code; // e.g. "trial", "basic", "moderate", "premium", "enterprise"

    @Column(nullable = false, length = 100)
    private String name; // e.g. "Basic Plan"

    @Column(name = "monthly_price", nullable = false)
    private double monthlyPrice;

    @Column(name = "max_employees", nullable = false)
    private int maxEmployees;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb;

    @Column(name = "trial_days", nullable = false)
    private int trialDays;

    @Column(name = "is_trial", nullable = false)
    private boolean isTrial;

    @Column(name = "validity_months", nullable = false, columnDefinition = "integer default 1")
    @Builder.Default
    private int validityMonths = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
