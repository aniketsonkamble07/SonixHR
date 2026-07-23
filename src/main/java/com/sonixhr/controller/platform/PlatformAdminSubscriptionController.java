// controller/platform/PlatformAdminSubscriptionController.java
package com.sonixhr.controller.platform;

import com.sonixhr.dto.subscription.SubscriptionPlanDTO;
import com.sonixhr.dto.subscription.PlanOperationLogDTO;
import com.sonixhr.service.subscription.PlatformSubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/platform/admin/subscription-plans")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTIONS') or hasAuthority('MANAGE_PRICING_PLANS') or hasAuthority('MANAGE_SUBSCRIPTION')")
public class PlatformAdminSubscriptionController {

    private final PlatformSubscriptionPlanService planService;

    // =====================================================
    // PLAN CRUD OPERATIONS
    // =====================================================

    @GetMapping
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        log.info("Platform admin: Getting all subscription plans");
        return ResponseEntity.ok(planService.getAllPlans());
    }

    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionPlanDTO>> getActivePlans() {
        log.info("Platform admin: Getting active subscription plans");
        return ResponseEntity.ok(planService.getActivePlans());
    }

    @GetMapping("/deleted")
    public ResponseEntity<List<SubscriptionPlanDTO>> getDeletedPlans() {
        log.info("Platform admin: Getting deleted subscription plans");
        return ResponseEntity.ok(planService.getDeletedPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionPlanDTO> getPlanById(@PathVariable Long id) {
        log.info("Platform admin: Getting subscription plan by id: {}", id);
        return ResponseEntity.ok(planService.getPlanById(id));
    }

    @PostMapping
    public ResponseEntity<SubscriptionPlanDTO> createPlan(@Valid @RequestBody SubscriptionPlanDTO dto) {
        log.info("Platform admin: Creating subscription plan: {}", dto.getCode());
        SubscriptionPlanDTO created = planService.createPlan(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionPlanDTO> updatePlan(@PathVariable Long id, @Valid @RequestBody SubscriptionPlanDTO dto) {
        log.info("Platform admin: Updating subscription plan: {}", id);
        return ResponseEntity.ok(planService.updatePlan(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        log.info("Platform admin: Deleting subscription plan: {}", id);
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<SubscriptionPlanDTO> restorePlan(@PathVariable Long id) {
        log.info("Platform admin: Restoring subscription plan: {}", id);
        return ResponseEntity.ok(planService.restorePlan(id));
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<SubscriptionPlanDTO> togglePlanActive(@PathVariable Long id) {
        log.info("Platform admin: Toggling subscription plan active status: {}", id);
        return ResponseEntity.ok(planService.togglePlanActive(id));
    }

    // =====================================================
    // PLAN FEATURE MANAGEMENT
    // =====================================================

    @PostMapping("/{id}/features")
    public ResponseEntity<SubscriptionPlanDTO> addFeature(
            @PathVariable Long id,
            @RequestParam String featureCode,
            @RequestParam(required = false) String description) {
        log.info("Platform admin: Adding feature {} to plan: {}", featureCode, id);
        return ResponseEntity.ok(planService.addFeatureToPlan(id, featureCode, description));
    }

    @DeleteMapping("/{id}/features/{featureCode}")
    public ResponseEntity<SubscriptionPlanDTO> removeFeature(
            @PathVariable Long id,
            @PathVariable String featureCode) {
        log.info("Platform admin: Removing feature {} from plan: {}", featureCode, id);
        return ResponseEntity.ok(planService.removeFeatureFromPlan(id, featureCode));
    }

    // =====================================================
    // PLAN HISTORY
    // =====================================================

    @GetMapping("/{id}/history")
    public ResponseEntity<List<PlanOperationLogDTO>> getPlanHistory(@PathVariable Long id) {
        log.info("Platform admin: Getting history for plan: {}", id);
        return ResponseEntity.ok(planService.getPlanOperationHistory(id));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PlanOperationLogDTO>> getAllPlanHistory() {
        log.info("Platform admin: Getting all plan history");
        return ResponseEntity.ok(planService.getAllPlanOperations());
    }

    // =====================================================
    // PLAN STATISTICS
    // =====================================================

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getPlanStatistics() {
        log.info("Platform admin: Getting plan statistics");
        return ResponseEntity.ok(planService.getPlanStatistics());
    }

    @GetMapping("/statistics/usage")
    public ResponseEntity<Map<String, Object>> getPlanUsageStatistics() {
        log.info("Platform admin: Getting plan usage statistics");
        return ResponseEntity.ok(planService.getPlanUsageStatistics());
    }

    @GetMapping("/validate/{code}")
    public ResponseEntity<Boolean> validatePlanCode(@PathVariable String code) {
        log.info("Platform admin: Validating plan code: {}", code);
        return ResponseEntity.ok(planService.isPlanCodeAvailable(code));
    }
}