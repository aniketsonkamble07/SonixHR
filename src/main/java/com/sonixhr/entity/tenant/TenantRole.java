package com.sonixhr.entity.tenant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sonixhr.common.base.BasePermission;
import com.sonixhr.common.base.BaseRole;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.TenantPermissionEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "tenant_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_tenant_name", columnNames = {"tenant_id", "name"})
}, indexes = {
        @Index(name = "idx_tenant_role_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tenant_role_name", columnList = "name"),
        @Index(name = "idx_tenant_role_default", columnList = "is_default"),
        @Index(name = "idx_tenant_role_tenant_system", columnList = "is_system_role")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@SuppressWarnings("null")
public class TenantRole extends BaseRole {

    @Column(name = "tenant_id", nullable = true)
    private Long tenantId;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

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
    @JsonIgnore
    private Set<Employee> employees = new HashSet<>();

    // ==================== Implement BaseRole Abstract Methods ====================

    @Override
    public Set<TenantPermission> getPermissions() {
        return permissions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setPermissions(Set<? extends BasePermission> permissions) {
        this.permissions = (Set<TenantPermission>) permissions;
    }

    // ==================== Helper Methods ====================

    public boolean isSystemRole() {
        return super.isSystemRole();
    }

    public void setSystemRole(boolean systemRole) {
        super.setSystemRole(systemRole);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public void addPermission(TenantPermission permission) {
        if (permission != null) {
            this.permissions.add(permission);
        }
    }

    public void removePermission(TenantPermission permission) {
        if (permission != null) {
            this.permissions.remove(permission);
        }
    }

    // Check if role has specific permission (by enum)
    public boolean hasPermission(TenantPermissionEnum permissionEnum) {
        if (permissionEnum == null) return false;
        return this.permissions.stream()
                .anyMatch(p -> p.getPermissionName() != null &&
                        p.getPermissionName().equals(permissionEnum.name()));
    }

    // Check by string name (for Spring Security)
    public boolean hasPermission(String permissionName) {
        if (permissionName == null) return false;
        return this.permissions.stream()
                .anyMatch(p -> p.getPermissionName() != null &&
                        p.getPermissionName().equals(permissionName));
    }

    // Get all permission names as strings (for Spring Security)
    public Set<String> getPermissionNames() {
        if (this.permissions == null || this.permissions.isEmpty()) {
            return new HashSet<>();
        }
        return this.permissions.stream()
                .filter(p -> p.getPermissionName() != null)
                .map(TenantPermission::getPermissionName)
                .collect(Collectors.toSet());
    }

    public int getPermissionCount() {
        return this.permissions.size();
    }

    public boolean isDefaultRole() {
        return isDefault;
    }

    public void setAsDefault() {
        this.isDefault = true;
    }

    public void removeDefaultFlag() {
        this.isDefault = false;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isAssignedToEmployees() {
        return employees != null && !employees.isEmpty();
    }

    public int getEmployeeCount() {
        return employees != null ? employees.size() : 0;
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantRole that = (TenantRole) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "TenantRole{" +
                "id=" + getId() +
                ", name='" + getName() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", isSystemRole=" + isSystemRole() +
                ", isDefault=" + isDefault +
                ", isActive=" + active +
                ", permissionCount=" + getPermissionCount() +
                '}';
    }
}