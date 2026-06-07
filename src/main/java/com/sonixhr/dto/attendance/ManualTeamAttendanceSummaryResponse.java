package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualTeamAttendanceSummaryResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private int totalTeamMembers;
    private Map<String, Object> teamTotals;
    private Map<String, Map<String, Object>> employeeSummaries;
    private String message;
}