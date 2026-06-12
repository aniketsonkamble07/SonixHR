package com.sonixhr.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationResponse {

    // Success/Error info
    private boolean success;
    private String message;

    // Tenant info
    private Long tenantId;
    private String tenantCode;
    private String companyName;
    private String subdomain;

    // Plan info
    private String planType;
    private String planStatus;
    private LocalDateTime trialEndsAt;

    // Status
    private String status;
    private Boolean isActive;

    // Admin info
    private String adminEmail;
    private String adminName;
    private String adminPhone;

    // Activation info
    private String activationToken;  // For development/testing
    private LocalDateTime activationTokenExpiry;

    // =====================================================
    // SUPER ADMIN EMPLOYEE INFO (ADD THIS)
    // =====================================================
    private Long superAdminEmployeeId;
    private String superAdminEmployeeCode;
    private String superAdminFullName;
    private String superAdminEmail;
    private String superAdminPosition;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // Helper method for success response
    public static TenantRegistrationResponse success(String message, Long tenantId, String subdomain,
                                                     String planType, LocalDateTime trialEndsAt,
                                                     String activationToken) {
        return TenantRegistrationResponse.builder()
                .success(true)
                .message(message)
                .tenantId(tenantId)
                .subdomain(subdomain)
                .planType(planType)
                .planStatus("trial")
                .trialEndsAt(trialEndsAt)
                .status("ACTIVE")
                .isActive(true)
                .activationToken(activationToken)
                .activationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();
    }

    // NEW: Success response with Super Admin employee details
    public static TenantRegistrationResponse successWithEmployee(String message, Long tenantId,
                                                                 String subdomain, String planType,
                                                                 LocalDateTime trialEndsAt, String activationToken,
                                                                 Long employeeId, String employeeCode,
                                                                 String fullName, String email) {
        return TenantRegistrationResponse.builder()
                .success(true)
                .message(message)
                .tenantId(tenantId)
                .subdomain(subdomain)
                .planType(planType)
                .planStatus("trial")
                .trialEndsAt(trialEndsAt)
                .status("ACTIVE")
                .isActive(true)
                .activationToken(activationToken)
                .activationTokenExpiry(LocalDateTime.now().plusHours(24))
                .superAdminEmployeeId(employeeId)
                .superAdminEmployeeCode(employeeCode)
                .superAdminFullName(fullName)
                .superAdminEmail(email)
                .superAdminPosition("Super Admin")
                .build();
    }

    // Helper method for error response
    public static TenantRegistrationResponse error(String message) {
        return TenantRegistrationResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}