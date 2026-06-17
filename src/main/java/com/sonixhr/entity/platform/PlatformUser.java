package com.sonixhr.entity.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sonixhr.common.base.BaseUser;
import com.sonixhr.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "platform_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_platform_user_email", columnNames = {"email"})
        },
        indexes = {
                @Index(name = "idx_platform_user_email", columnList = "email"),
                @Index(name = "idx_platform_user_status", columnList = "status"),
                @Index(name = "idx_platform_user_active", columnList = "is_active"),
                @Index(name = "idx_platform_user_reset_token", columnList = "reset_token")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PlatformUser extends BaseUser {

    @Column(length = 100)
    private String designation;

    @Column(name = "password_last_changed")
    private LocalDateTime passwordLastChanged;

    @Column(name = "reset_token", length = 255)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "platform_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"),
            indexes = {
                    @Index(name = "idx_user_role_user", columnList = "user_id"),
                    @Index(name = "idx_user_role_role", columnList = "role_id")
            }
    )
    @Builder.Default
    @JsonIgnore
    private Set<PlatformRole> roles = new HashSet<>();

    // ==================== UserDetails Implementation ====================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (getCachedAuthorities() != null && getCachedRolesVersion() != null &&
                getCachedRolesVersion().equals(getRolesVersion())) {
            return getCachedAuthorities();
        }

        Set<GrantedAuthority> authorities = new HashSet<>();
        loadAuthorities(authorities);

        setCachedAuthorities(authorities);
        setCachedRolesVersion(getRolesVersion());

        return authorities;
    }

    @Override
    protected void loadAuthorities(Set<GrantedAuthority> authorities) {
        for (PlatformRole role : roles) {
            if (role.isActive()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));

                for (PlatformPermission permission : role.getPermissions()) {
                    if (permission != null && permission.isActive()) {
                        String permissionName = permission.getPermission();
                        if (permissionName != null && !permissionName.isEmpty()) {
                            authorities.add(new SimpleGrantedAuthority(permissionName));
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE && isActive();
    }

    // ==================== Helper Methods ====================

    public void updatePassword(String encodedPassword) {
        setPassword(encodedPassword);
        this.passwordLastChanged = LocalDateTime.now();
        this.resetToken = null;
        this.resetTokenExpiry = null;
        incrementRolesVersion();
        clearAuthoritiesCache();
    }

    public void setPasswordResetToken(String token) {
        this.resetToken = token;
        this.resetTokenExpiry = LocalDateTime.now().plusHours(24);
    }

    public boolean isResetTokenValid(String token) {
        return token != null
                && token.equals(this.resetToken)
                && this.resetTokenExpiry != null
                && this.resetTokenExpiry.isAfter(LocalDateTime.now())
                && this.status == UserStatus.ACTIVE;
    }

    public void clearResetToken() {
        this.resetToken = null;
        this.resetTokenExpiry = null;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
        setActive(false);
        incrementRolesVersion();
        clearAuthoritiesCache();
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
        setActive(true);
        incrementRolesVersion();
        clearAuthoritiesCache();
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        setActive(false);
        incrementRolesVersion();
        clearAuthoritiesCache();
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
        setActive(false);
        incrementRolesVersion();
        clearAuthoritiesCache();
    }

    public boolean isSuspended() {
        return this.status == UserStatus.SUSPENDED;
    }

    @Override
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE && super.isActive();
    }

    // ==================== Permission Check Methods ====================

    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }

    public boolean hasPermission(String permissionName) {
        if (permissionName == null || permissionName.isEmpty()) return false;
        if (isSuperAdmin()) return true;

        return getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(permissionName));
    }

    public boolean hasAnyPermission(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) return false;
        if (isSuperAdmin()) return true;

        Set<String> permissionSet = Set.of(permissionNames);
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permissionSet::contains);
    }

    public boolean hasRole(String roleName) {
        return roles.stream()
                .anyMatch(role -> role.isActive() && role.getName().equals(roleName));
    }

    public boolean hasAnyRole(String... roleNames) {
        if (roleNames == null || roleNames.length == 0) return false;
        Set<String> roleSet = Set.of(roleNames);
        return roles.stream()
                .anyMatch(role -> role.isActive() && roleSet.contains(role.getName()));
    }

    public Set<String> getPermissionNames() {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    public Set<String> getRoleNames() {
        return roles.stream()
                .filter(PlatformRole::isActive)
                .map(PlatformRole::getName)
                .collect(Collectors.toSet());
    }

    public java.util.Map<String, Object> toSummary() {
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("id", getId());
        summary.put("email", getEmail());
        summary.put("fullName", getFullName());
        summary.put("designation", designation);
        summary.put("status", status);
        summary.put("active", isActive());
        summary.put("roles", getRoleNames());
        summary.put("permissionCount", getPermissionNames().size());
        summary.put("createdAt", getCreatedAt());
        summary.put("updatedAt", getUpdatedAt());
        return summary;
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformUser that = (PlatformUser) o;
        return getId() != null && java.util.Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PlatformUser{" +
                "id=" + getId() +
                ", email='" + getEmail() + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", status=" + status +
                ", active=" + isActive() +
                ", rolesVersion=" + getRolesVersion() +
                '}';
    }
}