package com.sonixhr.security;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantExpirationService {

    private final TenantRepository tenantRepository;

    // Allowed paths for expired/archived tenants (READ-ONLY billing/subscription access)
    private static final List<String> ALLOWED_PATHS = Arrays.asList(
            // Auth endpoints
            "/api/tenant/auth/logout",
            "/api/tenant/auth/refresh",
            "/api/tenant/auth/me",
            "/api/tenant/auth/verify-token",

            // Subscription & Billing (READ-ONLY)
            "/api/tenant/subscription",
            "/api/tenant/subscriptions",
            "/api/tenant/subscription/status",
            "/api/tenant/billing",
            "/api/tenant/invoices",
            "/api/tenant/payment-methods",
            "/api/tenant/upgrade-plan",
            "/api/tenant/cancel-subscription",
            "/api/tenant/reactivate-subscription",
            "/api/tenant/billing/portal",
            "/api/tenant/export/data-request",
            "/api/tenants/current/subscription",
            "/api/tenants/my-tenant"
    );

    // Path prefixes that are allowed for expired tenants (READ-ONLY)
    private static final List<String> ALLOWED_PREFIXES = Arrays.asList(
            "/api/tenant/subscription/",
            "/api/tenant/subscriptions/",
            "/api/tenant/billing/",
            "/api/tenant/invoices/",
            "/api/tenant/payment/"
    );

    /**
     * Check if a path is allowed for expired/archived tenants
     */
    public boolean isPathAllowedForExpiredTenant(String path) {
        if (path == null) return false;

        // Check exact matches
        for (String allowedPath : ALLOWED_PATHS) {
            if (path.equals(allowedPath)) {
                return true;
            }
        }

        // Check prefix matches
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a path is a read-only operation (GET request)
     */
    public boolean isReadOnlyOperation(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    /**
     * Check if tenant is active and not expired
     */
    public boolean isTenantActive(Long tenantId) {
        if (tenantId == null) return false;
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return false;

        Boolean isActive = tenant.getIsActive();

        // Tenant is active if:
        // 1. isActive = true AND
        // 2. planStatus = ACTIVE
        return Boolean.TRUE.equals(isActive) && tenant.getPlanStatus() == com.sonixhr.enums.PlanStatus.ACTIVE;
    }

    /**
     * Get tenant expiration status
     */
    public TenantStatus getTenantStatus(Long tenantId) {
        if (tenantId == null) {
            return TenantStatus.UNKNOWN;
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return TenantStatus.NOT_FOUND;
        }

        if (tenant.getStatus() == com.sonixhr.enums.UserStatus.DELETED) {
            return TenantStatus.DELETED;
        }

        Boolean isActive = tenant.getIsActive();
        TenantDataStatus dataStatus = tenant.getDataStatus();

        // Check if tenant is suspended (isActive = false)
        if (!Boolean.TRUE.equals(isActive)) {
            return TenantStatus.SUSPENDED;
        }

        // If subscription plan status is ACTIVE, the tenant is active
        if (tenant.getPlanStatus() == com.sonixhr.enums.PlanStatus.ACTIVE) {
            return TenantStatus.ACTIVE;
        }

        // Check data status for expired/suspended/archived tenants
        if (dataStatus == null) {
            return TenantStatus.ACTIVE;
        }

        switch (dataStatus) {
            case EXPIRED:
                return TenantStatus.EXPIRED;
            case RETAINED:
                return TenantStatus.RETAINED;
            case ARCHIVED:
            case ELIGIBLE_FOR_DELETION:
                return TenantStatus.ELIGIBLE_FOR_DELETION;
            default:
                return TenantStatus.ACTIVE;
        }
    }

    /**
     * Get message for tenant status
     */
    public String getTenantStatusMessage(TenantStatus status) {
        switch (status) {
            case ACTIVE:
                return "Your subscription is active.";
            case EXPIRED:
                return "Your subscription has expired. Please renew to continue accessing all features.";
            case RETAINED:
                return "Your subscription has expired. Your data is retained for 30 days. Please renew to reactivate.";
            case ELIGIBLE_FOR_DELETION:
                return "Your subscription has been marked for deletion. Your data will be permanently deleted soon. Please contact support if this is an error.";
            case SUSPENDED:
                return "Organization account is not active. Please contact your admin team.";
            case DELETED:
                return "Tenant has been deleted. Please contact support.";
            case NOT_FOUND:
                return "Tenant not found.";
            default:
                return "Unknown tenant status.";
        }
    }

    /**
     * Check if tenant has exceeded grace period
     */
    public boolean hasExceededGracePeriod(Long tenantId) {
        if (tenantId == null) return true;

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return true;

        // If data status is RETAINED, tenant is in grace period
        // If data status is ARCHIVED or ELIGIBLE_FOR_DELETION, grace period has exceeded
        return tenant.getDataStatus() == TenantDataStatus.ARCHIVED ||
                tenant.getDataStatus() == TenantDataStatus.ELIGIBLE_FOR_DELETION;
    }

    /**
     * Tenant status enum
     */
    public enum TenantStatus {
        ACTIVE("Active"),
        EXPIRED("Expired"),
        RETAINED("Retained - 30 day grace period"),
        ELIGIBLE_FOR_DELETION("Ready for permanent deletion"),
        SUSPENDED("Suspended"),
        DELETED("Deleted"),
        NOT_FOUND("Not Found"),
        UNKNOWN("Unknown");

        private final String displayName;

        TenantStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}