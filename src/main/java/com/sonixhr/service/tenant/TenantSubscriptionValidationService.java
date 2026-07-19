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

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSubscriptionValidationService {

    private final TenantRepository tenantRepository;

    @Cacheable(value = "tenantDetails", key = "#tenantId", unless = "#result == null")
    public CachedTenantDetails getTenantDetails(Long tenantId) {
        log.debug("Loading tenant details from database for caching: {}", tenantId);
        return tenantRepository.findById(tenantId)
                .map(tenant -> CachedTenantDetails.builder()
                        .tenantId(tenant.getId())
                        .isActive(tenant.getIsActive())
                        .status(tenant.getStatus())
                        .planStatus(tenant.getPlanStatus())
                        .endsAt(tenant.getEndsAt())
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

    public void validateSubscription(Long tenantId, String requestPath, Collection<? extends GrantedAuthority> authorities) {
        validateSubscription(tenantId, requestPath, "GET", authorities, null);
    }

    public void validateSubscription(Long tenantId, String requestPath, String method, Collection<? extends GrantedAuthority> authorities, jakarta.servlet.http.HttpServletRequest request) {
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

        // If a Company Admin attempts to access a self-serve renewal/billing path, do not block on suspended/inactive status
        boolean isSelfServeRenewalAttempt = isAllowedGrace && isAdmin && 
                (details.getPlanStatus() == PlanStatus.EXPIRED || details.getPlanStatus() == PlanStatus.PAST_DUE) &&
                (details.getDataStatus() == null || details.getDataStatus() == TenantDataStatus.RETAINED);

        if (details.getStatus() == UserStatus.DELETED) {
            throw new BusinessException("Tenant account has been deleted");
        }

        // Check plan status
        if (details.getPlanStatus() == PlanStatus.EXPIRED) {
            if (details.getDataStatus() == TenantDataStatus.ARCHIVED) {
                throw new BusinessException("Subscription has expired and the workspace is archived. Please contact support to restore and renew.");
            }
            if (details.getDataStatus() == TenantDataStatus.ELIGIBLE_FOR_DELETION) {
                throw new BusinessException("Subscription has expired and the workspace is marked for deletion. Please contact support.");
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
            if (details.getEndsAt() != null && details.getEndsAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
                throw new BusinessException("Subscription has expired. Please log in and renew online.");
            }
        }

        // Real-time expiration check
        if (details.getEndsAt() != null && details.getEndsAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
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

        // Check grace period PAST_DUE restrictions
        if (details.getPlanStatus() == PlanStatus.PAST_DUE) {
            // Block API access and webhooks
            if (isApiOrWebhook(request, requestPath)) {
                throw new BusinessException("API access and webhooks are blocked during the grace period.");
            }

            if (authorities != null && requestPath != null && method != null) {
                if (isAdmin) {
                    // Allow Company Admin to access Billing, Renewal, Invoices, Data Export, and Contact Support only
                    if (!isAllowedGrace) {
                        throw new BusinessException("Company Admin can only access Billing, Renewal, Invoices, Data Export, and Contact Support pages during the grace period.");
                    }
                } else {
                    // Make the workspace read-only (no create, update, or delete operations)
                    if (!"GET".equalsIgnoreCase(method)) {
                        throw new BusinessException("The workspace is read-only during the grace period. Create, update, and delete operations are blocked.");
                    }
                }
            }
        }
    }

    private boolean isApiOrWebhook(jakarta.servlet.http.HttpServletRequest request, String path) {
        if (path != null) {
            String lowerPath = path.toLowerCase();
            if (lowerPath.contains("webhook")) {
                return true;
            }
            if (path.startsWith("/api/public/") && 
                !path.startsWith("/api/public/register") && 
                !path.startsWith("/api/public/set-password") && 
                !path.startsWith("/api/public/check-email")) {
                return true;
            }
        }
        if (request != null) {
            if (request.getHeader("X-API-Key") != null || 
                request.getHeader("X-Api-Key") != null || 
                request.getHeader("api-key") != null ||
                request.getHeader("X-Webhook-Signature") != null ||
                request.getHeader("X-Webhook-Event") != null ||
                request.getHeader("X-Webhook-Token") != null) {
                return true;
            }
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
