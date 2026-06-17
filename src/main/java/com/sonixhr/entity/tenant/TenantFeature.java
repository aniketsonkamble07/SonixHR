package com.sonixhr.entity.tenant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
 
@Entity
@Table(name = "tenant_features", indexes = {
        @Index(name = "idx_features_tenant", columnList = "tenant_id"),
        @Index(name = "idx_features_enabled", columnList = "is_enabled"),
        @Index(name = "idx_features_name", columnList = "feature_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = false;

    @Column(name = "feature_limit")
    private Integer featureLimit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_value", columnDefinition = "jsonb")
    private String featureValue;

    @Column(name = "enabled_at")
    @Builder.Default
    private LocalDateTime enabledAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper method
    public void enable() {
        this.isEnabled = true;
        this.enabledAt = LocalDateTime.now();
    }

    public void disable() {
        this.isEnabled = false;
    }
}