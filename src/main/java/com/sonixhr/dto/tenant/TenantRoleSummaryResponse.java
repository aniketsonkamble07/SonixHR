package com.sonixhr.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRoleSummaryResponse {

    private Long id;
    private String name;
    private String description;
    private Boolean isDefault;
    private Integer employeeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
