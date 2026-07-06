package com.sonixhr.dto.employee;

import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.employee.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    @NotNull(message = "Address type is required")
    private AddressType addressType;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 500)
    private String addressLine1;

    @Size(max = 500)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    private IndianState state;

    @Size(max = 150)
    private String stateText;

    @Size(max = 50)
    private String country;

    @Size(max = 20)
    private String postalCode;

    private boolean isPrimary;
}
