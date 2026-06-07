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

    private String permission;  // ✅ Changed from 'name' to 'permission' (matches entity)

    private String description;

    private String category;

    private Integer displayOrder;  // ✅ Added for sorting

    private boolean selected;  // For role assignment UI

    // ✅ Optional: Add if you have icons in your system
    // private String icon;

    // Static factory method for easy creation from entity
    public static PermissionDTO fromEntity(com.sonixhr.entity.employee.EmployeePermission entity) {
        if (entity == null) {
            return null;
        }

        return PermissionDTO.builder()
                .id(entity.getId())
                .permission(entity.getPermission() != null ? entity.getPermission().name() : null)
                .description(entity.getDescription())
                .category(entity.getCategory())
                .displayOrder(entity.getDisplayOrder())
                .selected(false)
                .build();
    }

    // For enum-based permissions
    public static PermissionDTO fromEnum(com.sonixhr.enums.TenantPermissionEnum permissionEnum) {
        if (permissionEnum == null) {
            return null;
        }

        return PermissionDTO.builder()
                .permission(permissionEnum.name())
                .description(permissionEnum.getDescription())
                .category(permissionEnum.getCategory())
                .displayOrder(permissionEnum.getOrder())
                .selected(false)
                .build();
    }
}