package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantSubscriptionResponseDTO;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.CancellationType;
import com.sonixhr.enums.CancellationReason;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.security.TenantRLSService;
import com.sonixhr.service.common.CacheEvictionService;
import com.sonixhr.service.common.AuditLogService;
import com.sonixhr.service.EmailService;
import com.sonixhr.event.subscription.*;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.enums.TriggerSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @Value("${app.subscription.reactivation-window-days:30}")
    private int reactivationWindowDays;

    public TenantSubscriptionResponseDTO currentSubscription(Long tenantId) {
        log.info("Fetching current active subscription for tenant ID: {}", tenantId);
        TenantSubscription subscription = subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Current subscription not found for tenant"));
        return convertToDTO(subscription);
    }

    // ===== CREATE NEW SUBSCRIPTION =====
    @Transactional
    public TenantSubscriptionResponseDTO activateSubscription(Long tenantId, Long planId) {
        log.info("Activating subscription for tenant ID: {} and plan ID: {}", tenantId, planId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        // Close any existing active subscriptions
        closeActiveSubscription(tenantId, "Upgraded to new plan");

        // Create new subscription
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        int validityMonths = plan.getValidityMonths() != null && plan.getValidityMonths() > 0 ? plan.getValidityMonths()
                : 1;

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

        // Record audit event
        subscriptionEventLogService.recordEvent(
                tenant,
                saved,
                null,
                PlanStatus.ACTIVE.name(),
                TriggerSource.USER,
                auditLogService.getCurrentUserId(),
                "Subscription upgraded to plan: " + plan.getName()
        );

        // Update tenant
        tenant.setSubscriptionPlan(plan);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(now.plusMonths(validityMonths));
        tenant.setStatus(UserStatus.ACTIVE);
        tenantRepository.save(tenant);

        // Publish event
        eventPublisher.publishEvent(new SubscriptionActivatedEvent(saved));

        tenantRLSService.invalidateTenantCache(tenantId);
        cacheEvictionService.evictTenantCaches(tenantId);

        return convertToDTO(saved);
    }

    // ===== RENEW SUBSCRIPTION =====
    @Transactional
    public TenantSubscriptionResponseDTO renewSubscription(Long subscriptionId) {
        log.info("Renewing subscription ID: {}", subscriptionId);
        TenantSubscription renewed = renewSubscriptionInternal(subscriptionId);
        return convertToDTO(renewed);
    }

    @Transactional
    public TenantSubscriptionResponseDTO renewSubscriptionForTenant(Long tenantId) {
        log.info("Renewing subscription for tenant ID: {}", tenantId);
        TenantSubscription current = subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Active subscription not found for tenant: " + tenantId));
        TenantSubscription renewed = renewSubscriptionInternal(current.getId());
        return convertToDTO(renewed);
    }

    /**
     * Isolated renewal transaction mapping, ensuring failures do not rollback
     * scheduler batches.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantSubscription renewSubscriptionIsolation(Long subscriptionId) {
        log.info("Renewing subscription ID: {} with propagation REQUIRES_NEW", subscriptionId);
        return renewSubscriptionInternal(subscriptionId);
    }

    private TenantSubscription renewSubscriptionInternal(Long subscriptionId) {
        TenantSubscription current = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        // Validate subscription can be renewed
        if (current.getStatus() == PlanStatus.TERMINATED || current.getStatus() == PlanStatus.CANCELLED) {
            throw new BusinessException("Cannot renew a cancelled or terminated subscription. Create a new one.");
        }

        // Check if it's already active and not near expiry
        if (current.getStatus() == PlanStatus.ACTIVE &&
                current.getBillingPeriodEnd().isAfter(LocalDateTime.now(ZoneId.of("UTC")).plusDays(7))) {
            throw new BusinessException("Subscription is already active and not expiring soon");
        }

        // For expired subscriptions, reactivate
        if (current.getStatus() == PlanStatus.EXPIRED) {
            return reactivateExpiredSubscription(current);
        }

        // Normal renewal
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime newBillingEnd;

        // Determine if we should extend from end date or from now
        if (current.getBillingPeriodEnd() != null && current.getBillingPeriodEnd().isBefore(now)) {
            // Expired - start from now
            newBillingEnd = now.plusMonths(current.getSubscriptionPlan().getValidityMonths());
        } else if (current.getBillingPeriodEnd() != null) {
            // Still active - extend from end
            newBillingEnd = current.getBillingPeriodEnd()
                    .plusMonths(current.getSubscriptionPlan().getValidityMonths());
        } else {
            newBillingEnd = now.plusMonths(1);
        }

        // Create renewal record (new subscription)
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

        // Mark old as not current
        current.setIsCurrent(false);
        current.setIsActive(false);
        subscriptionRepository.saveAndFlush(current);

        TenantSubscription saved = subscriptionRepository.save(renewed);

        // Update tenant
        Tenant tenant = current.getTenant();
        if (tenant != null) {
            tenant.setSubscriptionPlan(current.getSubscriptionPlan());
            tenant.setPlanStatus(PlanStatus.ACTIVE);
            tenant.setEndsAt(newBillingEnd);
            tenant.setStatus(UserStatus.ACTIVE);
            tenantRepository.save(tenant);
        }

        // Record audit event
        if (tenant != null) {
            subscriptionEventLogService.recordEvent(
                    tenant,
                    saved,
                    current.getStatus().name(),
                    PlanStatus.ACTIVE.name(),
                    com.sonixhr.enums.TriggerSource.SYSTEM,
                    null,
                    "Subscription auto-renewed successfully."
            );
        }

        // Publish event
        eventPublisher.publishEvent(new SubscriptionRenewedEvent(saved, current));

        if (tenant != null) {
            tenantRLSService.invalidateTenantCache(tenant.getId());
            cacheEvictionService.evictTenantCaches(tenant.getId());
        }

        return saved;
    }

    // ===== REACTIVATE EXPIRED SUBSCRIPTION =====
    @Transactional
    public TenantSubscription reactivateExpiredSubscription(TenantSubscription expired) {
        Tenant tenant = expired.getTenant();
        if (tenant != null && (tenant.getDataStatus() == TenantDataStatus.ARCHIVED
                || tenant.getDataStatus() == TenantDataStatus.ELIGIBLE_FOR_DELETION)) {
            throw new BusinessException(
                    "Subscription has been archived. Self-serve reactivation is disabled. Please contact support.");
        }

        if (expired.getStatus() != PlanStatus.EXPIRED) {
            throw new BusinessException("Only expired subscriptions can be reactivated");
        }

        if (expired.getSubscriptionPlan() == null) {
            throw new BusinessException("Cannot reactivate: subscription plan is missing");
        }

        // Check if we should allow reactivation (configurable reactivation window)
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

        // Create reactivated subscription
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

        // Mark old as not current and save and flush first to prevent constraint
        // violations
        expired.setIsCurrent(false);
        expired.setIsActive(false);
        subscriptionRepository.saveAndFlush(expired);

        TenantSubscription saved = subscriptionRepository.save(reactivated);

        // Update tenant
        if (tenant != null) {
            tenant.setSubscriptionPlan(plan);
            tenant.setPlanStatus(PlanStatus.ACTIVE);
            tenant.setEndsAt(newEnd);
            tenant.resetSubscriptionLifecycle();
            tenantRepository.save(tenant);
        }

        // Publish event
        eventPublisher.publishEvent(new SubscriptionReactivatedEvent(saved, expired));

        if (tenant != null) {
            invalidateTenantCachesPostCommit(tenant.getId());
        }

        return saved;
    }

    @Transactional
    public TenantSubscriptionResponseDTO restoreArchivedTenant(Long tenantId, Long planId, String notes) {
        log.info("Restoring archived tenant ID: {} with plan ID: {}", tenantId, planId);
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

        // Deactivate/clear old current subscription to avoid index constraint violation
        subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId).ifPresent(sub -> {
            sub.setIsCurrent(false);
            sub.setIsActive(false);
            subscriptionRepository.saveAndFlush(sub);
        });

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        int months = (plan.getValidityMonths() == null || plan.getValidityMonths() <= 0) ? 1 : plan.getValidityMonths();
        LocalDateTime newEnd = now.plusMonths(months);

        // Create new active subscription
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

        // Update tenant
        tenant.setSubscriptionPlan(plan);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(newEnd);
        tenant.resetSubscriptionLifecycle();
        tenantRepository.save(tenant);

        // Publish event
        eventPublisher.publishEvent(new SubscriptionReactivatedEvent(saved, saved));

        // Audit Trail
        String performedByEmail = "Unknown Admin";
        Long currentUserId = auditLogService.getCurrentUserId();
        if (currentUserId != null) {
            performedByEmail = platformUserRepository.findById(currentUserId)
                    .map(PlatformUser::getEmail)
                    .orElse("Unknown Admin");
        }

        String metadataJson = String.format(
                "{\"notes\":%s,\"planId\":%d,\"planName\":%s,\"performedByEmail\":%s}",
                escapeJson(notes),
                plan.getId(),
                escapeJson(plan.getName()),
                escapeJson(performedByEmail));

        auditLogService.log(
                tenant,
                "TENANT_RESTORE",
                "dataStatus",
                oldDataStatus.name(),
                TenantDataStatus.RETAINED.name(),
                currentUserId,
                metadataJson);

        invalidateTenantCachesPostCommit(tenantId);

        return convertToDTO(saved);
    }

    private String escapeJson(String input) {
        if (input == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"')
                sb.append("\\\"");
            else if (c == '\\')
                sb.append("\\\\");
            else if (c == '\n')
                sb.append("\\n");
            else if (c == '\r')
                sb.append("\\r");
            else if (c == '\t')
                sb.append("\\t");
            else if (c < 32)
                sb.append(String.format("\\u%04x", (int) c));
            else
                sb.append(c);
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Helper to perform cache invalidations safely after database commit.
     */
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

    // ===== HANDLE EXPIRATION =====
    @Transactional
    public void handleSubscriptionExpiration(Long subscriptionId) {
        TenantSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        Long tenantId = subscription.getTenant() != null ? subscription.getTenant().getId() : null;
        log.info("Handling expiration for subscription. [subscriptionId={}, tenantId={}, status={}]",
                subscriptionId, tenantId, subscription.getStatus());

        if (subscription.getStatus() != PlanStatus.ACTIVE && subscription.getStatus() != PlanStatus.PAST_DUE) {
            log.info("Subscription expiration already processed or not eligible. [subscriptionId={}, status={}]",
                    subscriptionId, subscription.getStatus());
            return; // Already handled
        }

        // Check if we're in grace period
        if (subscription.getGracePeriodEnd() != null &&
                subscription.getGracePeriodEnd().isAfter(LocalDateTime.now(ZoneId.of("UTC")))) {
            log.info("Subscription is in grace period until {}. [subscriptionId={}, tenantId={}]",
                    subscription.getGracePeriodEnd(), subscriptionId, tenantId);
            return;
        }

        String oldStatus = subscription.getStatus() != null ? subscription.getStatus().name() : null;

        // Expire the subscription
        subscription.setStatus(PlanStatus.EXPIRED);
        subscription.setIsCurrent(false);
        subscription.setIsActive(false);
        subscription.setEndedAt(LocalDateTime.now(ZoneId.of("UTC")));
        subscriptionRepository.save(subscription);

        // Update tenant
        Tenant tenant = subscription.getTenant();
        if (tenant != null) {
            tenant.setPlanStatus(PlanStatus.EXPIRED);
            tenant.setStatus(UserStatus.SUSPENDED);
            if (tenant.getExpiredAt() == null) {
                tenant.setExpiredAt(LocalDateTime.now(ZoneId.of("UTC")));
            }
            tenant.setDataStatus(TenantDataStatus.RETAINED);
            tenant.setArchivedAt(null);

            // Send expiration email
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

            // Record audit event
            subscriptionEventLogService.recordEvent(
                    tenant,
                    subscription,
                    oldStatus,
                    PlanStatus.EXPIRED.name(),
                    com.sonixhr.enums.TriggerSource.SYSTEM,
                    null,
                    "Subscription expired."
            );

            // Invalidate caches
            cacheEvictionService.evictTenantCaches(tenant.getId());
            tenantRLSService.invalidateTenantCache(tenant.getId());
        }

        // Publish event
        eventPublisher.publishEvent(new SubscriptionExpiredEvent(subscription));
        log.info("Subscription successfully expired. [subscriptionId={}, tenantId={}]", subscriptionId, tenantId);
    }

    @Transactional
    public TenantSubscriptionResponseDTO cancelSubscription(Long subscriptionId, CancellationType type, String reason) {
        log.info("Cancelling subscription ID: {} with type: {} and reason: {}", subscriptionId, type, reason);
        TenantSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (subscription.getStatus() != PlanStatus.ACTIVE
                && subscription.getStatus() != PlanStatus.TRIAL
                && subscription.getStatus() != PlanStatus.PAST_DUE) {
            throw new BusinessException("Only active, trial, or past due subscriptions can be cancelled");
        }

        CancellationReason cancelReason = CancellationReason.OTHER;
        if (reason != null) {
            try {
                cancelReason = CancellationReason.valueOf(reason.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                cancelReason = CancellationReason.OTHER;
            }
        }

        String oldStatus = subscription.getStatus() != null ? subscription.getStatus().name() : null;

        if (type == CancellationType.IMMEDIATE) {
            // Immediate cancellation
            subscription.setStatus(PlanStatus.CANCELLED);
            subscription.setIsCurrent(false);
            subscription.setIsActive(false);
            LocalDateTime nowUTC = LocalDateTime.now(ZoneId.of("UTC"));
            subscription.setEndedAt(nowUTC);
            subscription.setCancellationDate(nowUTC);
            subscription.setCancellationReason(cancelReason);

            Tenant tenant = subscription.getTenant();
            if (tenant != null) {
                tenant.setPlanStatus(PlanStatus.EXPIRED); // Immediate cancellation sets Tenant to EXPIRED directly
                tenant.setEndsAt(nowUTC);
                tenant.deactivate();
                tenantRepository.save(tenant);

                tenantRLSService.invalidateTenantCache(tenant.getId());
                cacheEvictionService.evictTenantCaches(tenant.getId());
            }
        } else {
            // Cancel at end of period (downgrade/stop renewal)
            subscription.setCancelledAtEndOfPeriod(true);
            subscription.setStatus(PlanStatus.CANCELLED);
            subscription.setCancellationDate(LocalDateTime.now(ZoneId.of("UTC")));
            subscription.setCancellationReason(cancelReason);
        }

        TenantSubscription saved = subscriptionRepository.save(subscription);

        // Record audit event
        subscriptionEventLogService.recordEvent(
                saved.getTenant(),
                saved,
                oldStatus,
                PlanStatus.CANCELLED.name(),
                TriggerSource.USER,
                auditLogService.getCurrentUserId(),
                "Subscription cancelled (" + type + "): " + reason
        );

        // Publish event
        eventPublisher.publishEvent(new SubscriptionCancelledEvent(saved, type, reason));

        return convertToDTO(saved);
    }

    // ✅✅✅ FIXED: Upgrade by PLAN CODE (not name!) ✅✅✅
    @Transactional
    public TenantSubscriptionResponseDTO upgradeSubscription(Long tenantId, String planCode) {
        log.info("Upgrading tenant ID: {} to plan code: {}", tenantId, planCode);

        // ✅ FIX: Search by CODE, not NAME
        SubscriptionPlan plan = subscriptionPlanRepository.findByCodeIgnoreCase(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found with code: " + planCode));

        return activateSubscription(tenantId, plan.getId());
    }

    @Transactional
    public TenantSubscriptionResponseDTO cancelSubscription(Long tenantId) {
        log.info("Cancelling current active subscription for tenant ID: {}", tenantId);
        TenantSubscription sub = subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .or(() -> subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Active subscription not found for tenant: " + tenantId));
        return cancelSubscription(sub.getId(), CancellationType.IMMEDIATE, "CUSTOMER_REQUEST");
    }

    public List<TenantSubscriptionResponseDTO> getSubscriptionHistory(Long tenantId) {
        log.info("Fetching subscription history for tenant ID: {}", tenantId);
        List<TenantSubscription> list = subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // ===== HANDLE EXPIRED TENANT =====
    @Transactional
    public void handleExpiredTenant(Long tenantId) {
        log.info("Subscription for tenant {} is detected as expired in real-time. Deactivating...", tenantId);

        // 1. Evict caches first to prevent race conditions
        cacheEvictionService.evictTenantCaches(tenantId);
        tenantRLSService.invalidateTenantCache(tenantId);

        // 2. Update TenantSubscription
        subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId).ifPresent(sub -> {
            sub.expire(); // sets planStatus = PlanStatus.EXPIRED and isActive = false
            subscriptionRepository.save(sub);
        });

        // 3. Update Tenant and Send Email (idempotent)
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.expire("Subscription expired (real-time check)");
            tenant.setDataStatus(TenantDataStatus.RETAINED);
            tenant.setArchivedAt(null);

            // Send expiration email atomically/idempotently
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
                    log.warn("Failed to send subscription expiration email for tenant {}: {}", tenantId,
                            e.getMessage());
                }
            }
            tenantRepository.save(tenant);
        });
    }

    @Transactional
    public void processExpiredSubscription(Long subscriptionId, LocalDateTime now) {
        subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
            Long tenantId = sub.getTenant() != null ? sub.getTenant().getId() : null;
            log.info(
                    "Processing expired subscription check. [subscriptionId={}, tenantId={}, status={}, gracePeriodEnd={}]",
                    subscriptionId, tenantId, sub.getStatus(), sub.getGracePeriodEnd());

            if (sub.getGracePeriodEnd() == null) {
                // First time detecting expiration - delegate to enterGracePeriod
                enterGracePeriod(subscriptionId, now);
            } else {
                // Already has a grace period set
                if (sub.getGracePeriodEnd().isBefore(now)) {
                    log.info("Grace period ended. Expiring subscription. [subscriptionId={}, tenantId={}]",
                            subscriptionId, tenantId);
                    handleSubscriptionExpiration(subscriptionId);
                } else {
                    log.info("Grace period is still active. [subscriptionId={}, tenantId={}, gracePeriodEnd={}]",
                            subscriptionId, tenantId, sub.getGracePeriodEnd());
                }
            }
        });
    }

    @Transactional
    public void enterGracePeriod(Long subscriptionId, LocalDateTime now) {
        subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
            Long tenantId = sub.getTenant() != null ? sub.getTenant().getId() : null;
            log.info("Entering grace period check. [subscriptionId={}, tenantId={}, status={}, gracePeriodEnd={}]",
                    subscriptionId, tenantId, sub.getStatus(), sub.getGracePeriodEnd());

            // If it is already in PAST_DUE and grace period is active, do not overwrite or
            // resend email
            if (sub.getStatus() == PlanStatus.PAST_DUE && sub.getGracePeriodEnd() != null) {
                log.info("Subscription already in grace period. [subscriptionId={}, tenantId={}, gracePeriodEnd={}]",
                        subscriptionId, tenantId, sub.getGracePeriodEnd());
                return;
            }

            // Increment retry count
            int newRetryCount = (sub.getPaymentRetryCount() != null ? sub.getPaymentRetryCount() : 0) + 1;
            sub.setPaymentRetryCount(newRetryCount);

            if (newRetryCount > 3) {
                log.warn(
                        "Payment retry count exceeded threshold (3). Expiring subscription immediately. [subscriptionId={}, tenantId={}]",
                        subscriptionId, tenantId);
                // Reset/clear grace period to avoid confusion
                sub.setGracePeriodEnd(null);
                subscriptionRepository.saveAndFlush(sub);
                handleSubscriptionExpiration(subscriptionId);
                return;
            }

            String oldStatus = sub.getStatus() != null ? sub.getStatus().name() : null;

            // Enter grace period
            sub.setGracePeriodEnd(now.plusDays(3));
            sub.setStatus(PlanStatus.PAST_DUE);
            subscriptionRepository.save(sub);

            if (sub.getTenant() != null) {
                Tenant tenant = sub.getTenant();
                tenant.setPlanStatus(PlanStatus.PAST_DUE);
                tenantRepository.save(tenant);

                subscriptionEventLogService.recordEvent(
                        tenant,
                        sub,
                        oldStatus,
                        PlanStatus.PAST_DUE.name(),
                        com.sonixhr.enums.TriggerSource.SYSTEM,
                        null,
                        "Payment failed; entered grace period."
                );

                try {
                    emailService.sendPaymentFailedEmail(
                            tenant.getAdminEmail(),
                            tenant.getCompanyName());
                } catch (Exception e) {
                    log.error("Failed to send payment failed email. [subscriptionId={}, tenantId={}]: {}",
                            subscriptionId, tenantId, e.getMessage());
                }
                cacheEvictionService.evictTenantCaches(tenantId);
            }
            log.info(
                    "Successfully transitioned subscription to grace period. [subscriptionId={}, tenantId={}, retryCount={}]",
                    subscriptionId, tenantId, newRetryCount);
        });
    }

    // ===== HELPER: Close Active Subscription =====
    private void closeActiveSubscription(Long tenantId, String reason) {
        subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)
                .ifPresent(sub -> {
                    String oldStatus = sub.getStatus() != null ? sub.getStatus().name() : null;
                    sub.setIsCurrent(false);
                    sub.setIsActive(false);
                    sub.setEndedAt(LocalDateTime.now(ZoneId.of("UTC")));
                    sub.setStatus(PlanStatus.TERMINATED);
                    subscriptionRepository.saveAndFlush(sub);

                    subscriptionEventLogService.recordEvent(
                            sub.getTenant(),
                            sub,
                            oldStatus,
                            PlanStatus.TERMINATED.name(),
                            TriggerSource.USER,
                            auditLogService.getCurrentUserId(),
                            reason
                    );
                });
    }

    private static final int DEFAULT_MAX_EMPLOYEES = 100;

    private TenantSubscriptionResponseDTO convertToDTO(TenantSubscription sub) {
        if (sub == null)
            return null;

        SubscriptionPlan plan = sub.getSubscriptionPlan();
        int maxEmployees = sub.getMaxEmployees() != null ? sub.getMaxEmployees()
                : (plan != null && plan.getMaxEmployees() != null ? plan.getMaxEmployees() : DEFAULT_MAX_EMPLOYEES);

        return TenantSubscriptionResponseDTO.builder()
                .id(sub.getId())
                .planType(sub.getPlanType())
                .planName(sub.getPlanName())
                .planStatus(sub.getStatus())
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
}