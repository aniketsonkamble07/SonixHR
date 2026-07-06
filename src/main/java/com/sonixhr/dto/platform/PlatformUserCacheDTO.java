package com.sonixhr.dto.platform;




import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.UserStatus;

import java.io.Serializable;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUserCacheDTO implements Serializable {
    private Long id;
    private String email;
    private String fullName;
    private String designation;
    private UserStatus status;
    private boolean active;
    private Set<String> roleNames;
    private Set<String> permissions;
    private int rolesVersion;

    public static PlatformUserCacheDTO fromUser(PlatformUser user) {
        return PlatformUserCacheDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .designation(user.getDesignation())
                .status(user.getStatus())
                .active(user.isActive())
                .roleNames(user.getRoleNames())
                .permissions(user.getPermissionNames())
                .rolesVersion(user.getRolesVersion())
                .build();
    }
}
