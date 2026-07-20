package com.sonixhr.common.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseUser extends BaseEntity implements UserDetails {

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;


    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "roles_version")
    @Builder.Default
    private Integer rolesVersion = 1;

    @Transient
    private Collection<? extends GrantedAuthority> cachedAuthorities;

    @Transient
    private Integer cachedRolesVersion;

    public void incrementRolesVersion() {
        this.rolesVersion = (this.rolesVersion == null ? 1 : this.rolesVersion + 1);
    }

    public void clearAuthoritiesCache() {
        this.cachedAuthorities = null;
        this.cachedRolesVersion = null;
    }



    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (cachedAuthorities != null && cachedRolesVersion != null &&
                cachedRolesVersion.equals(this.rolesVersion)) {
            return cachedAuthorities;
        }

        Set<GrantedAuthority> authorities = new HashSet<>();
        loadAuthorities(authorities);

        this.cachedAuthorities = authorities;
        this.cachedRolesVersion = this.rolesVersion;

        return authorities;
    }

    protected abstract void loadAuthorities(Set<GrantedAuthority> authorities);

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
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return super.isActive();  // Use BaseEntity's active field
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Integer getRolesVersion() {
        return rolesVersion;
    }

    public void setRolesVersion(Integer rolesVersion) {
        this.rolesVersion = rolesVersion;
    }

    public Collection<? extends GrantedAuthority> getCachedAuthorities() {
        return cachedAuthorities;
    }

    public void setCachedAuthorities(Collection<? extends GrantedAuthority> cachedAuthorities) {
        this.cachedAuthorities = cachedAuthorities;
    }

    public Integer getCachedRolesVersion() {
        return cachedRolesVersion;
    }

    public void setCachedRolesVersion(Integer cachedRolesVersion) {
        this.cachedRolesVersion = cachedRolesVersion;
    }
}