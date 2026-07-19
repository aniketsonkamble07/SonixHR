package com.sonixhr.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseRole<P extends BasePermission> extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;


    @Column(name = "is_system_role")
    @Builder.Default
    private boolean systemRole = false;

    public boolean isAssignable() {
        return !systemRole;
    }

    public abstract Set<P> getPermissions();
    public abstract void setPermissions(Set<P> permissions);

    @Deprecated(since = "1.0", forRemoval = false)
    public Set<? extends BasePermission> getPermissionsWildcard() {
        return this.getPermissions();
    }

    @SuppressWarnings("unchecked")
    public void setPermissionsWildcard(Set<? extends BasePermission> permissions) {
        this.setPermissions((Set<P>) permissions);
    }
}