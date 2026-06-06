package com.sonixhr.entity.platform;

import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.enums.PlatformUserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "platform_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_tenant_email", columnNames = {"tenant_id", "email"})
        },
        indexes = {
                @Index(name = "idx_user_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_user_email", columnList = "email"),
                @Index(name = "idx_user_tenant_active", columnList = "tenant_id, is_active"),
                @Index(name = "idx_user_status", columnList = "status"),
                @Index(name = "idx_user_reset_token", columnList = "reset_token")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PlatformUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 100)
    private String designation;

    /**
     * Password column with explicit length for encoded passwords
     * BCrypt: 60 chars
     * Argon2: up to 255 chars
     * PBKDF2: variable length
     */
    @Column(nullable = false, length = 255)
    private String password;

    /**
     * Track password last changed for password expiration policies
     */
    @Column(name = "password_last_changed")
    private LocalDateTime passwordLastChanged;

    /**
     * Password reset token for "forgot password" flow
     */
    @Column(name = "reset_token", length = 255)
    private String resetToken;

    /**
     * Password reset token expiry
     */
    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    /**
     * Tracks number of failed login attempts for account locking
     */
    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    /**
     * Account lock time if locked due to too many failed attempts
     */
    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    /**
     * Last successful login timestamp
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Last login IP address for security auditing
     */
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    /**
     * User status (ACTIVE, INACTIVE, PENDING_VERIFICATION, SUSPENDED, LOCKED, DELETED)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private PlatformUserStatus status = PlatformUserStatus.PENDING_VERIFICATION;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "must_change_password")
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "system_protected")
    @Builder.Default
    private boolean systemProtected = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "platform_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"})
    )
    @Builder.Default
    private Set<PlatformRole> roles = new HashSet<>();

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_account_non_locked")
    @Builder.Default
    private boolean isAccountNonLocked = true;

    @Column(name = "is_enabled")
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_credentials_non_expired")
    @Builder.Default
    private boolean isCredentialsNonExpired = true;

    @Column(name = "is_account_non_expired")
    @Builder.Default
    private boolean isAccountNonExpired = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;

    @Version
    private Long version;

    // ==================== UserDetails Implementation ====================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> new SimpleGrantedAuthority(permission.getPermission().name()))
                .distinct()
                .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return isAccountNonExpired && status != PlatformUserStatus.DELETED;
    }

    @Override
    public boolean isAccountNonLocked() {
        boolean isNotLockedByStatus = status != PlatformUserStatus.LOCKED;
        boolean isNotLockedByAttempts = isAccountNonLocked && (lockTime == null || lockTime.isBefore(LocalDateTime.now().minusMinutes(30)));
        return isNotLockedByStatus && isNotLockedByAttempts;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        boolean passwordNotExpired = true;
        if (passwordLastChanged != null) {
            passwordNotExpired = passwordLastChanged.isAfter(LocalDateTime.now().minusDays(90));
        }
        return isCredentialsNonExpired && passwordNotExpired;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled && isActive && status == PlatformUserStatus.ACTIVE;
    }

    // ==================== Helper Methods for Password Management ====================

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;

        if (this.failedLoginAttempts >= 5) {
            this.isAccountNonLocked = false;
            this.lockTime = LocalDateTime.now();
            this.status = PlatformUserStatus.LOCKED;
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.isAccountNonLocked = true;
        this.lockTime = null;
        if (this.status == PlatformUserStatus.LOCKED) {
            this.status = PlatformUserStatus.ACTIVE;
        }
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordLastChanged = LocalDateTime.now();
        this.resetToken = null;
        this.resetTokenExpiry = null;
        this.mustChangePassword = false;
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
                && this.status == PlatformUserStatus.ACTIVE;
    }

    public void clearResetToken() {
        this.resetToken = null;
        this.resetTokenExpiry = null;
    }

    public void recordSuccessfulLogin(String ipAddress) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ipAddress;
        resetFailedLoginAttempts();
    }

    public boolean isLocked() {
        return !isAccountNonLocked() || status == PlatformUserStatus.LOCKED;
    }

    public Long getLockTimeRemainingMinutes() {
        if (lockTime == null) return 0L;
        long minutesElapsed = java.time.Duration.between(lockTime, LocalDateTime.now()).toMinutes();
        long remaining = 30 - minutesElapsed;
        return remaining > 0 ? remaining : 0;
    }

    public void softDelete() {
        this.isActive = false;
        this.isEnabled = false;
        this.status = PlatformUserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        if (this.status == PlatformUserStatus.DELETED) {
            this.isActive = true;
            this.isEnabled = true;
            this.status = PlatformUserStatus.INACTIVE;
            this.deletedAt = null;
        }
    }

    // ==================== PERMISSION CHECK METHODS (ADD THESE) ====================

    /**
     * Check if user has a specific permission
     * @param permissionName The permission name to check (e.g., "MANAGE_PLATFORM_ROLES")
     * @return true if user has the permission
     */
    public boolean hasPermission(String permissionName) {
        if (permissionName == null || permissionName.isEmpty()) {
            return false;
        }

        // Super admin has all permissions
        if (isSuperAdmin()) {
            return true;
        }

        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.getPermission() != null &&
                        permission.getPermission().name().equals(permissionName));
    }

    /**
     * Check if user has any of the given permissions
     * @param permissionNames Array of permission names to check
     * @return true if user has at least one of the permissions
     */
    public boolean hasAnyPermission(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) {
            return false;
        }

        if (isSuperAdmin()) {
            return true;
        }

        Set<String> permissionSet = Set.of(permissionNames);

        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.getPermission() != null &&
                        permissionSet.contains(permission.getPermission().name()));
    }

    /**
     * Check if user has all of the given permissions
     * @param permissionNames Array of permission names to check
     * @return true if user has all the permissions
     */
    public boolean hasAllPermissions(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) {
            return true;
        }

        if (isSuperAdmin()) {
            return true;
        }

        Set<String> requiredPermissions = Set.of(permissionNames);
        Set<String> userPermissions = getPermissionNames();

        return userPermissions.containsAll(requiredPermissions);
    }

    /**
     * Check if user is Super Admin
     * A user is considered Super Admin if:
     * 1. They have a role named "SUPER_ADMIN" or "PLATFORM_ADMIN"
     * 2. They have the "SUPER_ADMIN" permission
     * 3. They have the "MANAGE_PLATFORM_ROLES" permission
     * 4. They have the systemProtected flag set to true
     *
     * @return true if user is super admin
     */
    public boolean isSuperAdmin() {
        // Check by role name
        boolean hasSuperAdminRole = roles.stream()
                .anyMatch(role -> "SUPER_ADMIN".equals(role.getName()) ||
                        "PLATFORM_ADMIN".equals(role.getName()));

        if (hasSuperAdminRole) {
            return true;
        }

        // Check by permission
        boolean hasSuperAdminPermission = roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.getPermission() != null &&
                        ("SUPER_ADMIN".equals(permission.getPermission().name()) ||
                                "MANAGE_PLATFORM_ROLES".equals(permission.getPermission().name())));

        if (hasSuperAdminPermission) {
            return true;
        }

        // Check system protected flag
        return systemProtected;
    }

    /**
     * Check if user is Tenant Admin
     * @return true if user has tenant admin role
     */
    public boolean isTenantAdmin() {
        return roles.stream()
                .anyMatch(role -> "TENANT_ADMIN".equals(role.getName()) ||
                        "ORG_ADMIN".equals(role.getName()));
    }

    /**
     * Get all permission names as a set
     * @return Set of permission names
     */
    public Set<String> getPermissionNames() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .filter(permission -> permission.getPermission() != null)
                .map(permission -> permission.getPermission().name())
                .collect(Collectors.toSet());
    }

    /**
     * Get all role names as a set
     * @return Set of role names
     */
    public Set<String> getRoleNames() {
        return roles.stream()
                .map(PlatformRole::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Check if user has a specific role
     * @param roleName The role name to check
     * @return true if user has the role
     */
    public boolean hasRole(String roleName) {
        return roles.stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * Check if user has any of the given roles
     * @param roleNames Array of role names to check
     * @return true if user has at least one of the roles
     */
    public boolean hasAnyRole(String... roleNames) {
        if (roleNames == null || roleNames.length == 0) {
            return false;
        }
        Set<String> roleSet = Set.of(roleNames);
        return roles.stream()
                .anyMatch(role -> roleSet.contains(role.getName()));
    }

    /**
     * Get permissions by category
     * @param category The category name
     * @return Set of permissions in that category
     */
    public Set<String> getPermissionsByCategory(String category) {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .filter(permission -> permission.getPermission() != null &&
                        category.equals(permission.getCategory()))
                .map(permission -> permission.getPermission().name())
                .collect(Collectors.toSet());
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformUser that = (PlatformUser) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PlatformUser{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", designation='" + designation + '\'' +
                ", status=" + status +
                ", isActive=" + isActive +
                ", isAccountNonLocked=" + isAccountNonLocked +
                ", isEnabled=" + isEnabled +
                ", failedLoginAttempts=" + failedLoginAttempts +
                ", lastLoginAt=" + lastLoginAt +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", version=" + version +
                '}';
    }
}