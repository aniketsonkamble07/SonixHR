package com.sonixhr.dto.platform;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PlatformRoleResponse {
    private Long id;
    private String name;
    private String description;
    private List<PermissionInfo> permissions;

    @Data
    @Builder
    public static class PermissionInfo {
        private Long id;
        private String name;
        private String description;


    }
}