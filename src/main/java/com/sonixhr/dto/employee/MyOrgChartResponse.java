package com.sonixhr.dto.employee;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MyOrgChartResponse {
    private EmployeeSummaryResponse employee;
    private List<EmployeeSummaryResponse> managerChain;
    private List<EmployeeSummaryResponse> peers;
    private List<EmployeeSummaryResponse> directReports;
}
