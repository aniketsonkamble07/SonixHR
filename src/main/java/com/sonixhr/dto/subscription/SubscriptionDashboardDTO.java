package com.sonixhr.dto.subscription;

import java.util.List;
import java.util.Map;

public class SubscriptionDashboardDTO {
    private List<ChartPoint> monthlyRevenue;
    private List<ChartPoint> subscriptionGrowth;
    private List<ChartPoint> planDistribution;
    
    private List<ChartPoint> revenueByPlanChart;
    private List<ChartPoint> churnTrendChart;
    private List<ChartPoint> ltvChart;
    private List<ChartPoint> upgradeDowngradeTrendChart;

    private Map<String, Object> ltvStatistics;
    private Map<String, Object> upgradeDowngradeStats;
    private Map<String, Object> periodComparisons;
    private Map<String, Object> kpiMetrics;
    private Map<String, Object> insightsAndHealth;

    public SubscriptionDashboardDTO() {}

    public SubscriptionDashboardDTO(
            List<ChartPoint> monthlyRevenue,
            List<ChartPoint> subscriptionGrowth,
            List<ChartPoint> planDistribution,
            List<ChartPoint> revenueByPlanChart,
            List<ChartPoint> churnTrendChart,
            List<ChartPoint> ltvChart,
            List<ChartPoint> upgradeDowngradeTrendChart,
            Map<String, Object> ltvStatistics,
            Map<String, Object> upgradeDowngradeStats,
            Map<String, Object> periodComparisons,
            Map<String, Object> kpiMetrics,
            Map<String, Object> insightsAndHealth) {
        this.monthlyRevenue = monthlyRevenue;
        this.subscriptionGrowth = subscriptionGrowth;
        this.planDistribution = planDistribution;
        this.revenueByPlanChart = revenueByPlanChart;
        this.churnTrendChart = churnTrendChart;
        this.ltvChart = ltvChart;
        this.upgradeDowngradeTrendChart = upgradeDowngradeTrendChart;
        this.ltvStatistics = ltvStatistics;
        this.upgradeDowngradeStats = upgradeDowngradeStats;
        this.periodComparisons = periodComparisons;
        this.kpiMetrics = kpiMetrics;
        this.insightsAndHealth = insightsAndHealth;
    }

    public List<ChartPoint> getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(List<ChartPoint> monthlyRevenue) { this.monthlyRevenue = monthlyRevenue; }

    public List<ChartPoint> getSubscriptionGrowth() { return subscriptionGrowth; }
    public void setSubscriptionGrowth(List<ChartPoint> subscriptionGrowth) { this.subscriptionGrowth = subscriptionGrowth; }

    public List<ChartPoint> getPlanDistribution() { return planDistribution; }
    public void setPlanDistribution(List<ChartPoint> planDistribution) { this.planDistribution = planDistribution; }

    public List<ChartPoint> getRevenueByPlanChart() { return revenueByPlanChart; }
    public void setRevenueByPlanChart(List<ChartPoint> revenueByPlanChart) { this.revenueByPlanChart = revenueByPlanChart; }

    public List<ChartPoint> getChurnTrendChart() { return churnTrendChart; }
    public void setChurnTrendChart(List<ChartPoint> churnTrendChart) { this.churnTrendChart = churnTrendChart; }

    public List<ChartPoint> getLtvChart() { return ltvChart; }
    public void setLtvChart(List<ChartPoint> ltvChart) { this.ltvChart = ltvChart; }

    public List<ChartPoint> getUpgradeDowngradeTrendChart() { return upgradeDowngradeTrendChart; }
    public void setUpgradeDowngradeTrendChart(List<ChartPoint> upgradeDowngradeTrendChart) { this.upgradeDowngradeTrendChart = upgradeDowngradeTrendChart; }

    public Map<String, Object> getLtvStatistics() { return ltvStatistics; }
    public void setLtvStatistics(Map<String, Object> ltvStatistics) { this.ltvStatistics = ltvStatistics; }

    public Map<String, Object> getUpgradeDowngradeStats() { return upgradeDowngradeStats; }
    public void setUpgradeDowngradeStats(Map<String, Object> upgradeDowngradeStats) { this.upgradeDowngradeStats = upgradeDowngradeStats; }

    public Map<String, Object> getPeriodComparisons() { return periodComparisons; }
    public void setPeriodComparisons(Map<String, Object> periodComparisons) { this.periodComparisons = periodComparisons; }

    public Map<String, Object> getKpiMetrics() { return kpiMetrics; }
    public void setKpiMetrics(Map<String, Object> kpiMetrics) { this.kpiMetrics = kpiMetrics; }

    public Map<String, Object> getInsightsAndHealth() { return insightsAndHealth; }
    public void setInsightsAndHealth(Map<String, Object> insightsAndHealth) { this.insightsAndHealth = insightsAndHealth; }

    public static class ChartPoint {
        private String label;
        private double value;

        public ChartPoint() {}

        public ChartPoint(String label, double value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
    }
}
