package com.sonixhr.dto.employee;

import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.employee.AddressType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressResponse {
    private Long id;
    private AddressType addressType;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private IndianState state;
    private String stateText;
    private String country;
    private String postalCode;
    private boolean isPrimary;
}
