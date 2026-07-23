package com.sonixhr.dto.tenant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.PlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationResponse {

    private boolean success;
    private String message;

    // Tenant details
    private Long tenantId;
    private String tenantCode;
    private String companyName;
    private String companyEmail;
    private String companyPhone;
    private String postalCode;
    private String planType;
    private String planStatus;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endsAt;

    private String status;
    private boolean isActive;

    // Admin details
    private String adminEmail;
    private String adminName;
    private String adminPhone;
    private String officeAddress;
    private String city;
    private IndianState state;
    private String stateText;
    private String country;

    // Activation details
    private String activationToken;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime activationTokenExpiry;

    // Admin Employee details (NOT Super Admin)
    private Long adminEmployeeId;
    private String adminEmployeeCode;
    private String adminFullName;
    private String adminPosition;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}