package com.sonixhr.dto;

import com.sonixhr.enums.PlanType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateRequest {

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 200, message = "Company name must be between 2 and 200 characters")
    private String companyName;

    @NotBlank(message = "Subdomain is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Subdomain can only contain lowercase letters, numbers, and hyphens")
    @Size(min = 3, max = 100, message = "Subdomain must be between 3 and 100 characters")
    private String subdomain;

    @NotNull(message = "Plan type is required")
    private PlanType planType;

    @Email(message = "Invalid admin email format")
    @NotBlank(message = "Admin email is required")
    private String adminEmail;

    @NotBlank(message = "Admin name is required")
    private String adminName;

    private LocalDateTime trialEndsAt;
}