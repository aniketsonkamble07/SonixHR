package com.sonixhr.dto.employee;

import com.sonixhr.enums.employee.EmployeeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSearchResponse {
    private Long id;
    private String employeeCode;
    private String fullName;
    private String email;
    private String position;
    private String department;
    private EmployeeStatus status;
    private String currentManager;
    private String profilePictureUrl;
}