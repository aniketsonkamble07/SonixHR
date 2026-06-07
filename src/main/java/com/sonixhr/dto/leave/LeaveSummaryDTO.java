package com.sonixhr.dto.leave;

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
public class LeaveSummaryDTO {

    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private LocalDate startDate;
    private LocalDate endDate;

    private LeaveBalanceDTO leaveBalance;
    private LeaveStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveStatistics {
        private int totalLeavesTaken;
        private int pendingLeaves;
        private int approvedLeaves;
        private int rejectedLeaves;
        private int cancelledLeaves;
        private double totalLeaveDays;
        private Map<String, Double> leaveTypeBreakdown;
    }
}