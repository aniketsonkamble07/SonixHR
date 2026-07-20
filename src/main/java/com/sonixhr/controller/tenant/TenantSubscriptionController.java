package com.sonixhr.controller.tenant;

import com.sonixhr.dto.tenant.CancelSubscriptionRequest;
import com.sonixhr.dto.tenant.TenantSubscriptionResponseDTO;
import com.sonixhr.dto.tenant.UpgradeSubscriptionRequest;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.CancellationType;
import com.sonixhr.service.tenant.TenantSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * GET /api/tenant/subscriptions/current
     * Get current active subscription for the authenticated tenant
     */
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    public ResponseEntity<TenantSubscriptionResponseDTO> getCurrentSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get current subscription for tenant: {}", currentEmployee.getTenantId());
        Long tenantId = currentEmployee.getTenantId();
        TenantSubscriptionResponseDTO response = subscriptionService.currentSubscription(tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/tenant/subscriptions/cancel
     * Cancel a subscription by ID with request body
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> cancelSubscription(
            @Valid @RequestBody CancelSubscriptionRequest request) {

        log.info("Cancelling subscription with ID: {}", request.getSubscriptionId());

        TenantSubscriptionResponseDTO result = subscriptionService.cancelSubscription(
                request.getSubscriptionId(),
                CancellationType.IMMEDIATE,
                request.getReason() != null ? request.getReason() : "CUSTOMER_REQUEST"
        );

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/tenant/subscriptions/renew
     * Renew the current subscription for the authenticated tenant
     * No request body needed - uses authenticated employee
     */
    @PostMapping("/renew")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> renewSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to renew subscription for tenant: {}", currentEmployee.getTenantId());
        Long tenantId = currentEmployee.getTenantId();
        TenantSubscriptionResponseDTO response = subscriptionService.renewSubscriptionForTenant(tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/tenant/subscriptions/upgrade
     * Upgrade subscription to a new plan
     */
    @PostMapping("/upgrade")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> upgradeSubscription(
            @AuthenticationPrincipal Employee currentEmployee,
            @Valid @RequestBody UpgradeSubscriptionRequest request) {

        log.info("Upgrading subscription to plan: {} for tenant: {}",
                request.getPlanType(), currentEmployee.getTenantId());

        // Use tenant ID from authenticated employee
        Long tenantId = currentEmployee.getTenantId();

        TenantSubscriptionResponseDTO result = subscriptionService.upgradeSubscription(
                tenantId,
                request.getPlanType()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/tenant/subscriptions/history
     * Get subscription history for the authenticated tenant
     */
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    public ResponseEntity<List<TenantSubscriptionResponseDTO>> getSubscriptionHistory(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get subscription history for tenant: {}", currentEmployee.getTenantId());
        Long tenantId = currentEmployee.getTenantId();
        List<TenantSubscriptionResponseDTO> response = subscriptionService.getSubscriptionHistory(tenantId);
        return ResponseEntity.ok(response);
    }
}