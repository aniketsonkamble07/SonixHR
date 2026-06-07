package com.sonixhr.entity.platform;

import com.sonixhr.enums.UserStatus;
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
                @UniqueConstraint(name = "uk_platform_user_email", columnNames = {"email"})
        },
        indexes = {
                @Index(name = "idx_platform_user_email", columnList = "email"),
                @Index(name = "idx_platform_user_status", columnList = "status")
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

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 100)
    private String designation;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "password_last_changed")
    private LocalDateTime passwordLastChanged;

    @Column(name = "reset_token", length = 255)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    /**
     * User status: ACTIVE, INACTIVE, SUSPENDED, DELETED
     * Only SUSPENDED accounts are restricted from login
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "platform_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<PlatformRole> roles = new HashSet<>();

    // Audit fields
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
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Platform users: Only SUSPENDED accounts are locked
        return status != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Platform users: Only ACTIVE users can login
        return status == UserStatus.ACTIVE;
    }

    // ==================== Helper Methods ====================

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordLastChanged = LocalDateTime.now();
        this.resetToken = null;
        this.resetTokenExpiry = null;
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
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
    }

    public boolean isSuspended() {
        return this.status == UserStatus.SUSPENDED;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    // ==================== Permission Check Methods ====================

    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN") || hasRole("PLATFORM_ADMIN");
    }

    public boolean hasPermission(String permissionName) {
        if (permissionName == null || permissionName.isEmpty()) return false;
        if (isSuperAdmin()) return true;

        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.getPermission() != null &&
                        permission.getPermission().name().equals(permissionName));
    }

    public boolean hasAnyPermission(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) return false;
        if (isSuperAdmin()) return true;

        Set<String> permissionSet = Set.of(permissionNames);
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.getPermission() != null &&
                        permissionSet.contains(permission.getPermission().name()));
    }

    public boolean hasRole(String roleName) {
        return roles.stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    public boolean hasAnyRole(String... roleNames) {
        if (roleNames == null || roleNames.length == 0) return false;
        Set<String> roleSet = Set.of(roleNames);
        return roles.stream()
                .anyMatch(role -> roleSet.contains(role.getName()));
    }

    public Set<String> getPermissionNames() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .filter(permission -> permission.getPermission() != null)
                .map(permission -> permission.getPermission().name())
                .collect(Collectors.toSet());
    }

    public Set<String> getRoleNames() {
        return roles.stream()
                .map(PlatformRole::getName)
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
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", status=" + status +
                '}';
    }
}