package com.sonixhr.entity.tenant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
 
@Entity
@Table(name = "tenant_settings", indexes = {
        @Index(name = "idx_settings_tenant", columnList = "tenant_id"),
        @Index(name = "idx_settings_key", columnList = "setting_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "setting_type", length = 20)
    @Builder.Default
    private String settingType = "string";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods for typed values
    public Boolean getBooleanValue() {
        return Boolean.parseBoolean(settingValue);
    }

    public Integer getIntegerValue() {
        try {
            return Integer.parseInt(settingValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}