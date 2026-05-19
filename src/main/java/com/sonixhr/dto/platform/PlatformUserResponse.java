package com.sonixhr.dto.platform;



import com.sonixhr.enums.PlatformUserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class PlatformUserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String designation;
    private PlatformUserStatus status;
    private Boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String invitationLink;
    private LocalDateTime invitationExpiryAt;
    private Set<PlatformRoleResponse> roles;

    @Data
    @Builder
    public static class PlatformRoleResponse {
        private Long id;
        private String name;
        private String description;
    }
}