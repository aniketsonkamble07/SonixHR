package com.sonixhr.entity.tenant;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.TenantPermissionEnum;
import com.sonixhr.exceptions.BusinessException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_tenant_name", columnNames = {"tenant_id", "name"})
}, indexes = {
        @Index(name = "idx_role_tenant", columnList = "tenant_id"),
        @Index(name = "idx_role_name", columnList = "name"),
        @Index(name = "idx_role_default", columnList = "is_default")
})
public class TenantRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = true)  // CHANGE: nullable = true to allow system/template roles
    private Long tenantId;  // null = system role (template), non-null = tenant-specific role

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "role_tenant_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<TenantPermission> permissions = new HashSet<>();

    @ManyToMany(mappedBy = "roles")
    @Builder.Default
    private Set<Employee> employees = new HashSet<>();

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Check if this is a system role (template)
     */
    public boolean isSystemRole() {
        return tenantId == null;
    }

    /**
     * Check if this is a tenant-specific role
     */
    public boolean isTenantRole() {
        return tenantId != null;
    }

    /**
     * Add permission to role (maintains both sides if needed)
     */
    public void addPermission(TenantPermission permission) {
        if (permission != null) {
            this.permissions.add(permission);
        }
    }

    /**
     * Remove permission from role
     */
    public void removePermission(TenantPermission permission) {
        this.permissions.remove(permission);
    }

    /**
     * Add multiple permissions at once
     */
    public void addPermissions(Set<TenantPermission> permissions) {
        if (permissions != null) {
            this.permissions.addAll(permissions);
        }
    }

    /**
     * Check if role has specific permission
     */
    public boolean hasPermission(TenantPermissionEnum permissionEnum) {
        return permissions.stream()
                .anyMatch(p -> p.getPermission() == permissionEnum);
    }

    /**
     * Check if role has any of the given permissions
     */
    public boolean hasAnyPermission(TenantPermissionEnum... permissionEnums) {
        Set<TenantPermissionEnum> permissionSet = Set.of(permissionEnums);
        return permissions.stream()
                .anyMatch(p -> permissionSet.contains(p.getPermission()));
    }

    /**
     * Get all permission names as strings
     */
    public Set<String> getPermissionNames() {
        return permissions.stream()
                .map(p -> p.getPermission().name())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Check if role is assigned to any employee
     */
    public boolean isAssignedToEmployees() {
        return employees != null && !employees.isEmpty();
    }

    /**
     * Get count of employees with this role
     */
    public int getEmployeeCount() {
        return employees != null ? employees.size() : 0;
    }

    /**
     * Check if role is a default role for the tenant
     */
    public boolean isDefaultRole() {
        return isDefault;
    }

    /**
     * Set as default role (ensures only one default per tenant)
     * Note: This method should be called within a service with proper transaction
     */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /**
     * Remove default flag
     */
    public void removeDefaultFlag() {
        this.isDefault = false;
    }

    /**
     * Validate role before deletion
     */
    public void validateForDeletion() {
        if (isAssignedToEmployees()) {
            throw new BusinessException("Cannot delete role. It is assigned to " + getEmployeeCount() + " employee(s)");
        }

        if (isDefaultRole() && !isSystemRole()) {
            throw new BusinessException("Cannot delete default role. Set another role as default first");
        }
    }

    /**
     * Check if this is a super admin role
     */
    public boolean isSuperAdminRole() {
        return "Super Admin".equals(name);
    }

    /**
     * Check if this is an admin role
     */
    public boolean isAdminRole() {
        return "Admin".equals(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantRole)) return false;
        TenantRole that = (TenantRole) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "TenantRole{id=" + id + ", name='" + name + "', tenantId=" + tenantId + "}";
    }
}