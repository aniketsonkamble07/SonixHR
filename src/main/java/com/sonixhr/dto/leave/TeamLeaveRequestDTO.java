package com.sonixhr.dto.leave;

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
public class TeamLeaveRequestDTO {

    private Long managerId;
    private String managerName;
    private List<TeamLeaveSummary> teamLeaves;
    private LeaveStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamLeaveSummary {
        private Long employeeId;
        private String employeeName;
        private String employeeCode;
        private List<LeaveResponseDTO> leaves;
        private double totalLeaveDays;
        private int pendingCount;
        private int approvedCount;
        private int rejectedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveStatistics {
        private int totalPending;
        private int totalApproved;
        private int totalRejected;
        private int totalCancelled;
        private double totalLeaveDays;
        private int employeesOnLeaveToday;
        private int employeesOnLeaveThisWeek;
        private int employeesOnLeaveThisMonth;
    }
}