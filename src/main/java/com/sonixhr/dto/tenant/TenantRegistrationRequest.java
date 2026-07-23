package com.sonixhr.dto.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sonixhr.enums.IndianState;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantRegistrationRequest {

    // =====================================================
    // COMPANY DETAILS
    // =====================================================

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 200, message = "Company name must be between 2 and 200 characters")
    private String companyName;

    @NotBlank(message = "Company email is required")
    @Email(message = "Invalid email format")
    private String companyEmail;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Invalid phone number format")
    private String phone;

    @Size(max = 500, message = "Address must be up to 500 characters")
    private String address;

    @Size(max = 100, message = "City must be up to 100 characters")
    private String city;

    private IndianState state;

    @Size(max = 100, message = "State text must be up to 100 characters")
    private String stateText;

    @Size(max = 50, message = "Country must be up to 50 characters")
    private String country;

    @Size(max = 20, message = "Postal code must be up to 20 characters")
    private String postalCode;

    // =====================================================
    // ADMIN USER DETAILS
    // =====================================================

    @NotBlank(message = "Admin first name is required")
    @Size(min = 2, max = 100, message = "Admin first name must be between 2 and 100 characters")
    private String adminFirstName;

    @NotBlank(message = "Admin last name is required")
    @Size(min = 2, max = 100, message = "Admin last name must be between 2 and 100 characters")
    private String adminLastName;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid email format")
    private String adminEmail;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Invalid phone number format")
    private String adminPhone;

    // =====================================================
    // PLAN SELECTION
    // =====================================================

    @JsonAlias({"planType", "planCode"})
    private String planCode;

    // =====================================================
    // UTILITY METHODS
    // =====================================================

    public String getAdminFullName() {
        return (adminFirstName != null ? adminFirstName : "") +
                (adminLastName != null ? " " + adminLastName : "");
    }

    public String getAdminName() {
        return getAdminFullName();
    }
}