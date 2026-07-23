package com.sonixhr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionGroupDTO {

    private String groupName;
    private List<PermissionInfo> permissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionInfo {
        private Long id;
        private String name;
        private String description;
        private String category;
        private Integer displayOrder;
        private Boolean selected;

        // Permission metadata flags
        @Builder.Default
        private boolean billingPermission = false;

        @Builder.Default
        private boolean platformAdminPermission = false;

        @Builder.Default
        private boolean viewPermission = false;

        @Builder.Default
        private boolean writePermission = false;

        @Builder.Default
        private boolean exportPermission = false;

        private String permissionType; // DATA_VIEW, DATA_CREATE, DATA_EDIT, etc.
    }
}