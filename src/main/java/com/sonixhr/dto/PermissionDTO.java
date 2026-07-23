package com.sonixhr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {
    private Long id;
    private String permission;
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