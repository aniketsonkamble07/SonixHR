package com.sonixhr.dto.employee;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeUpdateRequest extends EmployeeCreateRequest {
    private LocalDate dateOfBirth;
    private com.sonixhr.enums.Gender gender;
    private com.sonixhr.enums.MaritalStatus maritalStatus;
    private com.sonixhr.enums.BloodGroup bloodGroup;
    private String nationality;
    private String personalEmail;
}
