package com.sonixhr.dto.employee;

import com.sonixhr.enums.employee.EmployeeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EmployeeCreateResponse {
    private Long id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String departmentName;
    private String departmentCode;
    private String position;
    private EmployeeStatus status;
    private LocalDate hireDate;
    private String message;
}
