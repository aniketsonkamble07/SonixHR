// controller/platform/PlatformSubscriptionPlanController.java
package com.sonixhr.controller.platform;

import com.sonixhr.dto.subscription.PlanOperationLogDTO;
import com.sonixhr.dto.subscription.SubscriptionPlanDTO;
import com.sonixhr.service.subscription.PlatformSubscriptionPlanService;
import com.sonixhr.service.subscription.SubscriptionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/platform/subscription-plans", "/api/admin/subscription-plans"})
@RequiredArgsConstructor
@Tag(name = "Platform Subscription Plans", description = "APIs for managing subscription plans")
@SecurityRequirement(name = "bearerAuth")
@SuppressWarnings("null")
public class PlatformSubscriptionPlanController {

    private final PlatformSubscriptionPlanService planService;
    private final SubscriptionHistoryService historyService;

    // =====================================================
    // PUBLIC ENDPOINTS - NO AUTH REQUIRED
    // =====================================================

    @GetMapping("/public")
    @Operation(summary = "Get public subscription plans",
            description = "Retrieves all public subscription plans. No authentication required.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Public plans retrieved successfully")
    })
    public ResponseEntity<List<SubscriptionPlanDTO>> getPublicPlans() {
        log.info("REST request to get public subscription plans");
        return ResponseEntity.ok(planService.getPublicPlans());
    }

    // =====================================================
    // AUTHENTICATED ENDPOINTS
    // =====================================================

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get all subscription plans",
            description = "Retrieves all active subscription plans. Requires VIEW_SUBSCRIPTIONS or VIEW_BILLING authority.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        log.info("REST request to get all subscription plans");
        return ResponseEntity.ok(planService.getAllPlans());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get active subscription plans",
            description = "Retrieves all active subscription plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getActivePlans() {
        log.info("REST request to get active subscription plans");
        return ResponseEntity.ok(planService.getActivePlans());
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Get deleted subscription plans",
            description = "Retrieves all deleted subscription plans. Admin only.")
    public ResponseEntity<List<SubscriptionPlanDTO>> getDeletedPlans() {
        log.info("REST request to get deleted subscription plans");
        return ResponseEntity.ok(planService.getDeletedPlans());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get subscription plan by ID",
            description = "Retrieves a specific subscription plan by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<SubscriptionPlanDTO> getPlanById(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to get subscription plan by id: {}", id);
        return ResponseEntity.ok(planService.getPlanById(id));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get subscription plan by code",
            description = "Retrieves a specific subscription plan by code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<SubscriptionPlanDTO> getPlanByCode(
            @Parameter(description = "Plan code", required = true)
            @PathVariable String code) {
        log.info("REST request to get subscription plan by code: {}", code);
        return ResponseEntity.ok(planService.getPlanByCode(code));
    }

    // =====================================================
    // ADMIN ENDPOINTS - CRUD OPERATIONS
    // =====================================================

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Create subscription plan",
            description = "Creates a new subscription plan. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Plan created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "409", description = "Plan already exists")
    })
    public ResponseEntity<SubscriptionPlanDTO> createPlan(
            @Valid @RequestBody SubscriptionPlanDTO dto) {
        log.info("REST request to create subscription plan: {}", dto.getCode());
        SubscriptionPlanDTO created = planService.createPlan(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Update subscription plan",
            description = "Updates an existing subscription plan. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<SubscriptionPlanDTO> updatePlan(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionPlanDTO dto) {
        log.info("REST request to update subscription plan with id: {}", id);
        return ResponseEntity.ok(planService.updatePlan(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Delete subscription plan",
            description = "Soft deletes a subscription plan. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Plan deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<Void> deletePlan(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to delete subscription plan with id: {}", id);
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Restore subscription plan",
            description = "Restores a soft-deleted subscription plan. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan restored successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<SubscriptionPlanDTO> restorePlan(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to restore subscription plan with id: {}", id);
        return ResponseEntity.ok(planService.restorePlan(id));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Toggle plan active status",
            description = "Toggles the active status of a subscription plan. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan status toggled successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<SubscriptionPlanDTO> togglePlanActive(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to toggle active status for plan with id: {}", id);
        return ResponseEntity.ok(planService.togglePlanActive(id));
    }

    // =====================================================
    // PLAN OPERATION HISTORY ENDPOINTS
    // =====================================================

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get plan operation history",
            description = "Retrieves the operation history for a specific plan")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<List<PlanOperationLogDTO>> getPlanHistory(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to get history for plan with id: {}", id);
        return ResponseEntity.ok(planService.getPlanOperationHistory(id));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get all plan operation history",
            description = "Retrieves all plan operation history")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<PlanOperationLogDTO>> getAllPlanHistory() {
        log.info("REST request to get all plan operation history");
        return ResponseEntity.ok(planService.getAllPlanOperations());
    }

    @GetMapping("/history/search")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Search plan operations",
            description = "Searches plan operations with filters")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Page<PlanOperationLogDTO>> searchPlanOperations(
            @Parameter(description = "Plan code")
            @RequestParam(required = false) String planCode,

            @Parameter(description = "Event type (PLAN_CREATED, PLAN_UPDATED, PLAN_DELETED, PLAN_RESTORED, PLAN_TOGGLED)")
            @RequestParam(required = false) String eventType,

            @Parameter(description = "Start date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,

            @Parameter(description = "End date (format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,

            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = 1L; // System tenant

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        log.info("REST request to search plan operations with filters: planCode={}, eventType={}", planCode, eventType);

        Page<PlanOperationLogDTO> results = historyService.searchPlanOperations(
                tenantId, planCode, eventType, start, end, pageable);
        return ResponseEntity.ok(results);
    }

    // =====================================================
    // PLAN STATISTICS ENDPOINTS
    // =====================================================

    @GetMapping("/statistics/overview")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get plan statistics overview",
            description = "Retrieves statistics about subscription plans")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Map<String, Object>> getPlanStatistics() {
        log.info("REST request to get plan statistics");
        return ResponseEntity.ok(planService.getPlanStatistics());
    }

    @GetMapping("/statistics/usage")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Get plan usage statistics",
            description = "Retrieves usage statistics for subscription plans")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usage statistics retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Map<String, Object>> getPlanUsageStatistics() {
        log.info("REST request to get plan usage statistics");
        return ResponseEntity.ok(planService.getPlanUsageStatistics());
    }

    // =====================================================
    // FEATURE MANAGEMENT ENDPOINTS
    // =====================================================

    @PostMapping("/{id}/features")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Add feature to plan",
            description = "Adds a feature to a subscription plan. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature added successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<SubscriptionPlanDTO> addFeature(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Feature code", required = true)
            @RequestParam String featureCode,
            @Parameter(description = "Feature description")
            @RequestParam(required = false) String description) {
        log.info("REST request to add feature {} to plan with id: {}", featureCode, id);
        SubscriptionPlanDTO plan = planService.addFeatureToPlan(id, featureCode, description);
        return ResponseEntity.ok(plan);
    }

    @DeleteMapping("/{id}/features/{featureCode}")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Remove feature from plan",
            description = "Removes a feature from a subscription plan. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feature removed successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<SubscriptionPlanDTO> removeFeature(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Feature code", required = true)
            @PathVariable String featureCode) {
        log.info("REST request to remove feature {} from plan with id: {}", featureCode, id);
        SubscriptionPlanDTO plan = planService.removeFeatureFromPlan(id, featureCode);
        return ResponseEntity.ok(plan);
    }

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Bulk create subscription plans",
            description = "Creates multiple subscription plans in bulk. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<SubscriptionPlanDTO>> bulkCreatePlans(
            @Valid @RequestBody List<SubscriptionPlanDTO> plans) {
        log.info("REST request to bulk create {} subscription plans", plans.size());
        return ResponseEntity.ok(planService.bulkCreatePlans(plans));
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    @Operation(summary = "Bulk delete subscription plans",
            description = "Soft deletes multiple subscription plans in bulk. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Plans deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Void> bulkDeletePlans(
            @Parameter(description = "Plan IDs (comma-separated)", required = true)
            @RequestParam List<Long> ids) {
        log.info("REST request to bulk delete {} subscription plans", ids.size());
        planService.bulkDeletePlans(ids);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // VALIDATION ENDPOINTS
    // =====================================================

    @GetMapping("/validate/{code}")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('VIEW_BILLING')")
    @Operation(summary = "Validate plan code",
            description = "Checks if a plan code is available")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Boolean> validatePlanCode(
            @Parameter(description = "Plan code to validate", required = true)
            @PathVariable String code) {
        log.info("REST request to validate plan code: {}", code);
        return ResponseEntity.ok(planService.isPlanCodeAvailable(code));
    }
}