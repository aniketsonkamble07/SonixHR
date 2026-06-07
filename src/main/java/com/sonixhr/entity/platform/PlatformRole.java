package com.sonixhr.entity.platform;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "platform_roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_role_tenant_name", columnNames = { "name"})
        },
        indexes = {
                @Index(name = "idx_role_tenant_system", columnList = " is_system_role"),
                @Index(name = "idx_role_name", columnList = "name"),
                @Index(name = "idx_role_created_at", columnList = "created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PlatformRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(name = "is_system_role")
    @Builder.Default
    private boolean isSystemRole = false;

    /**
     * Role category for better organization (e.g., ADMIN, HR, MANAGER, EMPLOYEE)
     */
    @Column(length = 50)
    private String category;



    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "platform_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"})
    )
    @Builder.Default
    private Set<PlatformPermission> permissions = new HashSet<>();

    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<PlatformUser> users = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    // ==================== Helper Methods ====================

    /**
     * Add a permission to this role
     */
    public void addPermission(PlatformPermission permission) {
        if (permission != null) {
            this.permissions.add(permission);
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
     * Check if role has a specific permission
     */
    public boolean hasPermission(String permissionName) {
        return this.permissions.stream()
                .anyMatch(p -> p.getPermission() != null &&
                        p.getPermission().name().equals(permissionName));
    }

    /**
     * Check if role has any of the given permissions
     */
    public boolean hasAnyPermission(String... permissionNames) {
        Set<String> permissionSet = Set.of(permissionNames);
        return this.permissions.stream()
                .map(p -> p.getPermission().name())
                .anyMatch(permissionSet::contains);
    }

    /**
     * Get all permission names as a set
     */
    public Set<String> getPermissionNames() {
        return this.permissions.stream()
                .map(p -> p.getPermission().name())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Count of permissions assigned to this role
     */
    public int getPermissionCount() {
        return this.permissions.size();
    }

    /**
     * Check if this is a system role (cannot be modified or deleted)
     */
    public boolean isSystemRole() {
        return isSystemRole;
    }

    /**
     * Check if role is assignable to users
     */
    public boolean isAssignable() {
        return !isSystemRole || "SYSTEM_ADMIN".equals(this.name);
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformRole that = (PlatformRole) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PlatformRole{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", isSystemRole=" + isSystemRole +
                ", category='" + category + '\'' +
                ", permissionCount=" + (permissions != null ? permissions.size() : 0) +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy=" + createdBy +
                ", version=" + version +
                '}';
    }
}