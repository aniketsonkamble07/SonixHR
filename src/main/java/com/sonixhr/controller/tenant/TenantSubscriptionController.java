package com.sonixhr.controller.tenant;

import com.sonixhr.dto.tenant.CancelSubscriptionRequest;
import com.sonixhr.dto.subscription.TenantSubscriptionResponseDTO;
import com.sonixhr.dto.subscription.UpgradeSubscriptionRequest;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.CancellationType;
import com.sonixhr.service.subscription.TenantSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tenant/subscriptions")
@RequiredArgsConstructor
public class TenantSubscriptionController {

    private final TenantSubscriptionService subscriptionService;

    // =====================================================
    // CURRENT SUBSCRIPTION
    // =====================================================

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TenantSubscriptionResponseDTO> getCurrentSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get current subscription for tenant: {}", tenantId);
        return ResponseEntity.ok(subscriptionService.currentSubscription(tenantId));
    }

    // =====================================================
    // CANCEL SUBSCRIPTION
    // =====================================================

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> cancelSubscription(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) CancelSubscriptionRequest request) {

        Long tenantId = currentEmployee.getTenantId();

        // 1. If request body is provided, use it
        if (request != null && request.getSubscriptionId() != null) {
            log.info("REST request to cancel subscription: {}", request.getSubscriptionId());
            TenantSubscriptionResponseDTO result = subscriptionService.cancelSubscription(
                    request.getSubscriptionId(),
                    CancellationType.IMMEDIATE,
                    request.getReason() != null ? request.getReason() : "CUSTOMER_REQUEST"
            );
            return ResponseEntity.ok(result);
        }

        // 2. Otherwise, cancel the current active subscription for this tenant
        log.info("REST request to cancel subscription for tenant: {}", tenantId);
        String cancelReason = reason != null ? reason : "CUSTOMER_REQUEST";
        TenantSubscriptionResponseDTO current = subscriptionService.currentSubscription(tenantId);
        TenantSubscriptionResponseDTO result = subscriptionService.cancelSubscription(
                current.getId(),
                CancellationType.IMMEDIATE,
                cancelReason
        );
        return ResponseEntity.ok(result);
    }

    // =====================================================
    // RENEW SUBSCRIPTION
    // =====================================================

    @PostMapping("/renew")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> renewSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to renew subscription for tenant: {}", tenantId);
        return ResponseEntity.ok(subscriptionService.renewSubscriptionForTenant(tenantId));
    }

    // =====================================================
    // UPGRADE SUBSCRIPTION
    // =====================================================

    @PostMapping("/upgrade")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> upgradeSubscription(
            @AuthenticationPrincipal Employee currentEmployee,
            @Valid @RequestBody UpgradeSubscriptionRequest request) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to upgrade subscription for tenant: {} to plan: {}", tenantId, request.getPlanType());
        TenantSubscriptionResponseDTO result = subscriptionService.upgradeSubscription(
                tenantId,
                request.getPlanType()
        );
        return ResponseEntity.ok(result);
    }

    // =====================================================
    // ACTIVATE NEW SUBSCRIPTION
    // =====================================================

    @PostMapping("/activate")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> activateSubscription(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam Long planId) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to activate subscription for tenant: {} with plan: {}", tenantId, planId);
        TenantSubscriptionResponseDTO response = subscriptionService.activateSubscription(tenantId, planId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // REACTIVATE EXPIRED SUBSCRIPTION
    // =====================================================

    @PostMapping("/reactivate")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> reactivateSubscription(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam(required = false) Long planId) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to reactivate subscription for tenant: {} with plan: {}", tenantId, planId);
        TenantSubscriptionResponseDTO response = subscriptionService.reactivateSubscription(tenantId, planId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // SUBSCRIPTION HISTORY
    // =====================================================

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    public ResponseEntity<Page<TenantSubscriptionResponseDTO>> getSubscriptionHistory(
            @AuthenticationPrincipal Employee currentEmployee,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get paginated subscription history for tenant: {}", tenantId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionHistory(tenantId, pageable));
    }

    @GetMapping("/history/all")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    public ResponseEntity<List<TenantSubscriptionResponseDTO>> getAllSubscriptionHistory(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get all subscription history for tenant: {}", tenantId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionHistory(tenantId));
    }

    // =====================================================
    // SUBSCRIPTION STATUS
    // =====================================================

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.sonixhr.dto.subscription.SubscriptionStatusResponse> getSubscriptionStatus(
            @AuthenticationPrincipal Employee currentEmployee,
            org.springframework.security.core.Authentication authentication) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get subscription status for tenant: {}", tenantId);
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
                authentication != null ? authentication.getAuthorities() : java.util.Collections.emptyList();
        return ResponseEntity.ok(subscriptionService.getSubscriptionStatus(tenantId, authorities));
    }

    @GetMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TenantBillingController.SubscriptionValidationResponse> hasActiveSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to validate subscription for tenant: {}", tenantId);

        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(tenantId);
        com.sonixhr.enums.PlanStatus status = subscriptionService.getSubscriptionStatus(tenantId);
        long daysUntilExpiry = subscriptionService.getDaysUntilExpiry(tenantId);

        TenantBillingController.SubscriptionValidationResponse response = TenantBillingController.SubscriptionValidationResponse.builder()
                .valid(hasActiveSubscription)
                .status(status)
                .daysUntilExpiry(daysUntilExpiry)
                .message(hasActiveSubscription ?
                        "Subscription is active" :
                        "Subscription is not active. Please renew.")
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/days-until-expiry")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getDaysUntilExpiry(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get days until expiry for tenant: {}", tenantId);
        return ResponseEntity.ok(subscriptionService.getDaysUntilExpiry(tenantId));
    }

    // =====================================================
    // RENEWAL CHECK
    // =====================================================

    @GetMapping("/check-renewal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TenantBillingController.RenewalCheckResponse> checkRenewalEligibility(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
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

        TenantBillingController.RenewalCheckResponse response = TenantBillingController.RenewalCheckResponse.builder()
                .canRenew(canRenew)
                .message(message)
                .currentStatus(status)
                .daysUntilExpiry(daysUntilExpiry)
                .hasActiveSubscription(hasActiveSubscription)
                .build();

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // SYNC STATUS
    // =====================================================

    @PostMapping("/sync-status")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<java.util.Map<String, Object>> syncSubscriptionStatus(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to sync subscription status for tenant: {}", tenantId);
        subscriptionService.syncTenantSubscriptionStatus(tenantId);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("message", "Tenant status synchronized successfully");
        return ResponseEntity.ok(response);
    }
}