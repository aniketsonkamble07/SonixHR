package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.SubscriptionDashboardDTO;
import com.sonixhr.service.platform.PlatformSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/platform/subscriptions")
@RequiredArgsConstructor
public class PlatformSubscriptionController {

    private final PlatformSubscriptionService subscriptionService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('VIEW_ANALYTICS')")
    public ResponseEntity<SubscriptionDashboardDTO> getDashboard() {
        log.info("REST request to get platform subscription dashboard analytics");
        SubscriptionDashboardDTO dashboard = subscriptionService.getDashboard();
        return ResponseEntity.ok(dashboard);
    }
}
