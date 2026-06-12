package com.sonixhr.entity.employee;

import com.sonixhr.enums.employee.EmployeePermissionEnum;
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
@Table(name = "employee_permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employee_permission", columnNames = {"permission"})
        },
        indexes = {
                @Index(name = "idx_emp_perm_permission", columnList = "permission"),
                @Index(name = "idx_emp_perm_category", columnList = "category"),
                @Index(name = "idx_emp_perm_active", columnList = "is_active"),
                @Index(name = "idx_emp_perm_display_order", columnList = "display_order")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private EmployeePermissionEnum permission;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String category;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Get permission name as string
     */
    public String getPermissionName() {
        return permission != null ? permission.name() : null;
    }

    /**
     * Get effective description (from DB or enum)
     */
    public String getEffectiveDescription() {
        if (description != null && !description.isEmpty()) {
            return description;
        }
        return permission != null ? permission.getDescription() : "";
    }

    /**
     * Get effective category (from DB or enum)
     */
    public String getEffectiveCategory() {
        if (category != null && !category.isEmpty()) {
            return category;
        }
        return permission != null ? permission.getCategory() : "General";
    }

    /**
     * Get effective display order (from DB or enum)
     */
    public int getEffectiveDisplayOrder() {
        if (displayOrder != null && displayOrder > 0) {
            return displayOrder;
        }
        return permission != null ? permission.getOrder() : 999;
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
     * Populate fields from enum if they are null
     */
    public void populateFromEnumIfNeeded() {
        if (permission != null) {
            if (description == null || description.isEmpty()) {
                this.description = permission.getDescription();
            }
            if (category == null || category.isEmpty()) {
                this.category = permission.getCategory();
            }
            if (displayOrder == null || displayOrder == 0) {
                this.displayOrder = permission.getOrder();
            }
        }
    }

    // =====================================================
    // JPA LIFECYCLE METHODS
    // =====================================================

    @PrePersist
    protected void onCreate() {
        populateFromEnumIfNeeded();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // =====================================================
    // EQUALS, HASHCODE, TOSTRING
    // =====================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeePermission that = (EmployeePermission) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "EmployeePermission{" +
                "id=" + id +
                ", permission=" + permission +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", displayOrder=" + displayOrder +
                ", active=" + active +
                ", createdAt=" + createdAt +
                '}';
    }
}