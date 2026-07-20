package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.CachedTenantDetails;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSubscriptionValidationService {

    private static final Logger log = LoggerFactory.getLogger(TenantSubscriptionValidationService.class);

    private final TenantRepository tenantRepository;
    private final com.sonixhr.service.platform.FeatureAccessService featureAccessService;

    @Cacheable(value = "tenantDetails", key = "#tenantId", unless = "#result == null")
    public CachedTenantDetails getTenantDetails(Long tenantId) {
        log.debug("Loading tenant details from database for caching: {}", tenantId);
        return tenantRepository.findById(tenantId)
                .map(tenant -> CachedTenantDetails.builder()
                        .tenantId(tenant.getId())
                        .isActive(tenant.getIsActive())
                        .status(tenant.getStatus())
                        .planStatus(tenant.getPlanStatus())
                        .billingPeriodEnd(tenant.getEndsAt())
                        .dataStatus(tenant.getDataStatus())
                        .build())
                .orElse(null);
    }

    @CacheEvict(value = "tenantDetails", key = "#tenantId")
    public void evictTenantDetails(Long tenantId) {
        log.debug("Evicted tenantDetails cache for tenant: {}", tenantId);
    }

    /**
     * Validates if the tenant's subscription is currently active and not expired.
     * If expired in real-time, triggers database updates and invalidates cache.
     */
    public void validateSubscription(Long tenantId) {
        validateSubscription(tenantId, null, "GET", null, null);
    }

    public void validateSubscription(Long tenantId, String requestPath,
            Collection<? extends GrantedAuthority> authorities) {
        validateSubscription(tenantId, requestPath, "GET", authorities, null);
    }

    public void validateSubscription(Long tenantId, String requestPath, String method,
            Collection<? extends GrantedAuthority> authorities, jakarta.servlet.http.HttpServletRequest request) {
        if (tenantId == null) {
            return;
        }

        CachedTenantDetails details = getTenantDetails(tenantId);
        if (details == null) {
            throw new ResourceNotFoundException("Tenant not found");
        }

        // Evaluate path and authority-based restrictions first
        boolean isAllowedGrace = (requestPath != null) && isAllowedGracePath(requestPath);
        boolean isAdmin = (authorities != null) && authorities.stream()
                .anyMatch(a -> a != null && ("MANAGE_SUBSCRIPTION".equalsIgnoreCase(a.getAuthority())
                        || "VIEW_BILLING".equalsIgnoreCase(a.getAuthority())));

        // If a Company Admin attempts to access a self-serve renewal/billing path, do
        // not block on suspended/inactive status
        boolean isSelfServeRenewalAttempt = isAllowedGrace && isAdmin &&
                (details.getPlanStatus() == PlanStatus.EXPIRED || details.getPlanStatus() == PlanStatus.PAST_DUE) &&
                (details.getDataStatus() == null || details.getDataStatus() == TenantDataStatus.RETAINED);

        if (details.getStatus() == UserStatus.DELETED) {
            throw new BusinessException("Tenant account has been deleted");
        }

        // Check plan status
        if (details.getPlanStatus() == PlanStatus.EXPIRED) {
            if (details.getDataStatus() == TenantDataStatus.ARCHIVED) {
                throw new BusinessException(
                        "Subscription has expired and the workspace is archived. Please contact support to restore and renew.");
            }
            if (details.getDataStatus() == TenantDataStatus.ELIGIBLE_FOR_DELETION) {
                throw new BusinessException(
                        "Subscription has expired and the workspace is marked for deletion. Please contact support.");
            }
            // EXPIRED stage (Day 0-30): RETAINED data status
            if (!isSelfServeRenewalAttempt) {
                throw new BusinessException("Subscription has expired. Please log in and renew online.");
            }
        }

        if (details.getPlanStatus() == PlanStatus.SUSPENDED) {
            throw new BusinessException("Subscription is suspended");
        }

        if (details.getPlanStatus() == PlanStatus.CANCELLED) {
            if (details.getBillingPeriodEnd() != null && details.getBillingPeriodEnd().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
                throw new BusinessException("Subscription has expired. Please log in and renew online.");
            }
        }

        // Real-time expiration check
        if (details.getBillingPeriodEnd() != null && details.getBillingPeriodEnd().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
            if (details.getPlanStatus() != PlanStatus.PAST_DUE && details.getPlanStatus() != PlanStatus.EXPIRED) {
                throw new BusinessException("Subscription has expired. Please log in and renew online.");
            }
        }

        if (!isSelfServeRenewalAttempt) {
            if (details.getStatus() == UserStatus.SUSPENDED) {
                throw new BusinessException("Tenant account is suspended");
            }
            if (!details.isActive()) {
                throw new BusinessException("Tenant account is inactive");
            }
        }

        // Enforce plan-level Webhook access restrictions
        if (isWebhookRequest(request, requestPath)) {
            if (!featureAccessService.hasFeature(tenantId, "WEBHOOK_ACCESS")) {
                throw new BusinessException("Webhook access is not enabled for your subscription plan. Please upgrade your plan.");
            }
            if (details.getPlanStatus() == PlanStatus.PAST_DUE) {
                throw new BusinessException("Webhook access is blocked during the grace period.");
            }
        }

        // Enforce plan-level API access restrictions
        if (isApiRequest(request, requestPath)) {
            if (!featureAccessService.hasFeature(tenantId, "API_ACCESS")) {
                throw new BusinessException("API access is not enabled for your subscription plan. Please upgrade your plan.");
            }
            if (details.getPlanStatus() == PlanStatus.PAST_DUE) {
                throw new BusinessException("API access is blocked during the grace period.");
            }
        }

        // Enforce plan-level module feature access restrictions
        if (requestPath != null) {
            String lowerPath = requestPath.toLowerCase();
            
            // 1. Payroll feature check
            if (lowerPath.startsWith("/api/payroll")) {
                if (!featureAccessService.hasFeature(tenantId, "PAYROLL")) {
                    throw new BusinessException("Payroll feature is not enabled for your subscription plan. Please upgrade your plan.");
                }
            }
            
            // 2. Leave Management feature check
            if (lowerPath.contains("/leaves") || lowerPath.contains("/leave")) {
                if (!featureAccessService.hasFeature(tenantId, "LEAVE_MANAGEMENT")) {
                    throw new BusinessException("Leave Management feature is not enabled for your subscription plan. Please upgrade your plan.");
                }
            }
            
            // 3. Attendance feature check
            if (lowerPath.startsWith("/api/attendance") || lowerPath.startsWith("/api/shifts")) {
                if (!featureAccessService.hasFeature(tenantId, "ATTENDANCE")) {
                    throw new BusinessException("Attendance tracking feature is not enabled for your subscription plan. Please upgrade your plan.");
                }
            }
        }

        // Check grace period PAST_DUE restrictions
        if (details.getPlanStatus() == PlanStatus.PAST_DUE) {

            if (authorities != null && requestPath != null && method != null) {
                if (isAdmin) {
                    // Allow Company Admin to access Billing, Renewal, Invoices, Data Export, and
                    // Contact Support only
                    if (!isAllowedGrace) {
                        throw new BusinessException(
                                "Company Admin can only access Billing, Renewal, Invoices, Data Export, and Contact Support pages during the grace period.");
                    }
                } else {
                    // Make the workspace read-only (no create, update, or delete operations)
                    if (!"GET".equalsIgnoreCase(method)) {
                        throw new BusinessException(
                                "The workspace is read-only during the grace period. Create, update, and delete operations are blocked.");
                    }
                }
            }
        }
    }

    private boolean isWebhookRequest(jakarta.servlet.http.HttpServletRequest request, String path) {
        if (path != null && path.toLowerCase().contains("webhook")) {
            return true;
        }
        if (request != null) {
            return request.getHeader("X-Webhook-Signature") != null ||
                   request.getHeader("X-Webhook-Event") != null ||
                   request.getHeader("X-Webhook-Token") != null;
        }
        return false;
    }

    private boolean isApiRequest(jakarta.servlet.http.HttpServletRequest request, String path) {
        if (path != null) {
            if (path.startsWith("/api/public/") &&
                    !path.startsWith("/api/public/register") &&
                    !path.startsWith("/api/public/set-password") &&
                    !path.startsWith("/api/public/check-email")) {
                return true;
            }
        }
        if (request != null) {
            return request.getHeader("X-API-Key") != null ||
                   request.getHeader("X-Api-Key") != null ||
                   request.getHeader("api-key") != null;
        }
        return false;
    }

    private boolean isAllowedGracePath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/tenant/subscriptions") ||
                path.startsWith("/api/export") ||
                path.startsWith("/api/employees/export") ||
                path.startsWith("/api/billing") ||
                path.startsWith("/api/subscriptions") ||
                path.startsWith("/api/employee/support-tickets") ||
                path.equals("/api/tenants/current/subscription") ||
                path.equals("/api/tenants/my-tenant");
    }

}
