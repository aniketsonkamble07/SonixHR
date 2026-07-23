package com.sonixhr.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionHistorySummaryDTO {

    // =====================================================
    // EVENT COUNTS
    // =====================================================

    private Long totalSubscriptionEvents;
    private Long totalPlanOperations;
    private Long totalSubscriptionOperations;

    // Subscription event counts
    private Long totalActivations;
    private Long totalRenewals;
    private Long totalUpgrades;
    private Long totalDowngrades;
    private Long totalCancellations;
    private Long totalSuspensions;
    private Long totalReactivations;
    private Long totalExpirations;
    private Long totalPauses;
    private Long totalResumes;

    // Plan operation counts
    private Long totalPlanCreated;
    private Long totalPlanUpdated;
    private Long totalPlanDeleted;
    private Long totalPlanRestored;
    private Long totalPlanToggled;

    // =====================================================
    // FINANCIAL SUMMARY
    // =====================================================

    private BigDecimal totalAmountSpent;
    private BigDecimal totalRevenue;
    private BigDecimal averageAmountPerEvent;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String currency;

    // Recurring revenue
    private BigDecimal monthlyRecurringRevenue;
    private BigDecimal annualRecurringRevenue;
    private BigDecimal quarterlyRecurringRevenue;

    // =====================================================
    // TIME-BASED ANALYSIS
    // =====================================================

    private Map<String, Long> eventsByMonth;
    private Map<String, Long> eventsByYear;
    private Map<String, Long> eventsByDayOfWeek;
    private Map<String, Long> eventsByHour;

    private Map<String, Long> eventsByType;
    private Map<String, Long> eventsByPlan;
    private Map<String, Long> eventsByPlanCode;
    private Map<String, Long> eventsByStatus;
    private Map<String, Long> eventsByTriggerSource;

    // =====================================================
    // CURRENT STATE
    // =====================================================

    private Long currentActiveSubscriptionCount;
    private Long currentSuspendedCount;
    private Long currentExpiredCount;
    private Long currentCancelledCount;
    private Long currentPausedCount;
    private Long currentActivePlanCount;
    private Long currentDeletedPlanCount;

    // =====================================================
    // DATE RANGES
    // =====================================================

    private LocalDateTime firstSubscriptionDate;
    private LocalDateTime lastSubscriptionDate;
    private LocalDateTime firstPlanCreatedDate;
    private LocalDateTime lastPlanUpdatedDate;
    private LocalDateTime analysisStartDate;
    private LocalDateTime analysisEndDate;

    // =====================================================
    // STATISTICAL ANALYSIS
    // =====================================================

    private Double averageSubscriptionDurationDays;
    private Double averagePlanLifetimeDays;
    private Integer totalUniquePlansUsed;
    private Integer totalUniquePlansEverCreated;
    private Integer totalMonthsActive;
    private Integer totalYearsActive;
    private Integer totalDaysAnalyzed;

    // =====================================================
    // TRENDING DATA
    // =====================================================

    private BigDecimal averageMonthlySpend;
    private BigDecimal averageYearlySpend;
    private Double monthlyGrowthRate;
    private Double yearlyGrowthRate;
    private Double churnRate;
    private Double retentionRate;
    private Double conversionRate;

    // =====================================================
    // MOST COMMON / TOP ITEMS
    // =====================================================

    private String mostCommonEventType;
    private String mostCommonPlanType;
    private String mostCommonPlanCode;
    private String mostCommonStatus;
    private String mostCommonTriggerSource;
    private String mostCommonCancellationType;

    // =====================================================
    // PLAN CHANGE ANALYSIS
    // =====================================================

    private Long totalPlanChanges;
    private Long totalPriceIncreases;
    private Long totalPriceDecreases;
    private Long totalEmployeeLimitIncreases;
    private Long totalEmployeeLimitDecreases;
    private Long totalFeatureAdditions;
    private Long totalFeatureRemovals;

    private BigDecimal averagePriceIncrease;
    private BigDecimal averagePriceDecrease;
    private Integer averageEmployeeLimitIncrease;
    private Integer averageEmployeeLimitDecrease;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Get the most active month
     */
    public Map.Entry<String, Long> getMostActiveMonth() {
        return eventsByMonth != null ?
                eventsByMonth.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .orElse(null) : null;
    }

    /**
     * Get the least active month
     */
    public Map.Entry<String, Long> getLeastActiveMonth() {
        return eventsByMonth != null ?
                eventsByMonth.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .orElse(null) : null;
    }

    /**
     * Calculate total events
     */
    public Long getTotalEvents() {
        return (totalSubscriptionEvents != null ? totalSubscriptionEvents : 0L) +
                (totalPlanOperations != null ? totalPlanOperations : 0L);
    }

    /**
     * Get percentage of subscription events
     */
    public Double getSubscriptionEventPercentage() {
        Long total = getTotalEvents();
        if (total == 0) return 0.0;
        return (totalSubscriptionEvents != null ? totalSubscriptionEvents.doubleValue() : 0.0) / total * 100;
    }

    /**
     * Get percentage of plan operations
     */
    public Double getPlanOperationPercentage() {
        Long total = getTotalEvents();
        if (total == 0) return 0.0;
        return (totalPlanOperations != null ? totalPlanOperations.doubleValue() : 0.0) / total * 100;
    }

    /**
     * Check if there are any events
     */
    public boolean hasEvents() {
        return getTotalEvents() > 0;
    }

    /**
     * Get summary for display
     */
    public Map<String, Object> getDisplaySummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEvents", getTotalEvents());
        summary.put("subscriptionEvents", totalSubscriptionEvents);
        summary.put("planOperations", totalPlanOperations);
        summary.put("totalRevenue", totalRevenue);
        summary.put("mrr", monthlyRecurringRevenue);
        summary.put("arr", annualRecurringRevenue);
        summary.put("activeSubscriptions", currentActiveSubscriptionCount);
        summary.put("churnRate", churnRate);
        summary.put("retentionRate", retentionRate);
        summary.put("mostCommonEvent", mostCommonEventType);
        summary.put("mostCommonPlan", mostCommonPlanType);
        summary.put("analysisPeriod", analysisStartDate + " to " + analysisEndDate);
        return summary;
    }

    /**
     * Get churn rate as percentage string
     */
    public String getChurnRateDisplay() {
        if (churnRate == null) return "0%";
        return String.format("%.2f%%", churnRate);
    }

    /**
     * Get retention rate as percentage string
     */
    public String getRetentionRateDisplay() {
        if (retentionRate == null) return "0%";
        return String.format("%.2f%%", retentionRate);
    }

    /**
     * Get monthly growth rate as percentage string
     */
    public String getMonthlyGrowthRateDisplay() {
        if (monthlyGrowthRate == null) return "0%";
        return String.format("%.2f%%", monthlyGrowthRate);
    }

    /**
     * Format currency amount
     */
    public String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%.2f", amount);
    }

    /**
     * Get average monthly spend display
     */
    public String getAverageMonthlySpendDisplay() {
        return formatCurrency(averageMonthlySpend);
    }

    /**
     * Get total revenue display
     */
    public String getTotalRevenueDisplay() {
        return formatCurrency(totalRevenue);
    }

    /**
     * Get MRR display
     */
    public String getMRRDisplay() {
        return formatCurrency(monthlyRecurringRevenue);
    }

    /**
     * Get ARR display
     */
    public String getARRDisplay() {
        return formatCurrency(annualRecurringRevenue);
    }

    /**
     * Build summary with default values
     */
    public static SubscriptionHistorySummaryDTO empty() {
        return SubscriptionHistorySummaryDTO.builder()
                .totalSubscriptionEvents(0L)
                .totalPlanOperations(0L)
                .totalSubscriptionOperations(0L)
                .totalActivations(0L)
                .totalRenewals(0L)
                .totalUpgrades(0L)
                .totalDowngrades(0L)
                .totalCancellations(0L)
                .totalSuspensions(0L)
                .totalReactivations(0L)
                .totalExpirations(0L)
                .totalPauses(0L)
                .totalResumes(0L)
                .totalPlanCreated(0L)
                .totalPlanUpdated(0L)
                .totalPlanDeleted(0L)
                .totalPlanRestored(0L)
                .totalPlanToggled(0L)
                .totalAmountSpent(BigDecimal.ZERO)
                .totalRevenue(BigDecimal.ZERO)
                .averageAmountPerEvent(BigDecimal.ZERO)
                .minAmount(BigDecimal.ZERO)
                .maxAmount(BigDecimal.ZERO)
                .currency("INR")
                .monthlyRecurringRevenue(BigDecimal.ZERO)
                .annualRecurringRevenue(BigDecimal.ZERO)
                .quarterlyRecurringRevenue(BigDecimal.ZERO)
                .eventsByMonth(new HashMap<>())
                .eventsByYear(new HashMap<>())
                .eventsByDayOfWeek(new HashMap<>())
                .eventsByHour(new HashMap<>())
                .eventsByType(new HashMap<>())
                .eventsByPlan(new HashMap<>())
                .eventsByPlanCode(new HashMap<>())
                .eventsByStatus(new HashMap<>())
                .eventsByTriggerSource(new HashMap<>())
                .currentActiveSubscriptionCount(0L)
                .currentSuspendedCount(0L)
                .currentExpiredCount(0L)
                .currentCancelledCount(0L)
                .currentPausedCount(0L)
                .currentActivePlanCount(0L)
                .currentDeletedPlanCount(0L)
                .totalUniquePlansUsed(0)
                .totalUniquePlansEverCreated(0)
                .totalMonthsActive(0)
                .totalYearsActive(0)
                .totalDaysAnalyzed(0)
                .averageMonthlySpend(BigDecimal.ZERO)
                .averageYearlySpend(BigDecimal.ZERO)
                .monthlyGrowthRate(0.0)
                .yearlyGrowthRate(0.0)
                .churnRate(0.0)
                .retentionRate(0.0)
                .conversionRate(0.0)
                .totalPlanChanges(0L)
                .totalPriceIncreases(0L)
                .totalPriceDecreases(0L)
                .totalEmployeeLimitIncreases(0L)
                .totalEmployeeLimitDecreases(0L)
                .totalFeatureAdditions(0L)
                .totalFeatureRemovals(0L)
                .averagePriceIncrease(BigDecimal.ZERO)
                .averagePriceDecrease(BigDecimal.ZERO)
                .averageEmployeeLimitIncrease(0)
                .averageEmployeeLimitDecrease(0)
                .build();
    }

    /**
     * Merge two summaries
     */
    public SubscriptionHistorySummaryDTO merge(SubscriptionHistorySummaryDTO other) {
        if (other == null) return this;

        return SubscriptionHistorySummaryDTO.builder()
                .totalSubscriptionEvents(mergeLong(totalSubscriptionEvents, other.totalSubscriptionEvents))
                .totalPlanOperations(mergeLong(totalPlanOperations, other.totalPlanOperations))
                .totalSubscriptionOperations(mergeLong(totalSubscriptionOperations, other.totalSubscriptionOperations))
                .totalActivations(mergeLong(totalActivations, other.totalActivations))
                .totalRenewals(mergeLong(totalRenewals, other.totalRenewals))
                .totalUpgrades(mergeLong(totalUpgrades, other.totalUpgrades))
                .totalDowngrades(mergeLong(totalDowngrades, other.totalDowngrades))
                .totalCancellations(mergeLong(totalCancellations, other.totalCancellations))
                .totalSuspensions(mergeLong(totalSuspensions, other.totalSuspensions))
                .totalReactivations(mergeLong(totalReactivations, other.totalReactivations))
                .totalExpirations(mergeLong(totalExpirations, other.totalExpirations))
                .totalPauses(mergeLong(totalPauses, other.totalPauses))
                .totalResumes(mergeLong(totalResumes, other.totalResumes))
                .totalPlanCreated(mergeLong(totalPlanCreated, other.totalPlanCreated))
                .totalPlanUpdated(mergeLong(totalPlanUpdated, other.totalPlanUpdated))
                .totalPlanDeleted(mergeLong(totalPlanDeleted, other.totalPlanDeleted))
                .totalPlanRestored(mergeLong(totalPlanRestored, other.totalPlanRestored))
                .totalPlanToggled(mergeLong(totalPlanToggled, other.totalPlanToggled))
                .totalAmountSpent(mergeBigDecimal(totalAmountSpent, other.totalAmountSpent))
                .totalRevenue(mergeBigDecimal(totalRevenue, other.totalRevenue))
                .averageAmountPerEvent(mergeBigDecimal(averageAmountPerEvent, other.averageAmountPerEvent))
                .minAmount(mergeBigDecimal(minAmount, other.minAmount))
                .maxAmount(mergeBigDecimal(maxAmount, other.maxAmount))
                .currency(other.currency != null ? other.currency : currency)
                .monthlyRecurringRevenue(mergeBigDecimal(monthlyRecurringRevenue, other.monthlyRecurringRevenue))
                .annualRecurringRevenue(mergeBigDecimal(annualRecurringRevenue, other.annualRecurringRevenue))
                .quarterlyRecurringRevenue(mergeBigDecimal(quarterlyRecurringRevenue, other.quarterlyRecurringRevenue))
                .eventsByMonth(mergeMaps(eventsByMonth, other.eventsByMonth))
                .eventsByYear(mergeMaps(eventsByYear, other.eventsByYear))
                .eventsByDayOfWeek(mergeMaps(eventsByDayOfWeek, other.eventsByDayOfWeek))
                .eventsByHour(mergeMaps(eventsByHour, other.eventsByHour))
                .eventsByType(mergeMaps(eventsByType, other.eventsByType))
                .eventsByPlan(mergeMaps(eventsByPlan, other.eventsByPlan))
                .eventsByPlanCode(mergeMaps(eventsByPlanCode, other.eventsByPlanCode))
                .eventsByStatus(mergeMaps(eventsByStatus, other.eventsByStatus))
                .eventsByTriggerSource(mergeMaps(eventsByTriggerSource, other.eventsByTriggerSource))
                .currentActiveSubscriptionCount(mergeLong(currentActiveSubscriptionCount, other.currentActiveSubscriptionCount))
                .currentSuspendedCount(mergeLong(currentSuspendedCount, other.currentSuspendedCount))
                .currentExpiredCount(mergeLong(currentExpiredCount, other.currentExpiredCount))
                .currentCancelledCount(mergeLong(currentCancelledCount, other.currentCancelledCount))
                .currentPausedCount(mergeLong(currentPausedCount, other.currentPausedCount))
                .currentActivePlanCount(mergeLong(currentActivePlanCount, other.currentActivePlanCount))
                .currentDeletedPlanCount(mergeLong(currentDeletedPlanCount, other.currentDeletedPlanCount))
                .firstSubscriptionDate(other.firstSubscriptionDate != null ? other.firstSubscriptionDate : firstSubscriptionDate)
                .lastSubscriptionDate(other.lastSubscriptionDate != null ? other.lastSubscriptionDate : lastSubscriptionDate)
                .firstPlanCreatedDate(other.firstPlanCreatedDate != null ? other.firstPlanCreatedDate : firstPlanCreatedDate)
                .lastPlanUpdatedDate(other.lastPlanUpdatedDate != null ? other.lastPlanUpdatedDate : lastPlanUpdatedDate)
                .analysisStartDate(other.analysisStartDate != null ? other.analysisStartDate : analysisStartDate)
                .analysisEndDate(other.analysisEndDate != null ? other.analysisEndDate : analysisEndDate)
                .averageSubscriptionDurationDays(mergeDouble(averageSubscriptionDurationDays, other.averageSubscriptionDurationDays))
                .averagePlanLifetimeDays(mergeDouble(averagePlanLifetimeDays, other.averagePlanLifetimeDays))
                .totalUniquePlansUsed(mergeInteger(totalUniquePlansUsed, other.totalUniquePlansUsed))
                .totalUniquePlansEverCreated(mergeInteger(totalUniquePlansEverCreated, other.totalUniquePlansEverCreated))
                .totalMonthsActive(mergeInteger(totalMonthsActive, other.totalMonthsActive))
                .totalYearsActive(mergeInteger(totalYearsActive, other.totalYearsActive))
                .totalDaysAnalyzed(mergeInteger(totalDaysAnalyzed, other.totalDaysAnalyzed))
                .averageMonthlySpend(mergeBigDecimal(averageMonthlySpend, other.averageMonthlySpend))
                .averageYearlySpend(mergeBigDecimal(averageYearlySpend, other.averageYearlySpend))
                .monthlyGrowthRate(mergeDouble(monthlyGrowthRate, other.monthlyGrowthRate))
                .yearlyGrowthRate(mergeDouble(yearlyGrowthRate, other.yearlyGrowthRate))
                .churnRate(mergeDouble(churnRate, other.churnRate))
                .retentionRate(mergeDouble(retentionRate, other.retentionRate))
                .conversionRate(mergeDouble(conversionRate, other.conversionRate))
                .mostCommonEventType(other.mostCommonEventType != null ? other.mostCommonEventType : mostCommonEventType)
                .mostCommonPlanType(other.mostCommonPlanType != null ? other.mostCommonPlanType : mostCommonPlanType)
                .mostCommonPlanCode(other.mostCommonPlanCode != null ? other.mostCommonPlanCode : mostCommonPlanCode)
                .mostCommonStatus(other.mostCommonStatus != null ? other.mostCommonStatus : mostCommonStatus)
                .mostCommonTriggerSource(other.mostCommonTriggerSource != null ? other.mostCommonTriggerSource : mostCommonTriggerSource)
                .mostCommonCancellationType(other.mostCommonCancellationType != null ? other.mostCommonCancellationType : mostCommonCancellationType)
                .totalPlanChanges(mergeLong(totalPlanChanges, other.totalPlanChanges))
                .totalPriceIncreases(mergeLong(totalPriceIncreases, other.totalPriceIncreases))
                .totalPriceDecreases(mergeLong(totalPriceDecreases, other.totalPriceDecreases))
                .totalEmployeeLimitIncreases(mergeLong(totalEmployeeLimitIncreases, other.totalEmployeeLimitIncreases))
                .totalEmployeeLimitDecreases(mergeLong(totalEmployeeLimitDecreases, other.totalEmployeeLimitDecreases))
                .totalFeatureAdditions(mergeLong(totalFeatureAdditions, other.totalFeatureAdditions))
                .totalFeatureRemovals(mergeLong(totalFeatureRemovals, other.totalFeatureRemovals))
                .averagePriceIncrease(mergeBigDecimal(averagePriceIncrease, other.averagePriceIncrease))
                .averagePriceDecrease(mergeBigDecimal(averagePriceDecrease, other.averagePriceDecrease))
                .averageEmployeeLimitIncrease(mergeInteger(averageEmployeeLimitIncrease, other.averageEmployeeLimitIncrease))
                .averageEmployeeLimitDecrease(mergeInteger(averageEmployeeLimitDecrease, other.averageEmployeeLimitDecrease))
                .build();
    }

    // =====================================================
    // PRIVATE HELPER METHODS FOR MERGE
    // =====================================================

    private Long mergeLong(Long a, Long b) {
        if (a == null && b == null) return 0L;
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    private BigDecimal mergeBigDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return BigDecimal.ZERO;
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }

    private Double mergeDouble(Double a, Double b) {
        if (a == null && b == null) return 0.0;
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    private Integer mergeInteger(Integer a, Integer b) {
        if (a == null && b == null) return 0;
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    private Map<String, Long> mergeMaps(Map<String, Long> a, Map<String, Long> b) {
        Map<String, Long> merged = new HashMap<>();
        if (a != null) merged.putAll(a);
        if (b != null) {
            for (Map.Entry<String, Long> entry : b.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        return merged;
    }
}