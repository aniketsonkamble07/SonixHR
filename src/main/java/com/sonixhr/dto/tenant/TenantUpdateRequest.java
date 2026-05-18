package com.sonixhr.dto.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUpdateRequest {

    @Size(min = 2, max = 200, message = "Company name must be between 2 and 200 characters")
    private String companyName;

    @Email(message = "Invalid email format")
    private String adminEmail;

    @Size(min = 2, max = 200, message = "Admin name must be between 2 and 200 characters")
    private String adminName;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Invalid phone number format")
    private String adminPhone;

    @Pattern(regexp = "^[a-z0-9-]+$", message = "Subdomain can only contain lowercase letters, numbers, and hyphens")
    @Size(min = 3, max = 100, message = "Subdomain must be between 3 and 100 characters")
    private String subdomain;
}
