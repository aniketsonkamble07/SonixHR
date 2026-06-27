package com.sonixhr.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRoleDeletePreviewResponse {
    private Long roleId;
    private String roleName;
    private Integer affectedUserCount;
    private List<PlatformUserResponse> affectedUsers;
    private List<PlatformRoleLookupResponse> reassignmentOptions;
    private boolean deletable;
    private String validationMessage;
}
