package com.sonixhr.dto.employee;

import com.sonixhr.enums.employee.EmployeeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EmployeeSummaryResponse {

    private Long id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private String position;
    private String departmentName;
    private EmployeeStatus status;
    private Boolean isActive;
    private String profilePictureUrl;
    private LocalDate hireDate;
    private String managerName;
    
    // Direct reports for hierarchical queries (org chart)
    private List<EmployeeSummaryResponse> directReports;
}
