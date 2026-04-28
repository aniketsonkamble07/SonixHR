package com.sonixhr.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.PlanType;
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
public class TenantResponse {
    private UUID id;
    private String tenantCode;
    private String companyName;
    private String subdomain;
    private PlanType planType;
    private PlanStatus planStatus;
    private String adminEmail;
    private String adminName;
    private Integer maxEmployees;
    private Integer maxStorageMb;
    private Integer currentEmployees;
    private Double storageUsedPercentage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime trialEndsAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime suspendedAt;
}