// controller/tenant/EnhancedSubscriptionHistoryController.java
package com.sonixhr.controller.tenant;

import com.sonixhr.dto.subscription.*;
import com.sonixhr.security.SecurityUtils;
import com.sonixhr.service.subscription.SubscriptionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tenant/subscription/history")
@RequiredArgsConstructor
@Tag(name = "Subscription History", description = "APIs for managing subscription history")
@SecurityRequirement(name = "bearerAuth")
public class EnhancedSubscriptionHistoryController {

    private final SubscriptionHistoryService subscriptionHistoryService;
    private final SecurityUtils securityUtils;

    // =====================================================
    // GET OPERATIONS
    // =====================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get paginated subscription history",
            description = "Retrieves paginated subscription history for the current tenant")
    public ResponseEntity<Page<SubscriptionHistoryDTO>> getSubscriptionHistory(
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get paginated subscription history for tenant: {}", tenantId);

        Page<SubscriptionHistoryDTO> history = subscriptionHistoryService.getSubscriptionHistory(tenantId, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get all subscription history",
            description = "Retrieves all subscription history for the current tenant")
    public ResponseEntity<List<SubscriptionHistoryDTO>> getAllSubscriptionHistory() {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get all subscription history for tenant: {}", tenantId);

        List<SubscriptionHistoryDTO> history = subscriptionHistoryService.getAllSubscriptionHistory(tenantId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history by ID",
            description = "Retrieves a specific subscription history entry by ID")
    public ResponseEntity<SubscriptionHistoryDTO> getSubscriptionHistoryById(
            @Parameter(description = "Subscription history ID", required = true)
            @PathVariable Long id) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history by id: {} for tenant: {}", id, tenantId);

        SubscriptionHistoryDTO history = subscriptionHistoryService.getSubscriptionHistoryById(id, tenantId);
        return ResponseEntity.ok(history);
    }

    // =====================================================
    // FILTERED SEARCH OPERATIONS
    // =====================================================

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search subscription history with advanced filters",
            description = "Search subscription history with multiple filter criteria")
    public ResponseEntity<Page<SubscriptionHistoryDTO>> searchSubscriptionHistory(
            @Parameter(description = "Start date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,

            @Parameter(description = "End date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,

            @Parameter(description = "Subscription status (ACTIVE, EXPIRED, CANCELLED, PAUSED)")
            @RequestParam(required = false) String status,

            @Parameter(description = "Plan type (BASIC, PREMIUM, ENTERPRISE, FREE, CUSTOM)")
            @RequestParam(required = false) String planType,

            @Parameter(description = "Event type (CREATED, RENEWED, UPGRADED, DOWNGRADED, CANCELLED, EXPIRED)")
            @RequestParam(required = false) String eventType,

            @Parameter(description = "Search term to match in notes or reason")
            @RequestParam(required = false) String searchTerm,

            @Parameter(description = "Auto-renew flag")
            @RequestParam(required = false) Boolean autoRenew,

            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to search subscription history for tenant: {} with filters", tenantId);

        SubscriptionHistoryRequestDTO request = SubscriptionHistoryRequestDTO.builder()
                .startDate(startDate != null ? startDate.atStartOfDay() : null)
                .endDate(endDate != null ? endDate.atTime(23, 59, 59) : null)
                .status(status != null ? com.sonixhr.enums.PlanStatus.fromCode(status) : null)
                .planType(planType)
                .eventType(eventType)
                .searchTerm(searchTerm)
                .isAutoRenew(autoRenew)
                .build();

        Page<SubscriptionHistoryDTO> history = subscriptionHistoryService.searchSubscriptionHistory(
                tenantId, request, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/date-range")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history for date range",
            description = "Retrieves subscription history within a specific date range")
    public ResponseEntity<List<SubscriptionHistoryDTO>> getSubscriptionHistoryByDateRange(
            @Parameter(description = "Start date (format: yyyy-MM-dd)", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,

            @Parameter(description = "End date (format: yyyy-MM-dd)", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        Long tenantId = securityUtils.getCurrentTenantId();

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }

        log.info("REST request to get subscription history for date range: {} to {} for tenant: {}",
                startDate, endDate, tenantId);

        List<SubscriptionHistoryDTO> history = subscriptionHistoryService.getSubscriptionHistoryByDateRange(
                tenantId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        return ResponseEntity.ok(history);
    }

    // =====================================================
    // FILTER BY EVENT TYPE
    // =====================================================

    @GetMapping("/events/{eventType}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history by event type")
    public ResponseEntity<Page<SubscriptionHistoryDTO>> getSubscriptionHistoryByEventType(
            @Parameter(description = "Event type", required = true)
            @PathVariable String eventType,
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history by event type: {} for tenant: {}", eventType, tenantId);

        Page<SubscriptionHistoryDTO> history = subscriptionHistoryService.getSubscriptionHistoryByEventType(
                tenantId, eventType, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/events")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history by multiple event types")
    public ResponseEntity<Page<SubscriptionHistoryDTO>> getSubscriptionHistoryByEventTypes(
            @Parameter(description = "Event types (comma-separated)")
            @RequestParam List<String> eventTypes,
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history by event types: {} for tenant: {}", eventTypes, tenantId);

        Page<SubscriptionHistoryDTO> history = subscriptionHistoryService.getSubscriptionHistoryByEventTypes(
                tenantId, eventTypes, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/plans/{planType}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history by plan type")
    public ResponseEntity<Page<SubscriptionHistoryDTO>> getSubscriptionHistoryByPlanType(
            @Parameter(description = "Plan type", required = true)
            @PathVariable String planType,
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history by plan type: {} for tenant: {}", planType, tenantId);

        Page<SubscriptionHistoryDTO> history = subscriptionHistoryService.getSubscriptionHistoryByPlanType(
                tenantId, planType, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/plan-code/{planCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history by plan code")
    public ResponseEntity<Page<SubscriptionHistoryDTO>> getSubscriptionHistoryByPlanCode(
            @Parameter(description = "Plan code", required = true)
            @PathVariable String planCode,
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history by plan code: {} for tenant: {}", planCode, tenantId);

        Page<SubscriptionHistoryDTO> history = subscriptionHistoryService.getSubscriptionHistoryByPlanCode(
                tenantId, planCode, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history by status")
    public ResponseEntity<Page<SubscriptionHistoryDTO>> getSubscriptionHistoryByStatus(
            @Parameter(description = "Status", required = true)
            @PathVariable String status,
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history by status: {} for tenant: {}", status, tenantId);

        Page<SubscriptionHistoryDTO> history = subscriptionHistoryService.getSubscriptionHistoryByStatus(
                tenantId, status, pageable);
        return ResponseEntity.ok(history);
    }

    // =====================================================
    // RECENT OPERATIONS
    // =====================================================

    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get recent subscription history")
    public ResponseEntity<List<SubscriptionHistoryDTO>> getRecentSubscriptionHistory(
            @Parameter(description = "Number of entries to retrieve (default: 10, max: 50)")
            @RequestParam(defaultValue = "10") int limit) {

        if (limit > 50) {
            limit = 50;
        }

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get recent {} subscription history entries for tenant: {}", limit, tenantId);

        List<SubscriptionHistoryDTO> history = subscriptionHistoryService.getRecentSubscriptionHistory(tenantId, limit);
        return ResponseEntity.ok(history);
    }

    // =====================================================
    // SUMMARY OPERATIONS
    // =====================================================

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history summary")
    public ResponseEntity<SubscriptionHistorySummaryDTO> getSubscriptionHistorySummary() {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history summary for tenant: {}", tenantId);

        SubscriptionHistorySummaryDTO summary = subscriptionHistoryService.getSubscriptionHistorySummary(tenantId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/summary/year")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get yearly subscription history summary")
    public ResponseEntity<SubscriptionHistorySummaryDTO> getYearlySubscriptionHistorySummary(
            @Parameter(description = "Year (format: yyyy)", example = "2026")
            @RequestParam(required = false) Integer year) {

        Long tenantId = securityUtils.getCurrentTenantId();

        if (year == null) {
            year = LocalDate.now().getYear();
        }

        log.info("REST request to get subscription history summary for year: {} for tenant: {}", year, tenantId);

        SubscriptionHistorySummaryDTO summary = subscriptionHistoryService.getYearlySubscriptionHistorySummary(
                tenantId, year);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/summary/monthly")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get monthly subscription history summary")
    public ResponseEntity<SubscriptionHistorySummaryDTO> getMonthlySubscriptionHistorySummary(
            @Parameter(description = "Year (format: yyyy)", example = "2026", required = true)
            @RequestParam int year,
            @Parameter(description = "Month (format: MM)", example = "1", required = true)
            @RequestParam int month) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history summary for {}-{} for tenant: {}", year, month, tenantId);

        SubscriptionHistorySummaryDTO summary = subscriptionHistoryService.getMonthlySubscriptionHistorySummary(
                tenantId, year, month);
        return ResponseEntity.ok(summary);
    }

    // =====================================================
    // EXPORT OPERATIONS
    // =====================================================

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Export subscription history")
    public ResponseEntity<byte[]> exportSubscriptionHistory(
            @Parameter(description = "Start date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,

            @Parameter(description = "End date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,

            @Parameter(description = "Export format (CSV, EXCEL)")
            @RequestParam(defaultValue = "CSV") String format) {

        Long tenantId = securityUtils.getCurrentTenantId();

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDateTime.now().minusMonths(6);
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

        log.info("REST request to export subscription history for tenant: {} from {} to {}",
                tenantId, start, end);

        byte[] exportData;
        if (format.equalsIgnoreCase("EXCEL")) {
            exportData = subscriptionHistoryService.exportSubscriptionHistoryToExcel(tenantId, start, end);
        } else {
            exportData = subscriptionHistoryService.exportSubscriptionHistory(tenantId, start, end);
        }

        String filename = String.format("subscription_history_%s_%s.%s",
                tenantId,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                format.toLowerCase());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(format.equalsIgnoreCase("EXCEL") ?
                MediaType.APPLICATION_OCTET_STREAM :
                MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(exportData);
    }

    // =====================================================
    // STATISTICS OPERATIONS
    // =====================================================

    @GetMapping("/statistics/trend")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get subscription trend statistics")
    public ResponseEntity<Map<String, Object>> getSubscriptionTrend(
            @Parameter(description = "Number of months to analyze (default: 12)")
            @RequestParam(defaultValue = "12") int months) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription trend for tenant: {} for {} months", tenantId, months);

        Map<String, Object> trend = subscriptionHistoryService.getSubscriptionTrends(tenantId, months);
        return ResponseEntity.ok(trend);
    }

    @GetMapping("/statistics/revenue")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get revenue statistics")
    public ResponseEntity<Map<String, Object>> getRevenueStatistics(
            @Parameter(description = "Year for revenue statistics")
            @RequestParam(required = false) Integer year) {

        Long tenantId = securityUtils.getCurrentTenantId();

        if (year == null) {
            year = LocalDate.now().getYear();
        }

        log.info("REST request to get revenue statistics for tenant: {} for year: {}", tenantId, year);

        Map<String, Object> statistics = subscriptionHistoryService.getRevenueAnalytics(tenantId, year);
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/statistics/churn")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get churn analysis")
    public ResponseEntity<Map<String, Object>> getChurnAnalysis(
            @Parameter(description = "Start date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,

            @Parameter(description = "End date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        Long tenantId = securityUtils.getCurrentTenantId();

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDateTime.now().minusMonths(6);
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

        log.info("REST request to get churn analysis for tenant: {} from {} to {}", tenantId, start, end);

        Map<String, Object> analysis = subscriptionHistoryService.getChurnAnalysis(tenantId, start, end);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/statistics/ltv")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get customer lifetime value")
    public ResponseEntity<Map<String, Object>> getCustomerLifetimeValue() {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get customer lifetime value for tenant: {}", tenantId);

        Map<String, Object> ltv = subscriptionHistoryService.getCustomerLifetimeValue(tenantId);
        return ResponseEntity.ok(ltv);
    }

    // =====================================================
    // DASHBOARD OPERATIONS
    // =====================================================

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get dashboard data")
    public ResponseEntity<Map<String, Object>> getDashboardData() {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get dashboard data for tenant: {}", tenantId);

        Map<String, Object> dashboard = subscriptionHistoryService.getDashboardData(tenantId);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/distribution/{timePeriod}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get event distribution")
    public ResponseEntity<Map<String, Long>> getEventDistribution(
            @Parameter(description = "Time period (day, week, month)", required = true)
            @PathVariable String timePeriod) {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get event distribution for tenant: {} by {}", tenantId, timePeriod);

        Map<String, Long> distribution = subscriptionHistoryService.getEventDistribution(tenantId, timePeriod);
        return ResponseEntity.ok(distribution);
    }

    @GetMapping("/plan-usage")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get plan usage statistics")
    public ResponseEntity<Map<String, Object>> getPlanUsageStatistics() {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get plan usage statistics for tenant: {}", tenantId);

        Map<String, Object> stats = subscriptionHistoryService.getPlanUsageStatistics(tenantId);
        return ResponseEntity.ok(stats);
    }

    // =====================================================
    // PLAN OPERATION HISTORY
    // =====================================================

    @GetMapping("/plan-operations/{planId}")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get plan operation history")
    public ResponseEntity<List<PlanOperationLogDTO>> getPlanOperationHistory(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long planId) {

        log.info("REST request to get plan operation history for plan: {}", planId);

        List<PlanOperationLogDTO> history = subscriptionHistoryService.getPlanOperationHistory(planId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/plan-operations")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get all plan operation history for tenant")
    public ResponseEntity<List<PlanOperationLogDTO>> getPlanOperationHistoryForTenant() {

        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get plan operation history for tenant: {}", tenantId);

        List<PlanOperationLogDTO> history = subscriptionHistoryService.getPlanOperationHistoryForTenant(tenantId);
        return ResponseEntity.ok(history);
    }
}