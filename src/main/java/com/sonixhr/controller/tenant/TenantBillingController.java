// controller/tenant/TenantBillingController.java
package com.sonixhr.controller.tenant;

import com.sonixhr.dto.subscription.*;
import com.sonixhr.security.SecurityUtils;
import com.sonixhr.service.subscription.TenantSubscriptionService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tenant/billing")
@RequiredArgsConstructor
@Tag(name = "Tenant Billing", description = "APIs for tenant billing and subscription management")
@SecurityRequirement(name = "bearerAuth")
public class TenantBillingController {

    private final TenantSubscriptionService subscriptionService;
    private final PlatformSubscriptionPlanService planService;
    private final SubscriptionHistoryService historyService;
    private final SecurityUtils securityUtils;

    // =====================================================
    // CURRENT SUBSCRIPTION
    // =====================================================

    @GetMapping("/subscription/current")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current subscription",
            description = "Retrieves the current subscription details for the tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "No active subscription found")
    })
    public ResponseEntity<TenantSubscriptionResponseDTO> getCurrentSubscription() {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get current subscription for tenant: {}", tenantId);
        TenantSubscriptionResponseDTO response = subscriptionService.getCurrentSubscription(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // AVAILABLE PLANS
    // =====================================================

    @GetMapping("/plans")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get available subscription plans",
            description = "Retrieves all available subscription plans for the tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<List<SubscriptionPlanDTO>> getAvailablePlans() {
        log.info("REST request to get available subscription plans");
        List<SubscriptionPlanDTO> plans = planService.getPublicPlans();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription plan by ID",
            description = "Retrieves a specific subscription plan by ID")
    public ResponseEntity<SubscriptionPlanDTO> getPlanById(
            @Parameter(description = "Plan ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to get subscription plan by id: {}", id);
        return ResponseEntity.ok(planService.getPlanById(id));
    }

    // =====================================================
    // RENEW SUBSCRIPTION
    // =====================================================

    @PostMapping("/subscription/renew")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Renew subscription",
            description = "Renews the current subscription for the tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription renewed successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot renew subscription"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "No active subscription found")
    })
    public ResponseEntity<TenantSubscriptionResponseDTO> renewSubscription() {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to renew subscription for tenant: {}", tenantId);
        TenantSubscriptionResponseDTO response = subscriptionService.renewSubscriptionForTenant(tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/subscription/renew/{subscriptionId}")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    @Operation(summary = "Renew specific subscription",
            description = "Renews a specific subscription by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription renewed successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot renew subscription"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
    public ResponseEntity<TenantSubscriptionResponseDTO> renewSubscriptionById(
            @Parameter(description = "Subscription ID", required = true)
            @PathVariable Long subscriptionId) {
        log.info("REST request to renew subscription by id: {}", subscriptionId);
        TenantSubscriptionResponseDTO response = subscriptionService.renewSubscription(subscriptionId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // ACTIVATE NEW SUBSCRIPTION
    // =====================================================

    @PostMapping("/subscription/activate")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    @Operation(summary = "Activate new subscription",
            description = "Activates a new subscription for the tenant with the specified plan")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription activated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<TenantSubscriptionResponseDTO> activateSubscription(
            @Parameter(description = "Plan ID", required = true)
            @RequestParam Long planId) {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to activate subscription for tenant: {} with plan: {}", tenantId, planId);
        TenantSubscriptionResponseDTO response = subscriptionService.activateSubscription(tenantId, planId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPGRADE SUBSCRIPTION
    // =====================================================

    @PostMapping("/subscription/upgrade")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    @Operation(summary = "Upgrade subscription",
            description = "Upgrades the current subscription to a higher plan")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription upgraded successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot upgrade subscription"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<TenantSubscriptionResponseDTO> upgradeSubscription(
            @Valid @RequestBody UpgradeSubscriptionRequest request) {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to upgrade subscription for tenant: {} to plan: {}", tenantId, request.getPlanType());
        TenantSubscriptionResponseDTO response = subscriptionService.upgradeSubscription(tenantId, request.getPlanType());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // CANCEL SUBSCRIPTION
    // =====================================================

    @PostMapping("/subscription/cancel")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    @Operation(summary = "Cancel subscription",
            description = "Cancels the current subscription")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot cancel subscription"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "No active subscription found")
    })
    public ResponseEntity<TenantSubscriptionResponseDTO> cancelSubscription(
            @Parameter(description = "Cancellation reason")
            @RequestParam(required = false) String reason) {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to cancel subscription for tenant: {}", tenantId);
        TenantSubscriptionResponseDTO response = subscriptionService.cancelSubscription(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // SUBSCRIPTION HISTORY
    // =====================================================

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get subscription history",
            description = "Retrieves the subscription history for the tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Page<SubscriptionHistoryDTO>> getSubscriptionHistory(
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to get subscription history for tenant: {}", tenantId);
        Page<SubscriptionHistoryDTO> history = historyService.getSubscriptionHistory(tenantId, pageable);
        return ResponseEntity.ok(history);
    }

    // =====================================================
    // SUBSCRIPTION STATUS
    // =====================================================

    @GetMapping("/subscription/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get subscription status",
            description = "Returns the current subscription status for the logged-in tenant."
    )
    public ResponseEntity<SubscriptionStatusResponse> getSubscriptionStatus(
            Authentication authentication) {

        Long tenantId = securityUtils.getCurrentTenantId();

        Collection<? extends GrantedAuthority> authorities =
                authentication != null ? authentication.getAuthorities() : Collections.emptyList();

        return ResponseEntity.ok(
                subscriptionService.getSubscriptionStatus(tenantId, authorities)
        );
    }

    // =====================================================
    // RENEWAL CHECK
    // =====================================================

    @GetMapping("/subscription/check-renewal")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if subscription can be renewed",
            description = "Checks if the subscription is eligible for renewal")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Check completed successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<RenewalCheckResponse> checkRenewalEligibility() {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to check renewal eligibility for tenant: {}", tenantId);

        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(tenantId);
        long daysUntilExpiry = subscriptionService.getDaysUntilExpiry(tenantId);
        com.sonixhr.enums.PlanStatus status = subscriptionService.getSubscriptionStatus(tenantId);

        boolean canRenew = false;
        String message = "";

        if (status == com.sonixhr.enums.PlanStatus.EXPIRED) {
            canRenew = true;
            message = "Your subscription has expired. You can renew to reactivate your account.";
        } else if (status == com.sonixhr.enums.PlanStatus.ACTIVE && daysUntilExpiry > 0) {
            canRenew = true;
            message = "Your subscription is active and can be renewed.";
        } else if (status == com.sonixhr.enums.PlanStatus.ACTIVE && daysUntilExpiry <= 0) {
            canRenew = false;
            message = "Your subscription has expired. Please contact support.";
        } else if (status == com.sonixhr.enums.PlanStatus.CANCELLED) {
            canRenew = false;
            message = "Your subscription has been cancelled. Please contact support.";
        } else {
            canRenew = false;
            message = "Your subscription is in " + status + " status. Please contact support.";
        }

        RenewalCheckResponse response = RenewalCheckResponse.builder()
                .canRenew(canRenew)
                .message(message)
                .currentStatus(status)
                .daysUntilExpiry(daysUntilExpiry)
                .hasActiveSubscription(hasActiveSubscription)
                .build();

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // SUBSCRIPTION VALIDATION (for frontend)
    // =====================================================

    @GetMapping("/subscription/validate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate subscription",
            description = "Validates if the tenant has an active subscription")
    public ResponseEntity<SubscriptionValidationResponse> validateSubscription() {
        Long tenantId = securityUtils.getCurrentTenantId();
        log.info("REST request to validate subscription for tenant: {}", tenantId);

        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(tenantId);
        com.sonixhr.enums.PlanStatus status = subscriptionService.getSubscriptionStatus(tenantId);
        long daysUntilExpiry = subscriptionService.getDaysUntilExpiry(tenantId);

        SubscriptionValidationResponse response = SubscriptionValidationResponse.builder()
                .valid(hasActiveSubscription)
                .status(status)
                .daysUntilExpiry(daysUntilExpiry)
                .message(hasActiveSubscription ?
                        "Subscription is active" :
                        "Subscription is not active. Please renew.")
                .build();

        return ResponseEntity.ok(response);
    }


    // =====================================================
    // INNER CLASSES FOR RESPONSES
    // =====================================================



    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RenewalCheckResponse {
        private Boolean canRenew;
        private String message;
        private com.sonixhr.enums.PlanStatus currentStatus;
        private Long daysUntilExpiry;
        private Boolean hasActiveSubscription;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SubscriptionValidationResponse {
        private Boolean valid;
        private com.sonixhr.enums.PlanStatus status;
        private Long daysUntilExpiry;
        private String message;
    }
}