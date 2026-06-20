package com.sonixhr.dto.platform;

import com.sonixhr.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTenantResponseDTO {

    private Long id;
    private String tenantCode;
    private String companyName;
    private String adminName;
    private String adminEmail;
    private String adminPhone;
    private String planType;
    private UserStatus status;
    private boolean isActive;
    private Integer maxEmployees;
    private String planStatus;
    private LocalDateTime trialEndsAt;
    private LocalDateTime suspendedAt;
    private String suspensionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
