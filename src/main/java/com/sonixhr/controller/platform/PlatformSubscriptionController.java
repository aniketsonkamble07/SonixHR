package com.sonixhr.controller.platform;

import com.sonixhr.dto.subscription.SubscriptionDashboardDTO;
import com.sonixhr.service.subscription.PlatformSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/platform/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Platform Subscriptions", description = "APIs for platform subscription statistics and dashboard")
public class PlatformSubscriptionController {

    private final PlatformSubscriptionService subscriptionService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_METRICS') or hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('MANAGE_SUBSCRIPTIONS')")
    @Operation(summary = "Get platform subscriptions dashboard metrics")
    public ResponseEntity<SubscriptionDashboardDTO> getDashboard() {
        log.info("REST request to get platform subscriptions dashboard");
        return ResponseEntity.ok(subscriptionService.getDashboard());
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_METRICS') or hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('MANAGE_SUBSCRIPTIONS')")
    @Operation(summary = "Get platform subscription overview statistics")
    public ResponseEntity<Map<String, Object>> getSubscriptionOverview() {
        log.info("REST request to get platform subscription overview");
        return ResponseEntity.ok(subscriptionService.getSubscriptionOverview());
    }
}
