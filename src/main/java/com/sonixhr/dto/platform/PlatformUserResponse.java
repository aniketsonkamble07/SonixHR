package com.sonixhr.dto.platform;



import com.sonixhr.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String designation;
    private UserStatus status;
    private Boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String invitationLink;
    private LocalDateTime invitationExpiryAt;
    private Set<PlatformRoleResponse> roles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformRoleResponse {
        private Long id;
        private String name;
        private String description;
    }
}