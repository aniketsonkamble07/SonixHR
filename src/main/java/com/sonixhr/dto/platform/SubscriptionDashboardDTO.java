package com.sonixhr.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDashboardDTO {
    private long freeTrialCount;
    private long basicPlanCount;
    private long moderatePlanCount;
    private long premiumPlanCount;
    private long enterprisePlanCount;
    
    private List<ChartPoint> monthlyRevenue;
    private List<ChartPoint> subscriptionGrowth;
    private List<ChartPoint> planDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartPoint {
        private String label;
        private double value;
    }
}
