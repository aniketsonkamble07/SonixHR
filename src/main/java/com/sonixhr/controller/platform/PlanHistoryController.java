package com.sonixhr.controller.platform;



import com.sonixhr.dto.subscription.PlanOperationLogDTO;
import com.sonixhr.service.subscription.SubscriptionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/plans/history")
@RequiredArgsConstructor
@Tag(name = "Plan History (Admin)", description = "APIs for managing plan operation history")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTIONS') or hasAuthority('MANAGE_PRICING_PLANS') or hasAuthority('MANAGE_SUBSCRIPTION')")
public class PlanHistoryController {

    private final SubscriptionHistoryService subscriptionHistoryService;

    @GetMapping("/{planId}")
    @Operation(summary = "Get plan operation history by plan ID")
    public ResponseEntity<List<PlanOperationLogDTO>> getPlanHistory(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long planId) {
        log.info("REST request to get history for plan: {}", planId);
        return ResponseEntity.ok(subscriptionHistoryService.getPlanOperationHistory(planId));
    }

    @GetMapping
    @Operation(summary = "Get all plan operation history")
    public ResponseEntity<List<PlanOperationLogDTO>> getAllPlanHistory() {
        log.info("REST request to get all plan operation history");
        return ResponseEntity.ok(subscriptionHistoryService.getPlanOperationHistoryForTenant(1L));
    }

    @GetMapping("/search")
    @Operation(summary = "Search plan operations")
    public ResponseEntity<Page<PlanOperationLogDTO>> searchPlanOperations(
            @Parameter(description = "Plan code")
            @RequestParam(required = false) String planCode,

            @Parameter(description = "Event type")
            @RequestParam(required = false) String eventType,

            @Parameter(description = "Start date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,

            @Parameter(description = "End date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,

            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = 1L; // System tenant

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        log.info("REST request to search plan operations with filters");

        Page<PlanOperationLogDTO> results = subscriptionHistoryService.searchPlanOperations(
                tenantId, planCode, eventType, start, end, pageable);
        return ResponseEntity.ok(results);
    }
}