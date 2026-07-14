package com.sonixhr.entity.platform;

import com.sonixhr.common.base.BasePermission;
import com.sonixhr.enums.PlatformPermissionEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "platform_permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_permission_name", columnNames = {"permission"})
        },
        indexes = {
                @Index(name = "idx_permission_type", columnList = "permission"),
                @Index(name = "idx_permission_active", columnList = "is_active"),
                @Index(name = "idx_permission_display_order", columnList = "display_order")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PlatformPermission extends BasePermission {

    // This field overrides the permission field from BasePermission
    // But we need to handle it differently
    @Transient
    private PlatformPermissionEnum permissionEnum;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    // ==================== Override BasePermission field handling ====================

    /**
     * Set permission from enum (stores the enum name as String in the parent class)
     */
    public void setPermissionEnum(PlatformPermissionEnum permissionEnum) {
        this.permissionEnum = permissionEnum;
        // Store the enum name as String in the parent class
        super.setPermission(permissionEnum != null ? permissionEnum.name() : null);
        super.setDescription(permissionEnum != null ? permissionEnum.getDescription() : null);
        super.setCategory(permissionEnum != null ? permissionEnum.getCategory() : null);
        if (displayOrder == null && permissionEnum != null) {
            this.displayOrder = permissionEnum.getOrder();
        }
    }

    /**
     * Get permission as enum
     */
    public PlatformPermissionEnum getPermissionEnum() {
        if (permissionEnum == null && super.getPermission() != null) {
            try {
                permissionEnum = PlatformPermissionEnum.valueOf(super.getPermission());
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }
        return permissionEnum;
    }

    /**
     * Get permission name as string
     */
    public String getPermissionName() {
        return super.getPermission();
    }

    /**
     * Get effective description
     */
    public String getEffectiveDescription() {
        if (super.getDescription() != null && !super.getDescription().isEmpty()) {
            return super.getDescription();
        }
        return getPermissionEnum() != null ? getPermissionEnum().getDescription() : "";
    }

    /**
     * Get effective category
     */
    public String getEffectiveCategory() {
        if (super.getCategory() != null && !super.getCategory().isEmpty()) {
            return super.getCategory();
        }
        return getPermissionEnum() != null ? getPermissionEnum().getCategory() : "General";
    }

    /**
     * Get effective display order
     */
    public int getEffectiveDisplayOrder() {
        if (displayOrder != null && displayOrder > 0) {
            return displayOrder;
        }
        return getPermissionEnum() != null ? getPermissionEnum().getOrder() : 999;
    }

    /**
     * Activate permission
     */
    public void activate() {
        this.active = true;
    }

    /**
     * Deactivate permission
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Check if permission is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Create a summary DTO
     */
    public com.sonixhr.dto.PermissionDTO toDTO() {
        return com.sonixhr.dto.PermissionDTO.builder()
                .id(getId())
                .permission(getPermissionName())
                .description(getEffectiveDescription())
                .category(getEffectiveCategory())
                .displayOrder(getEffectiveDisplayOrder())
                .selected(false)
                .build();
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformPermission that = (PlatformPermission) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PlatformPermission{" +
                "id=" + getId() +
                ", permission=" + getPermissionName() +
                ", description='" + getEffectiveDescription() + '\'' +
                ", category='" + getEffectiveCategory() + '\'' +
                ", displayOrder=" + displayOrder +
                ", active=" + active +
                '}';
    }
}