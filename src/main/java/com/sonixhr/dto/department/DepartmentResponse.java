package com.sonixhr.dto.department;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DepartmentResponse {
    private Long id;
    private UUID tenantId;
    private String name;
    private String code;
    private String description;
    private Long employeeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}