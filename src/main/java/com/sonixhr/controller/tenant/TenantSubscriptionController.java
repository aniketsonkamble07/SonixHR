package com.sonixhr.controller.tenant;

import com.sonixhr.dto.tenant.TenantSubscriptionResponseDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.service.tenant.TenantSubscriptionService;
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

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    public ResponseEntity<TenantSubscriptionResponseDTO> currentSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get current active subscription");
        Long tenantId = currentEmployee.getTenantId();
        TenantSubscriptionResponseDTO response = subscriptionService.currentSubscription(tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/renew")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> renewSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to renew subscription");
        Long tenantId = currentEmployee.getTenantId();
        TenantSubscriptionResponseDTO response = subscriptionService.renewSubscriptionForTenant(tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> upgradeSubscription(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam String planType) {
        log.info("REST request to upgrade/change subscription to plan: {}", planType);
        Long tenantId = currentEmployee.getTenantId();
        TenantSubscriptionResponseDTO response = subscriptionService.upgradeSubscription(tenantId, planType);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTION')")
    public ResponseEntity<TenantSubscriptionResponseDTO> cancelSubscription(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to cancel subscription");
        Long tenantId = currentEmployee.getTenantId();
        TenantSubscriptionResponseDTO response = subscriptionService.cancelSubscription(tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('VIEW_BILLING')")
    public ResponseEntity<List<TenantSubscriptionResponseDTO>> getSubscriptionHistory(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get subscription history");
        Long tenantId = currentEmployee.getTenantId();
        List<TenantSubscriptionResponseDTO> response = subscriptionService.getSubscriptionHistory(tenantId);
        return ResponseEntity.ok(response);
    }
}
