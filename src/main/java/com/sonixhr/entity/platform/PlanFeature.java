// entity/platform/PlanFeature.java
package com.sonixhr.entity.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "plan_features", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_feature", columnNames = {"plan_id", "feature_code"})
}, indexes = {
        @Index(name = "idx_plan_feature_plan", columnList = "plan_id"),
        @Index(name = "idx_plan_feature_code", columnList = "feature_code"),
        @Index(name = "idx_plan_feature_enabled", columnList = "is_enabled")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @NotBlank(message = "Feature code cannot be blank")
    @Size(min = 2, max = 100, message = "Feature code must be between 2 and 100 characters")
    @Column(name = "feature_code", nullable = false, length = 100)
    private String featureCode;

    @Size(max = 200, message = "Feature description cannot exceed 200 characters")
    @Column(name = "description", length = 200)
    private String description;

    @NotNull(message = "Enabled status cannot be null")
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.isEnabled);
    }

    public void enable() {
        this.isEnabled = true;
    }

    public void disable() {
        this.isEnabled = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanFeature that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return String.format("PlanFeature{id=%d, featureCode='%s', enabled=%s}",
                id, featureCode, isEnabled);
    }
}