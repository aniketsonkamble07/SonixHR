package com.sonixhr.entity.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sonixhr.common.base.BasePermission;
import com.sonixhr.common.base.BaseRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "platform_roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_role_name", columnNames = {"name"})
        },
        indexes = {
                @Index(name = "idx_role_system", columnList = "is_system_role"),
                @Index(name = "idx_role_name", columnList = "name"),
                @Index(name = "idx_role_category", columnList = "category"),
                @Index(name = "idx_role_active", columnList = "is_active"),
                @Index(name = "idx_role_priority", columnList = "priority"),
                @Index(name = "idx_role_created_at", columnList = "created_at")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PlatformRole extends BaseRole {

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "platform_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"}),
            indexes = {
                    @Index(name = "idx_role_perm_role", columnList = "role_id"),
                    @Index(name = "idx_role_perm_permission", columnList = "permission_id")
            }
    )
    @Builder.Default
    private Set<PlatformPermission> permissions = new HashSet<>();

    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    @JsonIgnore
    private Set<PlatformUser> users = new HashSet<>();

    // ==================== Implement BaseRole Abstract Methods ====================

    @Override
    public Set<PlatformPermission> getPermissions() {
        return permissions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setPermissions(Set<? extends BasePermission> permissions) {
        this.permissions = (Set<PlatformPermission>) permissions;
    }

    // ==================== Helper Methods ====================

    /**
     * Add a permission to this role
     */
    public void addPermission(PlatformPermission permission) {
        if (permission != null && permission.isActive()) {
            this.permissions.add(permission);
        }
    }

    /**
     * Add multiple permissions
     */
    public void addPermissions(PlatformPermission... permissions) {
        for (PlatformPermission permission : permissions) {
            addPermission(permission);
        }
    }

    /**
     * Remove a permission from this role
     */
    public void removePermission(PlatformPermission permission) {
        if (permission != null) {
            this.permissions.remove(permission);
        }
    }

    /**
     * Remove permission by name
     */
    public void removePermissionByName(String permissionName) {
        // FIXED: getPermission() returns String, so compare directly
        this.permissions.removeIf(p -> p.getPermission() != null &&
                p.getPermission().equals(permissionName));
    }

    /**
     * Check if role has a specific permission (optimized)
     */
    public boolean hasPermission(String permissionName) {
        if (permissionName == null || permissions.isEmpty()) {
            return false;
        }
        // FIXED: getPermission() returns String, so compare directly
        return this.permissions.stream()
                .anyMatch(p -> p.getPermission() != null &&
                        p.getPermission().equals(permissionName));
    }

    /**
     * Check if role has any of the given permissions (optimized)
     */
    public boolean hasAnyPermission(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0 || permissions.isEmpty()) {
            return false;
        }
        Set<String> permissionSet = Set.of(permissionNames);
        return this.permissions.stream()
                .filter(p -> p.getPermission() != null)
                .map(PlatformPermission::getPermission)  // This returns String
                .anyMatch(permissionSet::contains);
    }

    /**
     * Check if role has all of the given permissions
     */
    public boolean hasAllPermissions(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) {
            return true;
        }
        if (permissions.isEmpty()) {
            return false;
        }
        Set<String> permissionSet = getPermissionNames();
        return java.util.Arrays.stream(permissionNames).allMatch(permissionSet::contains);
    }

    /**
     * Get all permission names as a set (cached for performance)
     */
    public Set<String> getPermissionNames() {
        if (this.permissions == null || this.permissions.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        // FIXED: getPermission() returns String directly
        return this.permissions.stream()
                .filter(p -> p.getPermission() != null)
                .map(PlatformPermission::getPermission)  // Returns String
                .collect(Collectors.toSet());
    }

    /**
     * Get permission names as list
     */
    public java.util.List<String> getPermissionNamesList() {
        return new java.util.ArrayList<>(getPermissionNames());
    }

    /**
     * Count of permissions assigned to this role
     */
    public int getPermissionCount() {
        return this.permissions != null ? this.permissions.size() : 0;
    }

    /**
     * Check if role is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Activate role
     */
    public void activate() {
        this.active = true;
    }

    /**
     * Deactivate role (soft delete)
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Check if role is assignable to users
     */
    @Override
    public boolean isAssignable() {
        return !isSystemRole() && active;
    }

    /**
     * Check if role has higher priority than another role
     */
    public boolean isHigherPriorityThan(PlatformRole other) {
        if (other == null) return true;
        return this.priority > other.priority;
    }

    /**
     * Get role hierarchy level
     */
    public String getHierarchyLevel() {
        if (priority >= 100) return "SYSTEM";
        if (priority >= 80) return "ADMIN";
        if (priority >= 70) return "MANAGEMENT";
        if (priority >= 60) return "HR";
        if (priority >= 40) return "EMPLOYEE";
        return "BASIC";
    }

    /**
     * Create a summary of the role (without permissions list)
     */
    public java.util.Map<String, Object> toSummary() {
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("id", getId());
        summary.put("name", getName());
        summary.put("description", getDescription());
        summary.put("category", getCategory());
        summary.put("priority", priority);
        summary.put("systemRole", isSystemRole());
        summary.put("active", active);
        summary.put("permissionCount", getPermissionCount());
        summary.put("createdAt", getCreatedAt());
        summary.put("updatedAt", getUpdatedAt());
        return summary;
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformRole that = (PlatformRole) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PlatformRole{" +
                "id=" + getId() +
                ", name='" + getName() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", category='" + getCategory() + '\'' +
                ", priority=" + priority +
                ", systemRole=" + isSystemRole() +
                ", active=" + active +
                ", permissionCount=" + getPermissionCount() +
                ", createdAt=" + getCreatedAt() +
                '}';
    }
}