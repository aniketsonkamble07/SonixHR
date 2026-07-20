package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.PlatformDashboardDTO;
import com.sonixhr.dto.platform.SystemHealthDTO;
import com.sonixhr.service.platform.PlatformDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformDashboardController {

    private static final Logger log = LoggerFactory.getLogger(PlatformDashboardController.class);

    private final PlatformDashboardService dashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_METRICS')")
    public ResponseEntity<PlatformDashboardDTO> getDashboard(
            @RequestParam(defaultValue = "30") int trendDays) {
        log.info("REST request to get platform dashboard metrics");
        PlatformDashboardDTO stats = dashboardService.getDashboard(trendDays);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_HEALTH')")
    public ResponseEntity<SystemHealthDTO> getHealth() {
        log.info("REST request to get platform system health status");
        SystemHealthDTO health = dashboardService.getSystemHealth();
        return ResponseEntity.ok(health);
    }
}
