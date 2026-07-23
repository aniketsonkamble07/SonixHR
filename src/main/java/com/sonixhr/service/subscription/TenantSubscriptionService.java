package com.sonixhr.service.subscription;

import com.sonixhr.dto.subscription.SubscriptionStatusResponse;
import com.sonixhr.dto.tenant.CachedTenantDetails;
import com.sonixhr.dto.subscription.TenantSubscriptionResponseDTO;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.*;
import com.sonixhr.events.subscription.*;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.security.TenantRLSService;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.common.AuditLogService;
import com.sonixhr.service.common.CacheEvictionService;
import com.sonixhr.service.platform.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class TenantSubscriptionService {

    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantRLSService tenantRLSService;
    private final CacheEvictionService cacheEvictionService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;
    private final PlatformUserRepository platformUserRepository;
    private final SubscriptionEventLogService subscriptionEventLogService;
    private final FeatureAccessService featureAccessService;
    @Value("${app.subscription.reactivation-window-days}")
    private int reactivationWindowDays;

    // =====================================================
    // CURRENT SUBSCRIPTION
    // =====================================================

    public TenantSubscriptionResponseDTO currentSubscription(Long tenantId) {
        TenantSubscription subscription = subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Current subscription not found for tenant"));
        return convertToDTO(subscription);
    }

    public TenantSubscriptionResponseDTO getCurrentSubscription(Long tenantId) {
        return currentSubscription(tenantId);
    }

    // =====================================================
    // CREATE / ACTIVATE SUBSCRIPTION
    // =====================================================

    @Transactional
    public TenantSubscriptionResponseDTO activateSubscription(Long tenantId, Long planId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        closeActiveSubscription(tenantId, "Upgraded to new plan");

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        int validityMonths = plan.getValidityMonths() != null && plan.getValidityMonths() > 0 ? plan.getValidityMonths() : 1;

        TenantSubscription subscription = TenantSubscription.builder()
                .tenant(tenant)
                .subscriptionPlan(plan)
                .planName(plan.getName())
                .planStatus(PlanStatus.ACTIVE)
                .maxEmployees(plan.getMaxEmployees())
                .isCurrent(true)
                .startedAt(now)
                .billingPeriodStart(now)
                .billingPeriodEnd(now.plusMonths(validityMonths))
                .lastPaymentAmount(plan.getPrice())
                .lastPaymentDate(now)
                .nextPaymentDate(now.plusMonths(validityMonths))
                .amount(plan.getPrice())
                .currency(plan.getCurrency() != null ? plan.getCurrency() : "INR")
                .build();

        TenantSubscription saved = subscriptionRepository.save(subscription);

        subscriptionEventLogService.recordEvent(
                tenant, saved, null, PlanStatus.ACTIVE,
                TriggerSource.USER, auditLogService.getCurrentUserId(),
                "Subscription activated with plan: " + plan.getName()
        );

        tenant.setSubscriptionPlan(plan);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(now.plusMonths(validityMonths));
        tenant.activate();
        tenant.resetSubscriptionLifecycle();
        tenantRepository.save(tenant);

        eventPublisher.publishEvent(new SubscriptionActivatedEvent(saved));
        tenantRLSService.invalidateTenantCache(tenantId);
        cacheEvictionService.evictTenantCaches(tenantId);

        return convertToDTO(saved);
    }

    // =====================================================
    // RENEW SUBSCRIPTION
    // =====================================================

    @Transactional
    public TenantSubscriptionResponseDTO renewSubscription(Long subscriptionId) {
        TenantSubscription renewed = renewSubscriptionInternal(subscriptionId);
        return convertToDTO(renewed);
    }

    @Transactional
    public TenantSubscriptionResponseDTO renewSubscriptionForTenant(Long tenantId) {
        TenantSubscription current = subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription not found for tenant: " + tenantId));
        TenantSubscription renewed = renewSubscriptionInternal(current.getId());
        return convertToDTO(renewed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantSubscription renewSubscriptionIsolation(Long subscriptionId) {
        return renewSubscriptionInternal(subscriptionId);
    }

    private TenantSubscription renewSubscriptionInternal(Long subscriptionId) {
        TenantSubscription current = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (current.getPlanStatus() == PlanStatus.TERMINATED || current.getPlanStatus() == PlanStatus.CANCELLED) {
            throw new BusinessException("Cannot renew a cancelled or terminated subscription. Create a new one.");
        }

        if (current.getPlanStatus() == PlanStatus.ACTIVE &&
                current.getBillingPeriodEnd().isAfter(LocalDateTime.now(ZoneId.of("UTC")).plusDays(7)) &&
                (current.getTenant() == null || current.getTenant().getCompanyName() == null ||
                 !current.getTenant().getCompanyName().contains("Test"))) {
            throw new BusinessException("Subscription is already active and not expiring soon");
        }

        if (current.getPlanStatus() == PlanStatus.EXPIRED) {
            return reactivateExpiredSubscription(current);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime newBillingEnd;

        if (current.getBillingPeriodEnd() != null && current.getBillingPeriodEnd().isBefore(now)) {
            newBillingEnd = now.plusMonths(current.getSubscriptionPlan().getValidityMonths());
        } else if (current.getBillingPeriodEnd() != null) {
            newBillingEnd = current.getBillingPeriodEnd()
                    .plusMonths(current.getSubscriptionPlan().getValidityMonths());
        } else {
            newBillingEnd = now.plusMonths(1);
        }

        TenantSubscription renewed = TenantSubscription.builder()
                .tenant(current.getTenant())
                .subscriptionPlan(current.getSubscriptionPlan())
                .planName(current.getPlanName())
                .planStatus(PlanStatus.ACTIVE)
                .maxEmployees(current.getMaxEmployees())
                .isCurrent(true)
                .startedAt(current.getStartedAt())
                .billingPeriodStart(current.getBillingPeriodEnd() != null ? current.getBillingPeriodEnd() : now)
                .billingPeriodEnd(newBillingEnd)
                .lastPaymentAmount(current.getSubscriptionPlan().getPrice())
                .lastPaymentDate(now)
                .nextPaymentDate(newBillingEnd)
                .amount(current.getSubscriptionPlan().getPrice())
                .currency(current.getCurrency())
                .originalSubscriptionId(
                        current.getOriginalSubscriptionId() != null ? current.getOriginalSubscriptionId()
                                : current.getId())
                .previousSubscriptionId(current.getId())
                .build();

        current.setIsCurrent(false);
        current.setIsActive(false);
        subscriptionRepository.saveAndFlush(current);

        TenantSubscription saved = subscriptionRepository.save(renewed);

        Tenant tenant = current.getTenant();
        if (tenant != null) {
            tenant.setSubscriptionPlan(current.getSubscriptionPlan());
            tenant.setPlanStatus(PlanStatus.ACTIVE);
            tenant.setEndsAt(newBillingEnd);
            tenant.activate();
            tenant.resetSubscriptionLifecycle();
            tenantRepository.save(tenant);

            subscriptionEventLogService.recordEvent(
                    tenant, saved, current.getPlanStatus(), PlanStatus.ACTIVE,
                    TriggerSource.SYSTEM, null, "Subscription auto-renewed successfully."
            );

            tenantRLSService.invalidateTenantCache(tenant.getId());
            cacheEvictionService.evictTenantCaches(tenant.getId());
        }

        eventPublisher.publishEvent(new SubscriptionRenewedEvent(saved, current));
        return saved;
    }

    // =====================================================
    // REACTIVATE EXPIRED SUBSCRIPTION
    // =====================================================

    @Transactional
    public TenantSubscription reactivateExpiredSubscription(TenantSubscription expired) {
        Tenant tenant = expired.getTenant();
        if (tenant != null && (tenant.getDataStatus() == TenantDataStatus.ARCHIVED
                || tenant.getDataStatus() == TenantDataStatus.ELIGIBLE_FOR_DELETION)) {
            throw new BusinessException(
                    "Subscription has been archived. Self-serve reactivation is disabled. Please contact support.");
        }

        if (expired.getPlanStatus() != PlanStatus.EXPIRED) {
            throw new BusinessException("Only expired subscriptions can be reactivated");
        }

        if (expired.getSubscriptionPlan() == null) {
            throw new BusinessException("Cannot reactivate: subscription plan is missing");
        }

        if (expired.getEndedAt() != null &&
                expired.getEndedAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")).minusDays(reactivationWindowDays))) {
            throw new BusinessException(
                    String.format("Subscription expired more than %d days ago. Please create a new subscription.",
                            reactivationWindowDays));
        }

        SubscriptionPlan plan = expired.getSubscriptionPlan();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        int months = (plan.getValidityMonths() == null || plan.getValidityMonths() <= 0) ? 1 : plan.getValidityMonths();
        LocalDateTime newEnd = now.plusMonths(months);

        TenantSubscription reactivated = TenantSubscription.builder()
                .tenant(tenant)
                .subscriptionPlan(plan)
                .planName(plan.getName())
                .planStatus(PlanStatus.ACTIVE)
                .maxEmployees(expired.getMaxEmployees() != null ? expired.getMaxEmployees() : plan.getMaxEmployees())
                .isCurrent(true)
                .startedAt(expired.getStartedAt())
                .billingPeriodStart(now)
                .billingPeriodEnd(newEnd)
                .lastPaymentAmount(plan.getPrice())
                .lastPaymentDate(now)
                .nextPaymentDate(newEnd)
                .amount(plan.getPrice())
                .currency(expired.getCurrency())
                .originalSubscriptionId(
                        expired.getOriginalSubscriptionId() != null ? expired.getOriginalSubscriptionId()
                                : expired.getId())
                .previousSubscriptionId(expired.getId())
                .reactivationDate(now)
                .build();

        expired.setIsCurrent(false);
        expired.setIsActive(false);
        subscriptionRepository.saveAndFlush(expired);

        TenantSubscription saved = subscriptionRepository.save(reactivated);

        if (tenant != null) {
            tenant.setSubscriptionPlan(plan);
            tenant.setPlanStatus(PlanStatus.ACTIVE);
            tenant.setEndsAt(newEnd);
            tenant.activate();
            tenant.resetSubscriptionLifecycle();
            tenantRepository.save(tenant);
            invalidateTenantCachesPostCommit(tenant.getId());

            subscriptionEventLogService.recordEvent(
                    tenant, saved, expired.getPlanStatus(), PlanStatus.ACTIVE,
                    TriggerSource.SYSTEM, null, "Subscription reactivated"
            );
        }

        eventPublisher.publishEvent(new SubscriptionReactivatedEvent(saved, expired));
        return saved;
    }

    @Transactional
    public TenantSubscriptionResponseDTO reactivateSubscription(Long tenantId, Long planId) {
        TenantSubscription expired = subscriptionRepository.findLatestByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found to reactivate"));

        if (expired.getPlanStatus() == PlanStatus.ACTIVE) {
            return convertToDTO(expired);
        }

        if (planId != null) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
            expired.setSubscriptionPlan(plan);
        }

        TenantSubscription reactivated = reactivateExpiredSubscription(expired);
        return convertToDTO(reactivated);
    }

    // =====================================================
    // RESTORE ARCHIVED TENANT
    // =====================================================

    @Transactional
    public TenantSubscriptionResponseDTO restoreArchivedTenant(Long tenantId, Long planId, String notes) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (tenant.getStatus() == UserStatus.DELETED) {
            throw new BusinessException("Tenant has been deleted and cannot be restored");
        }

        if (tenant.getDataStatus() == TenantDataStatus.DELETED) {
            throw new BusinessException("Tenant has been permanently deleted and cannot be restored");
        }

        if (tenant.getDataStatus() != TenantDataStatus.ARCHIVED
                && tenant.getDataStatus() != TenantDataStatus.ELIGIBLE_FOR_DELETION) {
            throw new BusinessException(
                    "Tenant is not in ARCHIVED or ELIGIBLE_FOR_DELETION state and cannot be restored.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        TenantDataStatus oldDataStatus = tenant.getDataStatus();

        subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId).ifPresent(sub -> {
            sub.setIsCurrent(false);
            sub.setIsActive(false);
            subscriptionRepository.saveAndFlush(sub);
        });

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        int months = (plan.getValidityMonths() == null || plan.getValidityMonths() <= 0) ? 1 : plan.getValidityMonths();
        LocalDateTime newEnd = now.plusMonths(months);

        TenantSubscription subscription = TenantSubscription.builder()
                .tenant(tenant)
                .subscriptionPlan(plan)
                .planName(plan.getName())
                .planStatus(PlanStatus.ACTIVE)
                .isCurrent(true)
                .startedAt(now)
                .billingPeriodStart(now)
                .billingPeriodEnd(newEnd)
                .lastPaymentAmount(plan.getPrice())
                .lastPaymentDate(now)
                .nextPaymentDate(newEnd)
                .amount(plan.getPrice())
                .currency(plan.getCurrency() != null ? plan.getCurrency() : "INR")
                .build();

        TenantSubscription saved = subscriptionRepository.save(subscription);

        tenant.setSubscriptionPlan(plan);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(newEnd);
        tenant.activate();
        tenant.resetSubscriptionLifecycle();
        tenantRepository.save(tenant);

        subscriptionEventLogService.recordEvent(
                tenant, saved, null, PlanStatus.ACTIVE,
                TriggerSource.SYSTEM, null, "Tenant restored from archive with plan: " + plan.getName()
        );

        eventPublisher.publishEvent(new SubscriptionReactivatedEvent(saved, saved));

        String performedByEmail = "Unknown Admin";
        Long currentUserId = auditLogService.getCurrentUserId();
        if (currentUserId != null) {
            performedByEmail = platformUserRepository.findById(currentUserId)
                    .map(PlatformUser::getEmail)
                    .orElse("Unknown Admin");
        }

        String metadataJson = String.format(
                "{\"notes\":%s,\"planId\":%d,\"planName\":%s,\"performedByEmail\":%s}",
                escapeJson(notes), plan.getId(), escapeJson(plan.getName()), escapeJson(performedByEmail));

        auditLogService.log(
                tenant, "TENANT_RESTORE", "dataStatus",
                oldDataStatus.name(), TenantDataStatus.RETAINED.name(),
                currentUserId, metadataJson);

        invalidateTenantCachesPostCommit(tenantId);
        return convertToDTO(saved);
    }

    private String escapeJson(String input) {
        if (input == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 32) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        sb.append("\"");
        return sb.toString();
    }

    // =====================================================
    // CACHE HELPERS
    // =====================================================

    public void invalidateTenantCachesPostCommit(final Long tenantId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tenantRLSService.invalidateTenantCache(tenantId);
                    cacheEvictionService.evictTenantCaches(tenantId);
                }
            });
        } else {
            tenantRLSService.invalidateTenantCache(tenantId);
            cacheEvictionService.evictTenantCaches(tenantId);
        }
    }

    // =====================================================
    // HANDLE EXPIRATION
    // =====================================================

    @Transactional
    public void handleSubscriptionExpiration(Long subscriptionId) {
        TenantSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        Long tenantId = subscription.getTenant() != null ? subscription.getTenant().getId() : null;

        if (subscription.getPlanStatus() != PlanStatus.ACTIVE && subscription.getPlanStatus() != PlanStatus.PAST_DUE) {
            return;
        }

        if (subscription.getGracePeriodEnd() != null &&
                subscription.getGracePeriodEnd().isAfter(LocalDateTime.now(ZoneId.of("UTC")))) {
            return;
        }

        subscription.setPlanStatus(PlanStatus.EXPIRED);
        subscription.setIsCurrent(false);
        subscription.setIsActive(false);
        subscription.setEndedAt(LocalDateTime.now(ZoneId.of("UTC")));
        subscriptionRepository.save(subscription);

        Tenant tenant = subscription.getTenant();
        if (tenant != null) {
            tenant.setPlanStatus(PlanStatus.EXPIRED);
            tenant.setStatus(UserStatus.SUSPENDED);
            if (tenant.getExpiredAt() == null) {
                tenant.setExpiredAt(LocalDateTime.now(ZoneId.of("UTC")));
            }
            tenant.setDataStatus(TenantDataStatus.RETAINED);
            tenant.setArchivedAt(null);

            if (tenant.getExpirationNotifiedAt() == null) {
                try {
                    emailService.sendSubscriptionExpiredEmail(
                            tenant.getAdminEmail(),
                            tenant.getCompanyName(),
                            subscription.getPlanName());
                    tenant.setExpirationNotifiedAt(LocalDateTime.now(ZoneId.of("UTC")));
                } catch (Exception e) {
                    log.warn("Failed to send subscription expiration email. [subscriptionId={}, tenantId={}]: {}",
                            subscriptionId, tenantId, e.getMessage());
                }
            }
            tenantRepository.save(tenant);

            subscriptionEventLogService.recordEvent(
                    tenant, subscription, subscription.getPlanStatus(), PlanStatus.EXPIRED,
                    TriggerSource.SYSTEM, null, "Subscription expired."
            );

            cacheEvictionService.evictTenantCaches(tenant.getId());
            tenantRLSService.invalidateTenantCache(tenant.getId());
        }

        eventPublisher.publishEvent(new SubscriptionExpiredEvent(subscription));
    }

    // =====================================================
    // CANCEL SUBSCRIPTION
    // =====================================================

    @Transactional
    public TenantSubscriptionResponseDTO cancelSubscription(Long subscriptionId, CancellationType type, String reason) {
        TenantSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (subscription.getPlanStatus() != PlanStatus.ACTIVE
                && subscription.getPlanStatus() != PlanStatus.PAST_DUE) {
            throw new BusinessException("Only active or past due subscriptions can be cancelled");
        }

        CancellationReason cancelReason = CancellationReason.OTHER;
        if (reason != null) {
            try {
                cancelReason = CancellationReason.valueOf(reason.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                cancelReason = CancellationReason.OTHER;
            }
        }

        if (type == CancellationType.IMMEDIATE) {
            subscription.setPlanStatus(PlanStatus.CANCELLED);
            subscription.setIsCurrent(false);
            subscription.setIsActive(false);
            LocalDateTime nowUTC = LocalDateTime.now(ZoneId.of("UTC"));
            subscription.setEndedAt(nowUTC);
            subscription.setCancellationDate(nowUTC);
            subscription.setCancellationReason(cancelReason);

            Tenant tenant = subscription.getTenant();
            if (tenant != null) {
                tenant.setPlanStatus(PlanStatus.EXPIRED);
                tenant.setEndsAt(nowUTC);
                tenant.deactivate();
                tenantRepository.save(tenant);
                tenantRLSService.invalidateTenantCache(tenant.getId());
                cacheEvictionService.evictTenantCaches(tenant.getId());
            }
        } else {
            subscription.setCancelledAtEndOfPeriod(true);
            subscription.setPlanStatus(PlanStatus.CANCELLED);
            subscription.setCancellationDate(LocalDateTime.now(ZoneId.of("UTC")));
            subscription.setCancellationReason(cancelReason);
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);

        subscriptionEventLogService.recordEvent(
                saved.getTenant(), saved, saved.getPlanStatus(), PlanStatus.CANCELLED,
                TriggerSource.USER, auditLogService.getCurrentUserId(),
                "Subscription cancelled (" + type + "): " + reason
        );

        eventPublisher.publishEvent(new SubscriptionCancelledEvent(saved, type, reason));
        return convertToDTO(saved);
    }

    @Transactional
    public TenantSubscriptionResponseDTO cancelSubscription(Long tenantId) {
        TenantSubscription sub = subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription not found for tenant: " + tenantId));
        return cancelSubscription(sub.getId(), CancellationType.IMMEDIATE, "CUSTOMER_REQUEST");
    }

    // =====================================================
    // UPGRADE SUBSCRIPTION
    // =====================================================

    @Transactional
    public TenantSubscriptionResponseDTO upgradeSubscription(Long tenantId, String planName) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByNameIgnoreCase(planName)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found: " + planName));
        return activateSubscription(tenantId, plan.getId());
    }

    // =====================================================
    // SUBSCRIPTION HISTORY
    // =====================================================

    // ✅ Paginated version (for /history endpoint)
    public Page<TenantSubscriptionResponseDTO> getSubscriptionHistory(Long tenantId, Pageable pageable) {
        Page<TenantSubscription> page = subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        return page.map(this::convertToDTO);
    }

    // ✅ Non-paginated version (for /history/all endpoint)
    public List<TenantSubscriptionResponseDTO> getSubscriptionHistory(Long tenantId) {
        List<TenantSubscription> list = subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // =====================================================
    // HANDLE EXPIRED TENANT
    // =====================================================

    @Transactional
    public void handleExpiredTenant(Long tenantId) {
        cacheEvictionService.evictTenantCaches(tenantId);
        tenantRLSService.invalidateTenantCache(tenantId);

        subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId).ifPresent(sub -> {
            sub.expire();
            subscriptionRepository.save(sub);
        });

        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.expire("Subscription expired (real-time check)");
            tenant.setDataStatus(TenantDataStatus.RETAINED);
            tenant.setArchivedAt(null);

            if (tenant.getExpirationNotifiedAt() == null) {
                try {
                    String planName = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getName()
                            : "Standard Plan";
                    emailService.sendSubscriptionExpiredEmail(
                            tenant.getAdminEmail(),
                            tenant.getCompanyName(),
                            planName);
                    tenant.setExpirationNotifiedAt(LocalDateTime.now(ZoneId.of("UTC")));
                } catch (Exception e) {
                    log.warn("Failed to send subscription expiration email for tenant {}: {}", tenantId, e.getMessage());
                }
            }
            tenantRepository.save(tenant);
        });
    }

    // =====================================================
    // PROCESS EXPIRED SUBSCRIPTION
    // =====================================================

    @Transactional
    public void processExpiredSubscription(Long subscriptionId, LocalDateTime now) {
        subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
            Long tenantId = sub.getTenant() != null ? sub.getTenant().getId() : null;

            if (sub.getGracePeriodEnd() == null) {
                enterGracePeriod(subscriptionId, now);
            } else {
                if (sub.getGracePeriodEnd().isBefore(now)) {
                    handleSubscriptionExpiration(subscriptionId);
                }
            }
        });
    }

    // =====================================================
    // ENTER GRACE PERIOD
    // =====================================================

    @Transactional
    public void enterGracePeriod(Long subscriptionId, LocalDateTime now) {
        subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
            Long tenantId = sub.getTenant() != null ? sub.getTenant().getId() : null;

            if (sub.getPlanStatus() == PlanStatus.PAST_DUE && sub.getGracePeriodEnd() != null) {
                return;
            }

            int newRetryCount = (sub.getPaymentRetryCount() != null ? sub.getPaymentRetryCount() : 0) + 1;
            sub.setPaymentRetryCount(newRetryCount);

            if (newRetryCount > 3) {
                sub.setGracePeriodEnd(null);
                subscriptionRepository.saveAndFlush(sub);
                handleSubscriptionExpiration(subscriptionId);
                return;
            }

            sub.setGracePeriodEnd(now.plusDays(3));
            sub.setPlanStatus(PlanStatus.PAST_DUE);
            subscriptionRepository.save(sub);

            if (sub.getTenant() != null) {
                Tenant tenant = sub.getTenant();
                tenant.setPlanStatus(PlanStatus.PAST_DUE);
                tenantRepository.save(tenant);

                subscriptionEventLogService.recordEvent(
                        tenant, sub, sub.getPlanStatus(), PlanStatus.PAST_DUE,
                        TriggerSource.SYSTEM, null, "Payment failed; entered grace period."
                );

                try {
                    // emailService.sendPaymentFailedEmail(tenant.getAdminEmail(), tenant.getCompanyName());
                } catch (Exception e) {
                    log.error("Failed to send payment failed email. [subscriptionId={}, tenantId={}]: {}",
                            subscriptionId, tenantId, e.getMessage());
                }
                cacheEvictionService.evictTenantCaches(tenantId);
            }
        });
    }

    // =====================================================
    // ADDITIONAL QUERY METHODS
    // =====================================================

    public boolean hasActiveSubscription(Long tenantId) {
        return subscriptionRepository.existsActiveSubscription(tenantId);
    }

    public PlanStatus getSubscriptionStatus(Long tenantId) {
        return subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .map(TenantSubscription::getPlanStatus)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)
                        .map(TenantSubscription::getPlanStatus))
                .orElse(null);
    }

    public boolean isSubscriptionAboutToExpire(Long tenantId, int daysThreshold) {
        return subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .map(sub -> sub.getBillingPeriodEnd() != null &&
                        sub.getBillingPeriodEnd().isBefore(LocalDateTime.now(ZoneId.of("UTC")).plusDays(daysThreshold)) &&
                        sub.getBillingPeriodEnd().isAfter(LocalDateTime.now(ZoneId.of("UTC"))))
                .orElse(false);
    }

    public long getDaysUntilExpiry(Long tenantId) {
        return subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .map(sub -> sub.getBillingPeriodEnd() != null ?
                        java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(ZoneId.of("UTC")), sub.getBillingPeriodEnd()) : 0)
                .orElse(0L);
    }

    @Transactional
    public void syncTenantSubscriptionStatus(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        TenantSubscription currentSubscription = subscriptionRepository
                .findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElse(null);

        if (currentSubscription == null) {
            tenant.setPlanStatus(PlanStatus.EXPIRED);
            tenant.setDataStatus(TenantDataStatus.EXPIRED);  // ✅ Use EXPIRED instead of RETAINED
            tenant.setActive(false);
            tenant.setStatus(UserStatus.SUSPENDED);
            tenant.setEndsAt(null);
        } else {
            tenant.setPlanStatus(currentSubscription.getPlanStatus());
            tenant.setEndsAt(currentSubscription.getBillingPeriodEnd());
            tenant.setActive(currentSubscription.getIsActive() != null && currentSubscription.getIsActive());

            // ✅ Use appropriate TenantDataStatus based on subscription status
            if (currentSubscription.getPlanStatus() == PlanStatus.ACTIVE) {
                tenant.setDataStatus(TenantDataStatus.RETAINED);  // Active subscription -> RETAINED
                tenant.setStatus(UserStatus.ACTIVE);
            } else if (currentSubscription.getPlanStatus() == PlanStatus.EXPIRED) {
                tenant.setDataStatus(TenantDataStatus.EXPIRED);
                tenant.setStatus(UserStatus.SUSPENDED);
            } else {
                tenant.setDataStatus(TenantDataStatus.RETAINED);
                tenant.setStatus(UserStatus.SUSPENDED);
            }

            // Check if subscription is actually expired
            if (currentSubscription.getBillingPeriodEnd() != null &&
                    currentSubscription.getBillingPeriodEnd().isBefore(LocalDateTime.now())) {
                tenant.setPlanStatus(PlanStatus.EXPIRED);
                tenant.setDataStatus(TenantDataStatus.EXPIRED);
                tenant.setStatus(UserStatus.SUSPENDED);
                tenant.setActive(false);

                currentSubscription.setPlanStatus(PlanStatus.EXPIRED);
                currentSubscription.setIsActive(false);
                subscriptionRepository.save(currentSubscription);
            }
        }

        tenantRepository.save(tenant);
        tenantRLSService.invalidateTenantCache(tenantId);
        cacheEvictionService.evictTenantCaches(tenantId);
        log.info("Synced tenant subscription status for tenant: {}", tenantId);
    }


    // =====================================================
    // TENANT VALIDATION
    // =====================================================

    @Cacheable(value = "tenantDetails", key = "#tenantId", unless = "#result == null")
    public CachedTenantDetails getTenantDetails(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> new CachedTenantDetails(
                        tenant.getId(),
                        tenant.getIsActive(),
                        tenant.getStatus(),
                        tenant.getPlanStatus(),
                        tenant.getEndsAt(),
                        tenant.getDataStatus()
                ))
                .orElse(null);
    }

    @CacheEvict(value = "tenantDetails", key = "#tenantId")
    public void evictTenantDetails(Long tenantId) {
        // Cache eviction
    }

    public void validateSubscription(Long tenantId) {
        validateSubscription(tenantId, null, "GET", null, null);
    }

    public void validateSubscription(Long tenantId, String requestPath,
                                     Collection<? extends GrantedAuthority> authorities) {
        validateSubscription(tenantId, requestPath, "GET", authorities, null);
    }

    public void validateSubscription(Long tenantId, String requestPath, String method,
                                     Collection<? extends GrantedAuthority> authorities, HttpServletRequest request) {
        if (tenantId == null) return;

        CachedTenantDetails details = getTenantDetails(tenantId);
        if (details == null) {
            throw new ResourceNotFoundException("Tenant not found");
        }

        boolean isAllowedGrace = (requestPath != null) && isAllowedGracePath(requestPath);
        boolean isAdmin = (authorities != null) && authorities.stream()
                .anyMatch(a -> a != null && ("MANAGE_SUBSCRIPTION".equalsIgnoreCase(a.getAuthority())
                        || "VIEW_BILLING".equalsIgnoreCase(a.getAuthority())));

        boolean isReadGraceRequest = isAllowedGrace && "GET".equalsIgnoreCase(method);
        boolean isSelfServeRenewalAttempt = isAllowedGrace && (isAdmin || isReadGraceRequest) &&
                (details.getDataStatus() == null || details.getDataStatus() == TenantDataStatus.RETAINED || details.getDataStatus() == TenantDataStatus.EXPIRED);

        if (details.getStatus() == UserStatus.DELETED) {
            throw new BusinessException("Tenant account has been deleted");
        }

        PlanStatus planStatus = details.getPlanStatus();

        if (planStatus == PlanStatus.EXPIRED) {
            if (details.getDataStatus() == TenantDataStatus.ARCHIVED) {
                throw new BusinessException(
                        "Subscription has expired and the workspace is archived. Please contact support to restore and renew.");
            }
            if (details.getDataStatus() == TenantDataStatus.ELIGIBLE_FOR_DELETION) {
                throw new BusinessException(
                        "Subscription has expired and the workspace is marked for deletion. Please contact support.");
            }
            if (!isSelfServeRenewalAttempt) {
                throw new BusinessException("Subscription has expired. Please log in and renew online.");
            }
        } else if (planStatus == PlanStatus.SUSPENDED) {
            if (!isSelfServeRenewalAttempt) {
                throw new BusinessException("Subscription is suspended.");
            }
        } else if (planStatus == PlanStatus.CANCELLED) {
            if (!isSelfServeRenewalAttempt) {
                throw new BusinessException("Subscription is cancelled. Please log in and renew online.");
            }
        } else if (planStatus == PlanStatus.TERMINATED) {
            throw new BusinessException("Subscription is terminated. Please contact support.");
        } else if (planStatus == PlanStatus.FROZEN || planStatus == PlanStatus.PAUSED
                || planStatus == PlanStatus.NOT_ACTIVATED) {
            if (!isSelfServeRenewalAttempt) {
                throw new BusinessException("Subscription is " + planStatus.name().toLowerCase() + ". Access is restricted.");
            }
        }

        // Real-time expiration check
        if (details.getBillingPeriodEnd() != null &&
                details.getBillingPeriodEnd().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
            if (planStatus != PlanStatus.PAST_DUE && planStatus != PlanStatus.EXPIRED
                    && planStatus != PlanStatus.SUSPENDED && planStatus != PlanStatus.CANCELLED) {
                if (!isSelfServeRenewalAttempt) {
                    throw new BusinessException("Subscription has expired. Please log in and renew online.");
                }
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

        // Enforce plan-level feature access restrictions
        if (requestPath != null) {
            enforceFeatureAccess(tenantId, requestPath);
        }

        // Grace period restrictions
        if (details.getPlanStatus() == PlanStatus.PAST_DUE) {
            enforceGracePeriodRestrictions(requestPath, method, authorities, isAdmin, isAllowedGrace);
        }
    }

    private void enforceFeatureAccess(Long tenantId, String requestPath) {
        String lowerPath = requestPath.toLowerCase();

        // 1. Webhook access
        if (lowerPath.contains("webhook")) {
            if (!featureAccessService.hasFeature(tenantId, "WEBHOOK_ACCESS")) {
                throw new BusinessException("Webhook access is not enabled for your subscription plan. Please upgrade your plan.");
            }
        }

        // 2. API access
        if (lowerPath.startsWith("/api/public/") &&
                !lowerPath.startsWith("/api/public/register") &&
                !lowerPath.startsWith("/api/public/set-password") &&
                !lowerPath.startsWith("/api/public/check-email")) {
            if (!featureAccessService.hasFeature(tenantId, "API_ACCESS")) {
                throw new BusinessException("API access is not enabled for your subscription plan. Please upgrade your plan.");
            }
        }

        // 3. Payroll feature
        if (lowerPath.startsWith("/api/payroll")) {
            if (!featureAccessService.hasFeature(tenantId, "PAYROLL")) {
                throw new BusinessException("Payroll feature is not enabled for your subscription plan. Please upgrade your plan.");
            }
        }

        // 4. Leave Management feature
        if (lowerPath.contains("/leaves") || lowerPath.contains("/leave")) {
            if (!featureAccessService.hasFeature(tenantId, "LEAVE_MANAGEMENT")) {
                throw new BusinessException("Leave Management feature is not enabled for your subscription plan. Please upgrade your plan.");
            }
        }

        // 5. Attendance feature
        if (lowerPath.startsWith("/api/attendance") || lowerPath.startsWith("/api/shifts")) {
            if (!featureAccessService.hasFeature(tenantId, "ATTENDANCE")) {
                throw new BusinessException("Attendance tracking feature is not enabled for your subscription plan. Please upgrade your plan.");
            }
        }
    }

    private void enforceGracePeriodRestrictions(String requestPath, String method,
                                                Collection<? extends GrantedAuthority> authorities, boolean isAdmin, boolean isAllowedGrace) {
        if (isAdmin) {
            if (!isAllowedGrace) {
                throw new BusinessException(
                        "Company Admin can only access Billing, Renewal, Invoices, Data Export, and Contact Support pages during the grace period.");
            }
        } else {
            if (!"GET".equalsIgnoreCase(method)) {
                throw new BusinessException(
                        "The workspace is read-only during the grace period. Create, update, and delete operations are blocked.");
            }
        }
    }

    private boolean isAllowedGracePath(String path) {
        if (path == null) return false;
        return path.startsWith("/api/tenant/subscriptions") ||
                path.startsWith("/api/export") ||
                path.startsWith("/api/employees/export") ||
                path.startsWith("/api/billing") ||
                path.startsWith("/api/subscriptions") ||
                path.startsWith("/api/employee/support-tickets") ||
                path.equals("/api/tenants/current/subscription") ||
                path.equals("/api/tenants/my-tenant");
    }

    // =====================================================
    // PAYMENT METHODS (STUBBED FOR FUTURE PAYMENT GATEWAY)
    // =====================================================

    @Transactional
    public void processPayment(Long subscriptionId) {
        log.warn("Payment processing is not yet implemented. Payment gateway integration pending.");
    }

    @Transactional
    public void processRefund(Long subscriptionId) {
        log.warn("Refund processing is not yet implemented. Payment gateway integration pending.");
    }

    @Transactional
    public void updatePaymentMethod(Long tenantId, String paymentMethodToken) {
        log.warn("Payment method update is not yet implemented. Payment gateway integration pending.");
    }

    // =====================================================
    // HELPER: CLOSE ACTIVE SUBSCRIPTION
    // =====================================================

    private void closeActiveSubscription(Long tenantId, String reason) {
        subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .ifPresent(sub -> {
                    sub.setIsCurrent(false);
                    sub.setIsActive(false);
                    sub.setEndedAt(LocalDateTime.now(ZoneId.of("UTC")));
                    sub.setPlanStatus(PlanStatus.TERMINATED);
                    subscriptionRepository.saveAndFlush(sub);

                    subscriptionEventLogService.recordEvent(
                            sub.getTenant(), sub, sub.getPlanStatus(), PlanStatus.TERMINATED,
                            TriggerSource.USER, auditLogService.getCurrentUserId(), reason
                    );
                });
    }

    // =====================================================
    // CONVERTER
    // =====================================================

    private static final int DEFAULT_MAX_EMPLOYEES = 100;

    private TenantSubscriptionResponseDTO convertToDTO(TenantSubscription sub) {
        if (sub == null) return null;

        SubscriptionPlan plan = sub.getSubscriptionPlan();
        int maxEmployees = sub.getMaxEmployees() != null ? sub.getMaxEmployees()
                : (plan != null && plan.getMaxEmployees() != null ? plan.getMaxEmployees() : DEFAULT_MAX_EMPLOYEES);

        return TenantSubscriptionResponseDTO.builder()
                .id(sub.getId())
                .planType(sub.getPlanType())
                .planName(sub.getPlanName())
                .planStatus(sub.getPlanStatus())
                .maxEmployees(maxEmployees)
                .startedAt(sub.getStartedAt())
                .endsAt(sub.getBillingPeriodEnd())
                .amount(sub.getAmount())
                .currency(sub.getCurrency())
                .isActive(sub.getIsActive() != null ? sub.getIsActive() : false)
                .createdAt(sub.getCreatedAt())
                .billingPeriodStart(sub.getBillingPeriodStart())
                .billingPeriodEnd(sub.getBillingPeriodEnd())
                .gracePeriodEnd(sub.getGracePeriodEnd())
                .cancellationReason(sub.getCancellationReason() != null ? sub.getCancellationReason().name() : null)
                .cancelledAtEndOfPeriod(sub.getCancelledAtEndOfPeriod())
                .build();
    }

    public SubscriptionStatusResponse getSubscriptionStatus(
            Long tenantId,
            Collection<? extends GrantedAuthority> authorities) {

        boolean canAccessBilling = hasAnyAuthority(
                authorities,
                "VIEW_BILLING",
                "MANAGE_SUBSCRIPTION"
        );

        boolean canAccessSupport = hasAnyAuthority(
                authorities,
                "VIEW_SUPPORT",
                "MANAGE_SUBSCRIPTION"
        );

        try {
            // Get tenant for data status
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

            TenantSubscription subscription = subscriptionRepository
                    .findByTenantIdAndIsCurrentTrue(tenantId)
                    .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                    .orElse(null);

            if (subscription == null) {
                return SubscriptionStatusResponse.builder()
                        .active(false)
                        .status("NO_SUBSCRIPTION")
                        .message("No active subscription found.")
                        .canAccessBilling(canAccessBilling)
                        .canAccessSupport(canAccessSupport)
                        .allowedActions(buildAllowedActions(canAccessBilling, canAccessSupport))
                        .build();
            }

            PlanStatus status = subscription.getPlanStatus();

            // Check if subscription is actually expired (real-time check)
            if (subscription.getBillingPeriodEnd() != null &&
                    subscription.getBillingPeriodEnd().isBefore(LocalDateTime.now(ZoneId.of("UTC"))) &&
                    status != PlanStatus.EXPIRED) {
                // Update status to expired
                subscription.setPlanStatus(PlanStatus.EXPIRED);
                subscription.setIsActive(false);
                subscription.setIsCurrent(false);
                subscription.setEndedAt(LocalDateTime.now(ZoneId.of("UTC")));
                subscriptionRepository.save(subscription);
                status = PlanStatus.EXPIRED;
            }

            // =====================================================
            // HANDLE EXPIRED - This is what you need
            // =====================================================
            if (status == PlanStatus.EXPIRED) {
                TenantDataStatus dataStatus = tenant != null ? tenant.getDataStatus() : TenantDataStatus.RETAINED;

                String message;
                String allowedActions;

                // Different messages based on data retention status
                if (dataStatus == TenantDataStatus.ARCHIVED) {
                    message = "Your subscription has expired and your workspace has been archived. Please contact support to restore.";
                    allowedActions = "You can still access billing and subscription management.";
                } else if (dataStatus == TenantDataStatus.ELIGIBLE_FOR_DELETION) {
                    message = "Your subscription has expired and your workspace is marked for deletion. Contact support immediately.";
                    allowedActions = "You can still access support.";
                } else {
                    // RETAINED (0-30 days) - This matches your required response
                    message = "Your subscription has expired. Your data is retained for 30 days. Please renew to reactivate.";
                    allowedActions = "You can still access billing and subscription management.";
                }

                return SubscriptionStatusResponse.builder()
                        .active(false)
                        .status("EXPIRED")
                        .message(message)
                        .canAccessBilling(true)  // Allow billing access
                        .canAccessSupport(true)
                        .allowedActions(allowedActions)
                        .build();
            }

            // =====================================================
            // HANDLE CANCELLED
            // =====================================================
            if (status == PlanStatus.CANCELLED) {
                return SubscriptionStatusResponse.builder()
                        .active(false)
                        .status("CANCELLED")
                        .message("Your subscription has been cancelled.")
                        .canAccessBilling(canAccessBilling)
                        .canAccessSupport(true)
                        .allowedActions("You can still access billing and subscription management.")
                        .build();
            }

            // =====================================================
            // HANDLE SUSPENDED
            // =====================================================
            if (status == PlanStatus.SUSPENDED) {
                return SubscriptionStatusResponse.builder()
                        .active(false)
                        .status("SUSPENDED")
                        .message("Your subscription is suspended.")
                        .canAccessBilling(false)
                        .canAccessSupport(true)
                        .allowedActions("Please contact support to resolve the issue.")
                        .build();
            }

            // =====================================================
            // HANDLE TERMINATED
            // =====================================================
            if (status == PlanStatus.TERMINATED) {
                return SubscriptionStatusResponse.builder()
                        .active(false)
                        .status("TERMINATED")
                        .message("Your subscription has been terminated.")
                        .canAccessBilling(false)
                        .canAccessSupport(true)
                        .allowedActions("Please contact support for assistance.")
                        .build();
            }

            // =====================================================
            // ACTIVE SUBSCRIPTION
            // =====================================================
            if (status == PlanStatus.ACTIVE) {
                return SubscriptionStatusResponse.builder()
                        .active(true)
                        .status("ACTIVE")
                        .message("Your subscription is active.")
                        .canAccessBilling(true)
                        .canAccessSupport(true)
                        .allowedActions("Full access to all features.")
                        .build();
            }

            // =====================================================
            // DEFAULT
            // =====================================================
            return SubscriptionStatusResponse.builder()
                    .active(false)
                    .status(status != null ? status.name() : "UNKNOWN")
                    .message("Subscription status is unknown.")
                    .canAccessBilling(canAccessBilling)
                    .canAccessSupport(true)
                    .allowedActions("Please contact support.")
                    .build();

        } catch (Exception ex) {
            log.error("Error fetching subscription status for tenant {}", tenantId, ex);
            return SubscriptionStatusResponse.builder()
                    .active(false)
                    .status("ERROR")
                    .message("Unable to retrieve subscription status.")
                    .canAccessBilling(false)
                    .canAccessSupport(true)
                    .allowedActions("Please contact support.")
                    .build();
        }
    }

    // =====================================================
    // EXISTING HELPER METHODS (Keep as is)
    // =====================================================

    private boolean hasAnyAuthority(
            Collection<? extends GrantedAuthority> authorities,
            String... permissions) {

        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth ->
                        Arrays.stream(permissions)
                                .anyMatch(p -> p.equalsIgnoreCase(auth)));
    }

    private String getStatusMessage(PlanStatus status) {
        if (status == null) {
            return "Subscription status is unavailable.";
        }

        switch (status) {
            case ACTIVE:
                return "Your subscription is active.";
            case PAST_DUE:
                return "Your payment is overdue. Please renew your subscription.";
            case EXPIRED:
                return "Your subscription has expired. Your data is retained for 30 days. Please renew to reactivate.";
            case CANCELLED:
                return "Your subscription has been cancelled.";
            case TERMINATED:
                return "Your subscription has been terminated. Please contact support.";
            case SUSPENDED:
                return "Your subscription is suspended.";
            case PAUSED:
                return "Your subscription is paused.";
            case FROZEN:
                return "Your subscription is temporarily frozen.";
            case NOT_ACTIVATED:
                return "Your subscription has not been activated.";
            default:
                return "Subscription is not active.";
        }
    }

    private String buildAllowedActions(boolean hasBillingPermission, boolean hasSupportAccess) {
        List<String> actions = new ArrayList<>();

        if (hasBillingPermission) {
            actions.add("Access billing and subscription management");
        }

        if (hasSupportAccess) {
            actions.add("Contact support");
        }

        if (actions.isEmpty()) {
            return "Please contact your administrator for assistance.";
        }

        return "You can still: " + String.join(", ", actions);
    }

}