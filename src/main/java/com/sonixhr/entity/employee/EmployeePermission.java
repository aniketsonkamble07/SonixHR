package com.sonixhr.entity.employee;

import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.enums.employee.EmployeePermissionEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "employee_permissions", indexes = {
        @Index(name = "idx_perm_permission", columnList = "permission"),
        @Index(name = "idx_perm_category", columnList = "category")
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

    private Integer displayOrder;



    // ✅ Added audit fields
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

    // ✅ Fixed: Only populate on create, not on update
    @PrePersist
    public void prePersist() {
        populateFromEnumIfNeeded();
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        // Don't auto-populate on update - preserve manual changes
    }

    private void populateFromEnumIfNeeded() {
        if (permission != null) {
            // Only set defaults if values are null (new record)
            if (description == null) {
                this.description = permission.getDescription();
            }
            if (category == null) {
                this.category = permission.getCategory();
            }
            if (displayOrder == null) {
                this.displayOrder = permission.getOrder();
            }
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public String getPermissionName() {
        return permission != null ? permission.name() : null;
    }


}