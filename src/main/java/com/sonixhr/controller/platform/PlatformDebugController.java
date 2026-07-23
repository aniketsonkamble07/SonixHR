package com.sonixhr.controller.platform;

import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Only loaded in the "dev" profile. Will never be registered as a bean in prod.
// Run with: -Dspring.profiles.active=dev
// Or set spring.profiles.active=dev in application-dev.yml.

@Slf4j
@Profile("dev")
@RestController
@RequestMapping("/api/platform/debug")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('VIEW_SYSTEM_METRICS', 'VIEW_SYSTEM_SETTINGS', 'VIEW_PLATFORM_ROLES', 'VIEW_PLATFORM_ADMINS')")
public class PlatformDebugController {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformPermissionRepository permissionRepository;
    private final PlatformRoleRepository roleRepository;
    private final com.sonixhr.service.subscription.SubscriptionSchedulerService schedulerService;
    private final com.sonixhr.repository.tenant.TenantSubscriptionRepository subscriptionRepository;
    private final com.sonixhr.repository.tenant.TenantRepository tenantRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @GetMapping("/security-context")
    public ResponseEntity<Map<String, Object>> debugSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> result = new HashMap<>();
        result.put("authenticationPresent", auth != null);
        result.put("authenticated", auth != null && auth.isAuthenticated());

        if (auth != null) {
            result.put("name", auth.getName());
            result.put("principalClass", auth.getPrincipal().getClass().getName());
            result.put("authorities", auth.getAuthorities().toString());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/test-auth")
    public ResponseEntity<Map<String, Object>> testAuth(
            @AuthenticationPrincipal Object principal,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "message", "No authentication found"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("name", authentication.getName());
        response.put("authorities", authentication.getAuthorities().toString());

        if (principal instanceof PlatformUser user) {
            response.put("userType", "PLATFORM");
            response.put("email", user.getEmail());
            response.put("fullName", user.getFullName());
        } else if (principal instanceof UserDetails ud) {
            response.put("userType", "USERDETAILS");
            response.put("email", ud.getUsername());
        } else {
            response.put("userType", "UNKNOWN");
            response.put("principalClass", principal.getClass().getName());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/platform-setup")
    public ResponseEntity<Map<String, Object>> checkPlatformSetup() {
        Map<String, Object> info = new HashMap<>();

        info.put("totalPermissions", permissionRepository.count());

        List<PlatformRole> roles = roleRepository.findAll();
        info.put("totalRoles", roles.size());
        info.put("roles", roles.stream()
                .map(r -> Map.of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "permissionCount", r.getPermissions() != null ? r.getPermissions().size() : 0
                ))
                .collect(Collectors.toList()));

        platformUserRepository.findByEmail("admin@sonixhr.com").ifPresentOrElse(
                admin -> {
                    info.put("adminExists", true);
                    info.put("adminId", admin.getId());
                    info.put("adminStatus", admin.getStatus());
                    info.put("adminRoles", admin.getRoles().size());
                    info.put("adminAuthorities", admin.getAuthorities().size());
                },
                () -> {
                    info.put("adminExists", false);
                    info.put("expectedEmail", "admin@sonixhr.com");
                }
        );

        return ResponseEntity.ok(info);
    }

    @GetMapping("/trigger-scheduler")
    public ResponseEntity<Map<String, Object>> triggerScheduler() {
        log.info("Debugging trigger: running subscription lifecycle scheduler");
        schedulerService.runSubscriptionLifecycle();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Subscription lifecycle scheduler executed successfully"
        ));
    }

    @org.springframework.web.bind.annotation.PostMapping("/adjust-subscription-dates")
    public ResponseEntity<Map<String, Object>> adjustSubscriptionDates(
            @org.springframework.web.bind.annotation.RequestParam Long tenantId,
            @org.springframework.web.bind.annotation.RequestParam int daysOffset,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean autoRenew) {
        log.info("Adjusting subscription dates for tenant: {}, offset: {}, autoRenew: {}", tenantId, daysOffset, autoRenew);
        
        com.sonixhr.entity.tenant.TenantSubscription sub = subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("No current subscription found for tenant"));

        java.time.LocalDateTime end = java.time.LocalDateTime.now(java.time.ZoneId.of("UTC")).plusDays(daysOffset);
        sub.setBillingPeriodEnd(end);
        if (sub.getBillingPeriodStart() != null && sub.getBillingPeriodStart().isAfter(end)) {
            sub.setBillingPeriodStart(end.minusMonths(1));
        }
        if (autoRenew != null) {
            sub.setAutoRenew(autoRenew);
        }
        
        subscriptionRepository.saveAndFlush(sub);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "billingPeriodEnd", sub.getBillingPeriodEnd().toString(),
            "autoRenew", sub.getAutoRenew() != null ? sub.getAutoRenew() : false
        ));
    }

    @org.springframework.web.bind.annotation.PostMapping("/adjust-tenant-dates")
    public ResponseEntity<Map<String, Object>> adjustTenantDates(
            @org.springframework.web.bind.annotation.RequestParam Long tenantId,
            @org.springframework.web.bind.annotation.RequestParam int expiredDaysAgo,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String dataStatus) {
        log.info("Adjusting tenant dates for tenant: {}, expiredDaysAgo: {}, dataStatus: {}", tenantId, expiredDaysAgo, dataStatus);
        
        com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        tenant.setExpiredAt(java.time.LocalDateTime.now(java.time.ZoneId.of("UTC")).minusDays(expiredDaysAgo));
        if (dataStatus != null) {
            tenant.setDataStatus(com.sonixhr.enums.TenantDataStatus.valueOf(dataStatus));
        }
        
        tenantRepository.saveAndFlush(tenant);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "expiredAt", tenant.getExpiredAt().toString(),
            "dataStatus", tenant.getDataStatus().toString()
        ));
    }

    @GetMapping("/query-subscriptions")
    public ResponseEntity<java.util.List<Map<String, Object>>> querySubscriptions(
            @org.springframework.web.bind.annotation.RequestParam Long tenantId) {
        String sql = "SELECT id, tenant_id, subscription_plan_id, plan_name, plan_status, is_active, is_current, billing_period_end, auto_renew FROM tenant_subscriptions WHERE tenant_id = ? ORDER BY id DESC";
        java.util.List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, tenantId);
        return ResponseEntity.ok(list);
    }
}