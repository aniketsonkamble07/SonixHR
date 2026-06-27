package com.sonixhr.dto.tenant;

import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRoleDeletePreviewResponse {
    private Long roleId;
    private String roleName;
    private Integer affectedEmployeeCount;
    private List<EmployeeSummaryResponse> affectedEmployees;
    private List<TenantRoleLookupResponse> reassignmentOptions;
    private boolean deletable;
    private String validationMessage;
}
