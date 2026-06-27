package com.sonixhr.dto.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.sonixhr.enums.IndianState;
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

    @Size(max = 500, message = "Office address must be up to 500 characters")
    private String officeAddress;

    @Size(max = 100, message = "City name must be up to 100 characters")
    private String city;

    private IndianState state;

    @Size(max = 50, message = "Country name must be up to 50 characters")
    private String country;
}
