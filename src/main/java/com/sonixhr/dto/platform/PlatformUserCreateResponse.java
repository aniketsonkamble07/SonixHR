package com.sonixhr.dto.platform;

import com.sonixhr.enums.PlatformUserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUserCreateResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String designation;
    private PlatformUserStatus status;
    private boolean isActive;

    private Set<PlatformRoleResponse> roles;

    private String invitationLink;
    private LocalDateTime invitationExpiryAt;

    private LocalDateTime createdAt;

    // ===== Nested DTOs =====

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformRoleResponse {
        private Long id;
        private String name;
        private String description;
        private List<PlatformPermissionResponse> permissions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformPermissionResponse {
        private Long id;
        private String name;
        private String description;
    }
}