package com.sonixhr.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class PlatformRoleCreateRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private Set<Long> permissionIds;
}