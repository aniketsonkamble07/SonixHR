// service/subscription/SubscriptionHistoryService.java
package com.sonixhr.service.subscription;

import com.sonixhr.dto.subscription.*;
import com.sonixhr.entity.tenant.SubscriptionHistory;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.tenant.SubscriptionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings({"null", "unused"})
public class SubscriptionHistoryService {

    private final SubscriptionHistoryRepository subscriptionHistoryRepository;

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    public Page<SubscriptionHistoryDTO> getSubscriptionHistory(Long tenantId, Pageable pageable) {
        Page<SubscriptionHistory> historyPage = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId, pageable);
        return historyPage.map(this::convertToDTO);
    }

    public List<SubscriptionHistoryDTO> getAllSubscriptionHistory(Long tenantId) {
        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);
        return historyList.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public SubscriptionHistoryDTO getSubscriptionHistoryById(Long id, Long tenantId) {
        SubscriptionHistory history = subscriptionHistoryRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Subscription history not found with id: %d for tenant: %d", id, tenantId)
                ));
        return convertToDTO(history);
    }

    // =====================================================
    // SEARCH AND FILTER
    // =====================================================

    public Page<SubscriptionHistoryDTO> searchSubscriptionHistory(
            Long tenantId,
            SubscriptionHistoryRequestDTO request,
            Pageable pageable) {

        Page<SubscriptionHistory> historyPage = subscriptionHistoryRepository.searchSubscriptionHistory(
                tenantId,
                request.getStartDate(),
                request.getEndDate(),
                request.getStatus(),
                null, // planType - pass null or handle differently
                request.getEventType(),
                request.getSearchTerm(),
                request.getIsAutoRenew(),
                request.getCancellationType(),
                request.getMinAmount(),
                request.getMaxAmount(),
                request.getEmployeeId(),
                pageable
        );

        return historyPage.map(this::convertToDTO);
    }

    public List<SubscriptionHistoryDTO> getSubscriptionHistoryByDateRange(
            Long tenantId,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdAndEventDateBetweenOrderByEventDateDesc(tenantId, startDate, endDate);

        return historyList.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SubscriptionHistoryDTO> getRecentSubscriptionHistory(Long tenantId, int limit) {
        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findTop10ByTenantIdOrderByEventDateDesc(tenantId);

        return historyList.stream()
                .limit(limit)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // =====================================================
    // FILTER BY EVENT TYPE
    // =====================================================

    public Page<SubscriptionHistoryDTO> getSubscriptionHistoryByEventType(
            Long tenantId,
            String eventType,
            Pageable pageable) {

        Page<SubscriptionHistory> historyPage = subscriptionHistoryRepository
                .findByTenantIdAndEventTypeOrderByEventDateDesc(tenantId, eventType, pageable);

        return historyPage.map(this::convertToDTO);
    }

    public Page<SubscriptionHistoryDTO> getSubscriptionHistoryByEventTypes(
            Long tenantId,
            List<String> eventTypes,
            Pageable pageable) {

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        List<SubscriptionHistory> filtered = historyList.stream()
                .filter(h -> eventTypes.contains(h.getEventType()))
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        if (start > filtered.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, filtered.size());
        }
        int end = Math.min((start + pageable.getPageSize()), filtered.size());

        List<SubscriptionHistoryDTO> dtos = filtered.subList(start, end).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, filtered.size());
    }

    public Page<SubscriptionHistoryDTO> getSubscriptionHistoryByPlanType(
            Long tenantId,
            String planType,
            Pageable pageable) {

        // Since the repository expects SubscriptionPlan, we need to handle this differently
        // We'll filter by planCode instead
        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        List<SubscriptionHistoryDTO> dtos = historyList.stream()
                .filter(h -> planType != null && planType.equalsIgnoreCase(h.getPlanType()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        if (start > dtos.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, dtos.size());
        }
        int end = Math.min((start + pageable.getPageSize()), dtos.size());

        return new PageImpl<>(dtos.subList(start, end), pageable, dtos.size());
    }

    public Page<SubscriptionHistoryDTO> getSubscriptionHistoryByPlanCode(
            Long tenantId,
            String planCode,
            Pageable pageable) {

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        List<SubscriptionHistoryDTO> dtos = historyList.stream()
                .filter(h -> planCode != null && planCode.equalsIgnoreCase(h.getPlanCode()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        if (start > dtos.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, dtos.size());
        }
        int end = Math.min((start + pageable.getPageSize()), dtos.size());

        return new PageImpl<>(dtos.subList(start, end), pageable, dtos.size());
    }

    public Page<SubscriptionHistoryDTO> getSubscriptionHistoryByStatus(
            Long tenantId,
            String status,
            Pageable pageable) {

        PlanStatus planStatus = PlanStatus.fromCode(status);
        Page<SubscriptionHistory> historyPage = subscriptionHistoryRepository
                .findByTenantIdAndPlanStatusOrderByEventDateDesc(
                        tenantId,
                        planStatus,
                        pageable
                );

        return historyPage.map(this::convertToDTO);
    }

    // =====================================================
    // PLAN OPERATION HISTORY
    // =====================================================

    public List<PlanOperationLogDTO> getPlanOperationHistory(Long planId) {
        // Since findByPlanIdOrderByEventDateDesc doesn't exist, we need to filter manually
        List<SubscriptionHistory> allHistory = subscriptionHistoryRepository.findAll();

        return allHistory.stream()
                .filter(h -> h.getPlanId() != null && h.getPlanId().equals(planId))
                .filter(h -> h.getEventType() != null && h.getEventType().startsWith("PLAN_"))
                .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                .map(this::convertToPlanLogDTO)
                .collect(Collectors.toList());
    }

    public List<PlanOperationLogDTO> getPlanOperationHistoryForTenant(Long tenantId) {
        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        return historyList.stream()
                .filter(h -> h.getEventType() != null && h.getEventType().startsWith("PLAN_"))
                .map(this::convertToPlanLogDTO)
                .collect(Collectors.toList());
    }

    public Page<PlanOperationLogDTO> searchPlanOperations(
            Long tenantId,
            String planCode,
            String eventType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        LocalDateTime queryStart = startDate != null ? startDate : LocalDateTime.now().minusYears(20);
        LocalDateTime queryEnd = endDate != null ? endDate : LocalDateTime.now().plusYears(20);

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdAndEventDateBetweenOrderByEventDateDesc(tenantId, queryStart, queryEnd);

        List<PlanOperationLogDTO> dtos = historyList.stream()
                .filter(h -> h.getEventType() != null && h.getEventType().startsWith("PLAN_"))
                .filter(h -> planCode == null || planCode.equalsIgnoreCase(h.getPlanCode()))
                .filter(h -> eventType == null || eventType.equals(h.getEventType()))
                .map(this::convertToPlanLogDTO)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        if (start > dtos.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, dtos.size());
        }
        int end = Math.min((start + pageable.getPageSize()), dtos.size());

        return new PageImpl<>(dtos.subList(start, end), pageable, dtos.size());
    }

    // =====================================================
    // SUMMARY AND STATISTICS
    // =====================================================

    public SubscriptionHistorySummaryDTO getSubscriptionHistorySummary(Long tenantId) {
        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        if (historyList.isEmpty()) {
            return SubscriptionHistorySummaryDTO.empty();
        }

        return buildSummary(historyList);
    }

    public SubscriptionHistorySummaryDTO getYearlySubscriptionHistorySummary(Long tenantId, int year) {
        LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59);

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdAndEventDateBetweenOrderByEventDateDesc(tenantId, startDate, endDate);

        if (historyList.isEmpty()) {
            return SubscriptionHistorySummaryDTO.empty();
        }

        return buildSummary(historyList);
    }

    public SubscriptionHistorySummaryDTO getMonthlySubscriptionHistorySummary(
            Long tenantId,
            int year,
            int month) {

        LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())
                .withHour(23).withMinute(59).withSecond(59);

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdAndEventDateBetweenOrderByEventDateDesc(tenantId, startDate, endDate);

        if (historyList.isEmpty()) {
            return SubscriptionHistorySummaryDTO.empty();
        }

        return buildSummary(historyList);
    }

    // =====================================================
    // ANALYTICS AND INSIGHTS
    // =====================================================

    public Map<String, Object> getSubscriptionTrends(Long tenantId, int months) {
        Map<String, Object> trends = new LinkedHashMap<>();
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusMonths(months);

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdAndEventDateBetweenOrderByEventDateDesc(tenantId, startDate, endDate);

        Map<String, Long> monthlyEvents = historyList.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getEventDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()
                ));

        Map<String, Map<String, Long>> monthlyEventTypes = new LinkedHashMap<>();
        for (SubscriptionHistory h : historyList) {
            String month = h.getEventDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            monthlyEventTypes.computeIfAbsent(month, k -> new HashMap<>())
                    .merge(h.getEventType(), 1L, Long::sum);
        }

        trends.put("months", monthlyEvents.keySet());
        trends.put("eventCounts", monthlyEvents);
        trends.put("eventTypesByMonth", monthlyEventTypes);
        trends.put("totalEvents", historyList.size());
        trends.put("period", months + " months");
        trends.put("analysisStartDate", startDate);
        trends.put("analysisEndDate", endDate);

        return trends;
    }

    public Map<String, Object> getChurnAnalysis(
            Long tenantId,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Map<String, Object> analysis = new LinkedHashMap<>();

        long cancellations = subscriptionHistoryRepository
                .countByEventType(tenantId, "CANCELLED");

        long activeAtStart = subscriptionHistoryRepository
                .countByStatus(tenantId, PlanStatus.ACTIVE);

        double churnRate = activeAtStart > 0 ?
                (double) cancellations / activeAtStart * 100 : 0.0;

        analysis.put("churnRate", churnRate);
        analysis.put("churnRateDisplay", String.format("%.2f%%", churnRate));
        analysis.put("totalCancellations", cancellations);
        analysis.put("activeAtStart", activeAtStart);
        analysis.put("periodStart", startDate);
        analysis.put("periodEnd", endDate);
        analysis.put("retentionRate", 100 - churnRate);

        return analysis;
    }

    public Map<String, Object> getRevenueAnalytics(Long tenantId, int year) {
        Map<String, Object> analytics = new LinkedHashMap<>();

        LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59);

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdAndEventDateBetweenOrderByEventDateDesc(tenantId, startDate, endDate);

        BigDecimal totalRevenue = historyList.stream()
                .map(SubscriptionHistory::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> revenueByMonth = new LinkedHashMap<>();
        for (SubscriptionHistory h : historyList) {
            String month = h.getEventDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            revenueByMonth.merge(month,
                    h.getAmount() != null ? h.getAmount() : BigDecimal.ZERO,
                    BigDecimal::add);
        }

        analytics.put("year", year);
        analytics.put("totalRevenue", totalRevenue);
        analytics.put("revenueByMonth", revenueByMonth);
        analytics.put("totalEvents", historyList.size());
        analytics.put("currency", historyList.isEmpty() ? "USD" :
                historyList.get(0).getCurrency());

        return analytics;
    }

    public Map<String, Object> getPlanPerformanceMetrics(Long tenantId) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        Map<String, Long> planUsage = historyList.stream()
                .filter(h -> h.getPlanCode() != null)
                .collect(Collectors.groupingBy(
                        SubscriptionHistory::getPlanCode,
                        Collectors.counting()
                ));

        Map<String, BigDecimal> avgRevenueByPlan = new HashMap<>();
        for (Map.Entry<String, Long> entry : planUsage.entrySet()) {
            String planCode = entry.getKey();
            BigDecimal total = historyList.stream()
                    .filter(h -> planCode.equals(h.getPlanCode()))
                    .map(SubscriptionHistory::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgRevenueByPlan.put(planCode,
                    total.divide(BigDecimal.valueOf(entry.getValue()), 2, RoundingMode.HALF_UP));
        }

        metrics.put("planUsage", planUsage);
        metrics.put("averageRevenueByPlan", avgRevenueByPlan);
        metrics.put("totalPlansUsed", planUsage.size());
        metrics.put("mostUsedPlan", planUsage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN"));

        return metrics;
    }

    public Map<String, Object> getCustomerLifetimeValue(Long tenantId) {
        Map<String, Object> ltv = new LinkedHashMap<>();

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        double avgDuration = historyList.stream()
                .filter(h -> h.getStartDate() != null && h.getEndDate() != null)
                .mapToLong(h -> java.time.Duration.between(h.getStartDate(), h.getEndDate()).toDays())
                .average()
                .orElse(0.0);

        BigDecimal avgRevenue = historyList.stream()
                .map(SubscriptionHistory::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historyList.size() > 0 ? historyList.size() : 1),
                        2, RoundingMode.HALF_UP);

        BigDecimal estimatedLTV = avgRevenue.multiply(
                BigDecimal.valueOf(avgDuration / 30));

        ltv.put("averageSubscriptionDurationDays", avgDuration);
        ltv.put("averageMonthlyRevenue", avgRevenue);
        ltv.put("estimatedLifetimeValue", estimatedLTV);
        ltv.put("totalSubscriptions", historyList.size());
        ltv.put("totalRevenue", historyList.stream()
                .map(SubscriptionHistory::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return ltv;
    }

    // =====================================================
    // EXPORT FUNCTIONS
    // =====================================================

    public byte[] exportSubscriptionHistory(Long tenantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdAndEventDateBetweenOrderByEventDateDesc(tenantId, startDate, endDate);
        return generateCSV(historyList);
    }

    public byte[] exportSubscriptionHistoryToExcel(Long tenantId, LocalDateTime startDate, LocalDateTime endDate) {
        return exportSubscriptionHistory(tenantId, startDate, endDate);
    }

    // =====================================================
    // DASHBOARD AND REPORTS
    // =====================================================

    public Map<String, Object> getDashboardData(Long tenantId) {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        SubscriptionHistorySummaryDTO summary = getSubscriptionHistorySummary(tenantId);
        Map<String, Object> trends = getSubscriptionTrends(tenantId, 6);
        Map<String, Object> planMetrics = getPlanPerformanceMetrics(tenantId);

        dashboard.put("summary", summary);
        dashboard.put("trends", trends);
        dashboard.put("planMetrics", planMetrics);
        dashboard.put("timestamp", LocalDateTime.now());

        return dashboard;
    }

    public Map<String, Long> getEventDistribution(Long tenantId, String timePeriod) {
        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        DateTimeFormatter formatter = switch (timePeriod.toLowerCase()) {
            case "day" -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
            case "week" -> DateTimeFormatter.ofPattern("yyyy-'W'ww");
            case "month" -> DateTimeFormatter.ofPattern("yyyy-MM");
            default -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
        };

        return historyList.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getEventDate().format(formatter),
                        Collectors.counting()
                ));
    }

    public Map<String, Object> getPlanUsageStatistics(Long tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        List<SubscriptionHistory> historyList = subscriptionHistoryRepository
                .findByTenantIdOrderByEventDateDesc(tenantId);

        long activePlans = historyList.stream()
                .filter(h -> "PLAN_CREATED".equals(h.getEventType()))
                .filter(h -> historyList.stream()
                        .noneMatch(d -> "PLAN_DELETED".equals(d.getEventType()) &&
                                Objects.equals(d.getPlanId(), h.getPlanId()) &&
                                d.getEventDate().isAfter(h.getEventDate())))
                .count();

        long deletedPlans = historyList.stream()
                .filter(h -> "PLAN_DELETED".equals(h.getEventType()))
                .count();

        long planUpdates = historyList.stream()
                .filter(h -> "PLAN_UPDATED".equals(h.getEventType()))
                .count();

        stats.put("totalPlansCreated",
                historyList.stream().filter(h -> "PLAN_CREATED".equals(h.getEventType())).count());
        stats.put("activePlans", activePlans);
        stats.put("deletedPlans", deletedPlans);
        stats.put("planUpdates", planUpdates);
        stats.put("uniquePlansUsed", historyList.stream()
                .map(SubscriptionHistory::getPlanCode)
                .filter(Objects::nonNull)
                .distinct()
                .count());

        return stats;
    }

    // =====================================================
    // CONVERSION METHODS
    // =====================================================

    private SubscriptionHistoryDTO convertToDTO(SubscriptionHistory entity) {
        if (entity == null) {
            return null;
        }

        SubscriptionHistoryDTO dto = new SubscriptionHistoryDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setSubscriptionId(entity.getSubscriptionId());
        dto.setPlanId(entity.getPlanId());
        dto.setPlanCode(entity.getPlanCode());
        dto.setPlanName(entity.getPlanName());
        dto.setPlanType(entity.getPlanType());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeName(entity.getEmployeeName());
        dto.setEmployeeEmail(entity.getEmployeeEmail());
        dto.setPlanStatus(entity.getPlanStatus());
        dto.setEventType(entity.getEventType());
        dto.setEventDate(entity.getEventDate());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setAmount(entity.getAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setTransactionId(entity.getTransactionId());
        dto.setInvoiceNumber(entity.getInvoiceNumber());
        dto.setReason(entity.getReason());
        dto.setNotes(entity.getNotes());
        dto.setPreviousPlanId(entity.getPreviousPlanId());
        dto.setPreviousPlanCode(entity.getPreviousPlanCode());
        dto.setPreviousPlanName(entity.getPreviousPlanName());
        dto.setPreviousPrice(entity.getPreviousPrice());
        dto.setNewPrice(entity.getNewPrice());
        dto.setPreviousMaxEmployees(entity.getPreviousMaxEmployees());
        dto.setNewMaxEmployees(entity.getNewMaxEmployees());
        dto.setPreviousValidityMonths(entity.getPreviousValidityMonths());
        dto.setNewValidityMonths(entity.getNewValidityMonths());
        dto.setIsAutoRenew(entity.getIsAutoRenew());
        dto.setGracePeriodDays(entity.getGracePeriodDays());
        dto.setDaysRemaining(entity.getDaysRemaining());
        dto.setCancellationType(entity.getCancellationType());
        dto.setTriggerSource(entity.getTriggerSource());
        dto.setTriggeredById(entity.getTriggeredById());
        dto.setMetadata(entity.getMetadata());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setFieldChanged(entity.getFieldChanged());
        dto.setOldValue(entity.getOldValue());
        dto.setNewValue(entity.getNewValue());
        return dto;
    }

    private PlanOperationLogDTO convertToPlanLogDTO(SubscriptionHistory history) {
        PlanOperationLogDTO dto = new PlanOperationLogDTO();
        dto.setId(history.getId());
        dto.setTenantId(history.getTenantId());
        dto.setPlanId(history.getPlanId());
        dto.setPlanCode(history.getPlanCode());
        dto.setPlanName(history.getPlanName());
        dto.setEventType(history.getEventType());
        dto.setEventDate(history.getEventDate());
        dto.setAmount(history.getAmount());
        dto.setCurrency(history.getCurrency());
        dto.setPreviousPlanId(history.getPreviousPlanId());
        dto.setPreviousPlanCode(history.getPreviousPlanCode());
        dto.setPreviousPlanName(history.getPreviousPlanName());
        dto.setPreviousPrice(history.getPreviousPrice());
        dto.setNewPrice(history.getNewPrice());
        dto.setPreviousMaxEmployees(history.getPreviousMaxEmployees());
        dto.setNewMaxEmployees(history.getNewMaxEmployees());
        dto.setPreviousValidityMonths(history.getPreviousValidityMonths());
        dto.setNewValidityMonths(history.getNewValidityMonths());
        dto.setFieldChanged(history.getFieldChanged());
        dto.setOldValue(history.getOldValue());
        dto.setNewValue(history.getNewValue());
        dto.setTriggerSource(history.getTriggerSource() != null ?
                history.getTriggerSource().name() : null);
        dto.setTriggeredById(history.getTriggeredById());
        dto.setCreatedBy(history.getCreatedBy());
        dto.setCreatedAt(history.getCreatedAt());
        return dto;
    }

    // =====================================================
    // SUMMARY BUILDING METHODS
    // =====================================================

    private SubscriptionHistorySummaryDTO buildSummary(List<SubscriptionHistory> historyList) {
        Long tenantId = historyList.get(0).getTenantId();

        // Group by month
        Map<String, Long> eventsByMonth = historyList.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getEventDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()
                ));

        // Group by year
        Map<String, Long> eventsByYear = historyList.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getEventDate().format(DateTimeFormatter.ofPattern("yyyy")),
                        Collectors.counting()
                ));

        // Group by event type
        Map<String, Long> eventsByType = historyList.stream()
                .collect(Collectors.groupingBy(
                        SubscriptionHistory::getEventType,
                        Collectors.counting()
                ));

        // Group by plan code
        Map<String, Long> eventsByPlan = historyList.stream()
                .filter(h -> h.getPlanCode() != null)
                .collect(Collectors.groupingBy(
                        SubscriptionHistory::getPlanCode,
                        Collectors.counting()
                ));

        // Group by status
        Map<String, Long> eventsByStatus = historyList.stream()
                .filter(h -> h.getPlanStatus() != null)
                .collect(Collectors.groupingBy(
                        h -> h.getPlanStatus().getCode(),
                        Collectors.counting()
                ));

        // Calculate financial metrics
        BigDecimal totalRevenue = historyList.stream()
                .map(SubscriptionHistory::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Event type counts
        long activations = eventsByType.getOrDefault("ACTIVATED", 0L);
        long renewals = eventsByType.getOrDefault("RENEWED", 0L);
        long upgrades = eventsByType.getOrDefault("UPGRADED", 0L);
        long downgrades = eventsByType.getOrDefault("DOWNGRADED", 0L);
        long cancellations = eventsByType.getOrDefault("CANCELLED", 0L);
        long suspensions = eventsByType.getOrDefault("SUSPENDED", 0L);
        long reactivations = eventsByType.getOrDefault("REACTIVATED", 0L);
        long expirations = eventsByType.getOrDefault("EXPIRED", 0L);

        // Plan operation counts
        long planCreated = eventsByType.getOrDefault("PLAN_CREATED", 0L);
        long planUpdated = eventsByType.getOrDefault("PLAN_UPDATED", 0L);
        long planDeleted = eventsByType.getOrDefault("PLAN_DELETED", 0L);
        long planRestored = eventsByType.getOrDefault("PLAN_RESTORED", 0L);
        long planToggled = eventsByType.getOrDefault("PLAN_TOGGLED", 0L);

        long totalPlanOps = planCreated + planUpdated + planDeleted + planRestored + planToggled;
        long totalSubOps = activations + renewals + upgrades + downgrades + cancellations +
                suspensions + reactivations + expirations;

        // Calculate average duration
        double avgDuration = historyList.stream()
                .filter(h -> h.getStartDate() != null && h.getEndDate() != null)
                .mapToLong(h -> java.time.Duration.between(h.getStartDate(), h.getEndDate()).toDays())
                .average()
                .orElse(0.0);

        // Current status counts
        long activeCount = subscriptionHistoryRepository.countByStatus(tenantId, PlanStatus.ACTIVE);
        long suspendedCount = subscriptionHistoryRepository.countByStatus(tenantId, PlanStatus.SUSPENDED);
        long expiredCount = subscriptionHistoryRepository.countByStatus(tenantId, PlanStatus.EXPIRED);
        long cancelledCount = subscriptionHistoryRepository.countByStatus(tenantId, PlanStatus.CANCELLED);

        // Get unique plans
        long uniquePlans = historyList.stream()
                .map(SubscriptionHistory::getPlanCode)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // Calculate MRR
        BigDecimal mrr = calculateMRR(historyList);
        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12));

        // Date ranges
        LocalDateTime firstDate = historyList.stream()
                .map(SubscriptionHistory::getEventDate)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime lastDate = historyList.stream()
                .map(SubscriptionHistory::getEventDate)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // Calculate average monthly spend
        BigDecimal avgMonthlySpend = BigDecimal.ZERO;
        if (!eventsByMonth.isEmpty()) {
            BigDecimal total = eventsByMonth.values().stream()
                    .map(BigDecimal::valueOf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgMonthlySpend = total.divide(BigDecimal.valueOf(eventsByMonth.size()), 2, RoundingMode.HALF_UP);
        }

        // Find most common items
        String mostCommonEvent = eventsByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");

        String mostCommonPlan = eventsByPlan.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");

        String mostCommonStatus = eventsByStatus.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");

        // Calculate churn rate
        long totalChurned = cancellations + expirations;
        double churnRate = historyList.size() > 0 ?
                (double) totalChurned / historyList.size() * 100 : 0.0;

        // Plan change analysis
        long totalPlanChanges = planUpdated;
        long priceIncreases = historyList.stream()
                .filter(h -> h.getPreviousPrice() != null && h.getNewPrice() != null)
                .filter(h -> h.getNewPrice().compareTo(h.getPreviousPrice()) > 0)
                .count();
        long priceDecreases = historyList.stream()
                .filter(h -> h.getPreviousPrice() != null && h.getNewPrice() != null)
                .filter(h -> h.getNewPrice().compareTo(h.getPreviousPrice()) < 0)
                .count();

        return SubscriptionHistorySummaryDTO.builder()
                .totalSubscriptionEvents((long) historyList.size())
                .totalPlanOperations(totalPlanOps)
                .totalSubscriptionOperations(totalSubOps)
                .totalActivations(activations)
                .totalRenewals(renewals)
                .totalUpgrades(upgrades)
                .totalDowngrades(downgrades)
                .totalCancellations(cancellations)
                .totalSuspensions(suspensions)
                .totalReactivations(reactivations)
                .totalExpirations(expirations)
                .totalPlanCreated(planCreated)
                .totalPlanUpdated(planUpdated)
                .totalPlanDeleted(planDeleted)
                .totalPlanRestored(planRestored)
                .totalPlanToggled(planToggled)
                .totalAmountSpent(totalRevenue)
                .totalRevenue(totalRevenue)
                .currency(historyList.get(0).getCurrency() != null ?
                        historyList.get(0).getCurrency() : "USD")
                .monthlyRecurringRevenue(mrr)
                .annualRecurringRevenue(arr)
                .eventsByMonth(eventsByMonth)
                .eventsByYear(eventsByYear)
                .eventsByType(eventsByType)
                .eventsByPlan(eventsByPlan)
                .eventsByPlanCode(eventsByPlan)
                .eventsByStatus(eventsByStatus)
                .currentActiveSubscriptionCount(activeCount)
                .currentSuspendedCount(suspendedCount)
                .currentExpiredCount(expiredCount)
                .currentCancelledCount(cancelledCount)
                .firstSubscriptionDate(firstDate)
                .lastSubscriptionDate(lastDate)
                .averageSubscriptionDurationDays(avgDuration)
                .totalUniquePlansUsed((int) uniquePlans)
                .totalMonthsActive(eventsByMonth.size())
                .totalYearsActive(eventsByYear.size())
                .averageMonthlySpend(avgMonthlySpend)
                .churnRate(churnRate)
                .retentionRate(100 - churnRate)
                .mostCommonEventType(mostCommonEvent)
                .mostCommonPlanType(mostCommonPlan)
                .mostCommonPlanCode(mostCommonPlan)
                .mostCommonStatus(mostCommonStatus)
                .totalPlanChanges(totalPlanChanges)
                .totalPriceIncreases(priceIncreases)
                .totalPriceDecreases(priceDecreases)
                .build();
    }

    private BigDecimal calculateMRR(List<SubscriptionHistory> historyList) {
        return historyList.stream()
                .filter(h -> h.getPlanStatus() == PlanStatus.ACTIVE)
                .findFirst()
                .map(h -> h.getAmount() != null ? h.getAmount() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    // =====================================================
    // EXPORT METHODS
    // =====================================================

    private byte[] generateCSV(List<SubscriptionHistory> historyList) {
        StringBuilder csv = new StringBuilder();

        csv.append("ID,Subscription ID,Event Type,Status,Plan Type,Event Date,Start Date,End Date,")
                .append("Amount,Currency,Payment Method,Transaction ID,Invoice Number,Reason,")
                .append("Employee ID,Employee Name,Employee Email,")
                .append("Previous Plan,New Plan,Is Auto Renew,Grace Period Days,Days Remaining,")
                .append("Cancellation Type,Trigger Source,Triggered By ID,Created At,Created By\n");

        for (SubscriptionHistory h : historyList) {
            csv.append(h.getId()).append(",")
                    .append(escapeCSV(h.getSubscriptionId())).append(",")
                    .append(h.getEventType()).append(",")
                    .append(h.getPlanStatus() != null ? h.getPlanStatus().getCode() : "").append(",")
                    .append(h.getPlanType() != null ? h.getPlanType() : "").append(",")
                    .append(h.getEventDate()).append(",")
                    .append(h.getStartDate() != null ? h.getStartDate() : "").append(",")
                    .append(h.getEndDate() != null ? h.getEndDate() : "").append(",")
                    .append(h.getAmount() != null ? h.getAmount() : "").append(",")
                    .append(escapeCSV(h.getCurrency())).append(",")
                    .append(escapeCSV(h.getPaymentMethod())).append(",")
                    .append(escapeCSV(h.getTransactionId())).append(",")
                    .append(escapeCSV(h.getInvoiceNumber())).append(",")
                    .append(escapeCSV(h.getReason())).append(",")
                    .append(h.getEmployeeId() != null ? h.getEmployeeId() : "").append(",")
                    .append(escapeCSV(h.getEmployeeName())).append(",")
                    .append(escapeCSV(h.getEmployeeEmail())).append(",")
                    .append(h.getPreviousPlanCode() != null ? h.getPreviousPlanCode() : "").append(",")
                    .append(h.getPlanCode() != null ? h.getPlanCode() : "").append(",")
                    .append(h.getIsAutoRenew() != null ? h.getIsAutoRenew() : "").append(",")
                    .append(h.getGracePeriodDays() != null ? h.getGracePeriodDays() : "").append(",")
                    .append(h.getDaysRemaining() != null ? h.getDaysRemaining() : "").append(",")
                    .append(escapeCSV(h.getCancellationType())).append(",")
                    .append(h.getTriggerSource() != null ? h.getTriggerSource().name() : "").append(",")
                    .append(h.getTriggeredById() != null ? h.getTriggeredById() : "").append(",")
                    .append(h.getCreatedAt() != null ? h.getCreatedAt() : "").append(",")
                    .append(escapeCSV(h.getCreatedBy())).append("\n");
        }

        return csv.toString().getBytes();
    }

    private byte[] generatePlanCSV(List<SubscriptionHistory> historyList) {
        StringBuilder csv = new StringBuilder();

        csv.append("ID,Plan ID,Plan Code,Plan Name,Event Type,Event Date,")
                .append("Previous Price,New Price,Previous Max Employees,New Max Employees,")
                .append("Field Changed,Old Value,New Value,Trigger Source,Created By\n");

        for (SubscriptionHistory h : historyList) {
            csv.append(h.getId()).append(",")
                    .append(h.getPlanId()).append(",")
                    .append(escapeCSV(h.getPlanCode())).append(",")
                    .append(escapeCSV(h.getPlanName())).append(",")
                    .append(h.getEventType()).append(",")
                    .append(h.getEventDate()).append(",")
                    .append(h.getPreviousPrice() != null ? h.getPreviousPrice() : "").append(",")
                    .append(h.getNewPrice() != null ? h.getNewPrice() : "").append(",")
                    .append(h.getPreviousMaxEmployees() != null ? h.getPreviousMaxEmployees() : "").append(",")
                    .append(h.getNewMaxEmployees() != null ? h.getNewMaxEmployees() : "").append(",")
                    .append(escapeCSV(h.getFieldChanged())).append(",")
                    .append(escapeCSV(h.getOldValue())).append(",")
                    .append(escapeCSV(h.getNewValue())).append(",")
                    .append(h.getTriggerSource() != null ? h.getTriggerSource().name() : "").append(",")
                    .append(escapeCSV(h.getCreatedBy())).append("\n");
        }

        return csv.toString().getBytes();
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}