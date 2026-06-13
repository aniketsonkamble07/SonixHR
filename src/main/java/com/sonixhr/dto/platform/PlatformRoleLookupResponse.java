package com.sonixhr.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRoleLookupResponse {
    private Long id;
    private String name;
    private Boolean isSystemRole;
}
