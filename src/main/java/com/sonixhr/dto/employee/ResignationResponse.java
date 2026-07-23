package com.sonixhr.dto.employee;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.ResignationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResignationResponse {

    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String departmentName;
    private String position;
    private EmployeeStatus status;
    private ResignationStatus resignationStatus;
    private String resignationReason;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate resignationDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate proposedLastWorkingDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate approvedLastWorkingDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastWorkingDate;

    private boolean isResignationAccepted;
}
