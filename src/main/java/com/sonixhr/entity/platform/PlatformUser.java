package com.sonixhr.entity.platform;


import com.sonixhr.enums.PlatformUserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "platform_users", indexes = {
        @Index(name = "idx_platform_users_email", columnList = "email"),
        @Index(name = "idx_platform_users_status", columnList = "status"),
        @Index(name = "idx_platform_users_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 100)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private PlatformUserStatus status = PlatformUserStatus.ACTIVE;

    @Builder.Default
    private boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Builder.Default
    private boolean mustChangePassword = false;

    @Builder.Default
    private boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ✅ Many-to-Many with PlatformRole (dynamic roles)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "platform_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<PlatformRole> roles = new HashSet<>();

    // ===== Helper Methods =====

    public Set<String> getEffectivePermissions() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName().name())
                .collect(Collectors.toSet());
    }

    public boolean hasPermission(String permissionName) {
        return getEffectivePermissions().contains(permissionName);
    }

    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
            this.status = PlatformUserStatus.LOCKED;
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        if (this.status == PlatformUserStatus.LOCKED) {
            this.status = PlatformUserStatus.ACTIVE;
        }
    }

    public void softDelete() {
        this.active = false;
        this.status = PlatformUserStatus.INACTIVE;
        this.deletedAt = LocalDateTime.now();
    }

    public void activate() {
        this.active = true;
        this.status = PlatformUserStatus.ACTIVE;
        this.deletedAt = null;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void suspend() {
        this.active = false;
        this.status = PlatformUserStatus.SUSPENDED;
    }

    // ===== UserDetails Implementation =====

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getEffectivePermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return passwordHash;
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
        return !isAccountLocked() && status != PlatformUserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active && status == PlatformUserStatus.ACTIVE;
    }
}