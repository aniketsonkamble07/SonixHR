package com.sonixhr.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBalanceDTO {

    private Map<String, LeaveTypeBalance> balances;
    private LeaveSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveTypeBalance {
        private double total;
        private double used;
        private double remaining;
        private String color;
        private String leaveTypeName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveSummary {
        private double totalUsed;
        private double totalAvailable;
        private double remaining;
        private double utilizationPercentage;
        private String message;
    }
}