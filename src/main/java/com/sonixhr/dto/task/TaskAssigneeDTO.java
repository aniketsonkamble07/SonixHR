package com.sonixhr.dto.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssigneeDTO {
    private Long id;
    private String fullName;
    private String employeeCode;
    private String departmentName;
    private String designation;
}
