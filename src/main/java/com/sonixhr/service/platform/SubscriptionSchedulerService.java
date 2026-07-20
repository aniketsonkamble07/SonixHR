package com.sonixhr.service.platform;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.tenant.TenantSubscriptionService;
import com.sonixhr.service.tenant.SubscriptionEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSchedulerService {

    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantSubscriptionService subscriptionService;
    private final EmailService emailService;
    private final TenantRepository tenantRepository;
    private final PlatformTenantService platformTenantService;
    private final SubscriptionEventLogService eventLogService;

    @Value("${app.subscription.batch-size:100}")
    private int batchSize;

    @Value("${app.subscription.archival-warning-days:23}")
    private int archivalWarningDays;

    @Value("${app.subscription.archival-days:30}")
    private int archivalDays;

    @Value("${app.subscription.soft-delete-days:120}")
    private int softDeleteDays;

    @Value("${app.subscription.final-reminder-months:11}")
    private int finalReminderMonths;

    /**
     * Runs every day at 1:00 AM to process all subscription lifecycle stages in a fixed, ordered pass.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void runSubscriptionLifecycle() {
        log.info("Subscription lifecycle process started");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        // Step 1: Auto-renewal attempts
        try {
            renewSubscriptions(now);
        } catch (Exception e) {
            log.error("Fatal error in renewSubscriptions step: {}", e.getMessage(), e);
        }

        // Step 2: Grace demotion for expired active subscriptions (trials expire immediately)
        try {
            demoteExpiredActiveSubscriptions(now);
        } catch (Exception e) {
            log.error("Fatal error in demoteExpiredActiveSubscriptions step: {}", e.getMessage(), e);
        }

        // Step 3: Grace expiry for PAST_DUE subscriptions
        try {
            expireGracePeriodSubscriptions(now);
        } catch (Exception e) {
            log.error("Fatal error in expireGracePeriodSubscriptions step: {}", e.getMessage(), e);
        }

        // Step 5: Archival warning notifications
        try {
            sendArchivalWarnings(now);
        } catch (Exception e) {
            log.error("Fatal error in sendArchivalWarnings step: {}", e.getMessage(), e);
        }

        // Step 6: Archival transitions
        try {
            archiveExpiredTenants(now);
        } catch (Exception e) {
            log.error("Fatal error in archiveExpiredTenants step: {}", e.getMessage(), e);
        }

        // Step 7: Soft-delete transitions
        try {
            softDeleteArchivedTenants(now);
        } catch (Exception e) {
            log.error("Fatal error in softDeleteArchivedTenants step: {}", e.getMessage(), e);
        }

        // Step 7.5: Final data reminders
        try {
            sendFinalDataReminders(now);
        } catch (Exception e) {
            log.error("Fatal error in sendFinalDataReminders step: {}", e.getMessage(), e);
        }

        // Step 8: Hard deletion/purging (respecting legalHold)
        try {
            purgeEligibleTenants();
        } catch (Exception e) {
            log.error("Fatal error in purgeEligibleTenants step: {}", e.getMessage(), e);
        }

        log.info("Subscription lifecycle process completed");
    }

    /**
     * Attempts to auto-renew active subscriptions before they expire.
     */
    public void renewSubscriptions(LocalDateTime now) {
        log.info("Step 1: Attempting auto-renewals");
        LocalDateTime oneDayFromNow = now.plusDays(1);
        Set<Long> processedIds = new HashSet<>();
        Page<TenantSubscription> autoRenewPage;
        int loopCount = 0;

        do {
            autoRenewPage = subscriptionRepository.findAutoRenewSubscriptionsBefore(
                PlanStatus.ACTIVE, oneDayFromNow, PageRequest.of(0, batchSize)
            );

            if (autoRenewPage.isEmpty()) {
                break;
            }

            boolean processedAny = false;
            for (TenantSubscription sub : autoRenewPage.getContent()) {
                if (processedIds.contains(sub.getId())) {
                    continue;
                }
                processedIds.add(sub.getId());
                processedAny = true;

                try {
                    // Trial check: Zero-price plans have nothing to renew and should bypass charge attempts
                    if (sub.getSubscriptionPlan() != null && sub.getSubscriptionPlan().getPrice() != null &&
                        sub.getSubscriptionPlan().getPrice().compareTo(java.math.BigDecimal.ZERO) == 0) {
                        log.info("Skipping renewal check for trial/free subscription ID: {}", sub.getId());
                        continue;
                    }

                    subscriptionService.renewSubscriptionIsolation(sub.getId());
                    log.info("Auto-renewed subscription ID: {}", sub.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-renew subscription ID: {}: {}. Entering grace period.", sub.getId(), e.getMessage());
                    enterGracePeriodSafe(sub.getId(), now);
                }
            }

            if (!processedAny) {
                break;
            }
            loopCount++;
            if (loopCount > 100) {
                log.warn("Renewal safety loop limit reached");
                break;
            }
        } while (autoRenewPage.hasNext());
    }

    /**
     * Handles active subscriptions that have passed their ends_at date.
     * Trials (price = 0) are expired immediately, others enter grace period (PAST_DUE).
     */
    public void demoteExpiredActiveSubscriptions(LocalDateTime now) {
        log.info("Step 2: Processing grace demotions for expired ACTIVE subscriptions");
        Set<Long> processedIds = new HashSet<>();
        Page<TenantSubscription> expiredPage;
        int loopCount = 0;

        do {
            expiredPage = subscriptionRepository.findExpiredSubscriptionsBefore(
                PlanStatus.ACTIVE, now, PageRequest.of(0, batchSize)
            );

            if (expiredPage.isEmpty()) {
                break;
            }

            boolean processedAny = false;
            for (TenantSubscription sub : expiredPage.getContent()) {
                if (processedIds.contains(sub.getId())) {
                    continue;
                }
                processedIds.add(sub.getId());
                processedAny = true;

                try {
                    boolean isTrial = sub.getSubscriptionPlan() != null && sub.getSubscriptionPlan().getPrice() != null &&
                        sub.getSubscriptionPlan().getPrice().compareTo(java.math.BigDecimal.ZERO) == 0;
                    if (isTrial) {
                        log.info("Trial plan expired. Expiring immediately. [subscriptionId={}, tenantId={}]", sub.getId(), sub.getTenant().getId());
                        subscriptionService.handleSubscriptionExpiration(sub.getId());
                    } else {
                        log.info("Subscription expired. Entering grace period. [subscriptionId={}, tenantId={}]", sub.getId(), sub.getTenant().getId());
                        subscriptionService.processExpiredSubscription(sub.getId(), now);
                    }
                } catch (Exception e) {
                    log.error("Error processing expired ACTIVE subscription ID {}: {}", sub.getId(), e.getMessage());
                }
            }

            if (!processedAny) {
                break;
            }
            loopCount++;
            if (loopCount > 100) {
                log.warn("Demotion safety loop limit reached");
                break;
            }
        } while (expiredPage.hasNext());
    }

    /**
     * Expire PAST_DUE subscriptions whose grace period has ended.
     */
    public void expireGracePeriodSubscriptions(LocalDateTime now) {
        log.info("Step 3: Expiring grace-period subscriptions");
        Set<Long> processedIds = new HashSet<>();
        Page<TenantSubscription> graceExpiredPage;
        int loopCount = 0;

        do {
            graceExpiredPage = subscriptionRepository.findGracePeriodExpiredSubscriptions(
                PlanStatus.PAST_DUE, now, PageRequest.of(0, batchSize)
            );

            if (graceExpiredPage.isEmpty()) {
                break;
            }

            boolean processedAny = false;
            for (TenantSubscription sub : graceExpiredPage.getContent()) {
                if (processedIds.contains(sub.getId())) {
                    continue;
                }
                processedIds.add(sub.getId());
                processedAny = true;

                try {
                    log.info("Grace period expired. Changing status to EXPIRED. [subscriptionId={}]", sub.getId());
                    subscriptionService.handleSubscriptionExpiration(sub.getId());
                } catch (Exception e) {
                    log.error("Failed to expire grace-period subscription ID {}: {}", sub.getId(), e.getMessage());
                }
            }

            if (!processedAny) {
                break;
            }
            loopCount++;
            if (loopCount > 100) {
                log.warn("Grace expiry safety loop limit reached");
                break;
            }
        } while (graceExpiredPage.hasNext());
    }

    /**
     * Expire CANCELLED subscriptions once their paid period ends.
     */
    public void expireCancelledSubscriptions(LocalDateTime now) {
        log.info("Step 4: Expiring cancelled subscriptions");
        Set<Long> processedIds = new HashSet<>();
        Page<TenantSubscription> expiredCancelledPage;
        int loopCount = 0;

        do {
            expiredCancelledPage = subscriptionRepository.findExpiredSubscriptionsBefore(
                PlanStatus.CANCELLED, now, PageRequest.of(0, batchSize)
            );

            if (expiredCancelledPage.isEmpty()) {
                break;
            }

            boolean processedAny = false;
            for (TenantSubscription sub : expiredCancelledPage.getContent()) {
                if (processedIds.contains(sub.getId())) {
                    continue;
                }
                processedIds.add(sub.getId());
                processedAny = true;

                try {
                    log.info("Cancelled subscription period ended. Changing status to EXPIRED. [subscriptionId={}]", sub.getId());
                    subscriptionService.handleSubscriptionExpiration(sub.getId());
                } catch (Exception e) {
                    log.error("Failed to expire cancelled subscription ID {}: {}", sub.getId(), e.getMessage());
                }
            }

            if (!processedAny) {
                break;
            }
            loopCount++;
            if (loopCount > 100) {
                log.warn("Cancellation expiry safety loop limit reached");
                break;
            }
        } while (expiredCancelledPage.hasNext());
    }

    /**
     * Sends emails to tenants approaching the 30-day archival threshold.
     */
    public void sendArchivalWarnings(LocalDateTime now) {
        log.info("Step 5: Sending archival warning notifications");
        LocalDateTime thresholdDate = now.minusDays(archivalWarningDays);
        int pageIndex = 0;
        Page<Tenant> tenantsPage;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForArchiveWarning(
                TenantDataStatus.RETAINED, thresholdDate, PageRequest.of(pageIndex, batchSize)
            );

            for (Tenant tenant : tenantsPage.getContent()) {
                try {
                    String planName = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getName() : "Standard Plan";
                    emailService.sendArchiveWarningEmail(
                        tenant.getAdminEmail(),
                        tenant.getCompanyName(),
                        planName,
                        7
                    );
                    tenant.setArchiveWarningNotifiedAt(now);
                    tenantRepository.save(tenant);
                    log.info("Sent archive warning email to tenant: {}", tenant.getCompanyName());
                } catch (Exception e) {
                    log.error("Failed to send archive warning email for tenant ID {}: {}", tenant.getId(), e.getMessage());
                }
            }
            pageIndex++;
        } while (tenantsPage.hasNext());
    }

    /**
     * Transition expired tenants from RETAINED to ARCHIVED status after 30 days of expiration.
     */
    public void archiveExpiredTenants(LocalDateTime now) {
        log.info("Step 6: Processing archival transitions");
        LocalDateTime thresholdDate = now.minusDays(archivalDays);
        Set<Long> processedIds = new HashSet<>();
        Page<Tenant> tenantsPage;
        int loopCount = 0;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForArchive(
                TenantDataStatus.RETAINED, thresholdDate, PageRequest.of(0, batchSize)
            );

            if (tenantsPage.isEmpty()) {
                break;
            }

            boolean processedAny = false;
            for (Tenant tenant : tenantsPage.getContent()) {
                if (processedIds.contains(tenant.getId())) {
                    continue;
                }
                processedIds.add(tenant.getId());
                processedAny = true;

                try {
                    tenant.setDataStatus(TenantDataStatus.ARCHIVED);
                    tenant.setArchivedAt(now);
                    Tenant saved = tenantRepository.save(tenant);

                    eventLogService.recordEvent(
                        saved,
                        null,
                        "EXPIRED",
                        "EXPIRED_ARCHIVED",
                        com.sonixhr.enums.TriggerSource.SYSTEM,
                        null,
                        "Tenant data archived after 30 days of subscription expiration."
                    );

                    subscriptionService.invalidateTenantCachesPostCommit(tenant.getId());
                    log.info("Tenant ID {} transitioned to ARCHIVED", tenant.getId());
                } catch (Exception e) {
                    log.error("Failed to archive tenant ID {}: {}", tenant.getId(), e.getMessage());
                }
            }

            if (!processedAny) {
                break;
            }
            loopCount++;
            if (loopCount > 100) {
                log.warn("Archival safety loop limit reached");
                break;
            }
        } while (tenantsPage.hasNext());
    }

    /**
     * Transition tenants from ARCHIVED to ELIGIBLE_FOR_DELETION after 120 days total post-expiration (90 days in ARCHIVED status).
     */
    public void softDeleteArchivedTenants(LocalDateTime now) {
        log.info("Step 7: Processing soft-delete transitions");
        LocalDateTime thresholdDate = now.minusDays(softDeleteDays);
        Set<Long> processedIds = new HashSet<>();
        Page<Tenant> tenantsPage;
        int loopCount = 0;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForSoftDelete(
                TenantDataStatus.ARCHIVED, thresholdDate, PageRequest.of(0, batchSize)
            );

            if (tenantsPage.isEmpty()) {
                break;
            }

            boolean processedAny = false;
            for (Tenant tenant : tenantsPage.getContent()) {
                if (processedIds.contains(tenant.getId())) {
                    continue;
                }
                processedIds.add(tenant.getId());
                processedAny = true;

                try {
                    tenant.setDataStatus(TenantDataStatus.ELIGIBLE_FOR_DELETION);
                    tenant.setStatus(com.sonixhr.enums.UserStatus.DELETED);
                    tenant.setDeletedAt(now);
                    Tenant saved = tenantRepository.save(tenant);

                    eventLogService.recordEvent(
                        saved,
                        null,
                        "EXPIRED_ARCHIVED",
                        "ELIGIBLE_FOR_DELETION",
                        com.sonixhr.enums.TriggerSource.SYSTEM,
                        null,
                        "Tenant transitioned to Soft Delete (ELIGIBLE_FOR_DELETION) after 120 days total post-expiration."
                    );

                    subscriptionService.invalidateTenantCachesPostCommit(tenant.getId());
                    log.info("Tenant ID {} transitioned to Soft Delete (ELIGIBLE_FOR_DELETION)", tenant.getId());
                } catch (Exception e) {
                    log.error("Failed to transition tenant ID {} to soft delete: {}", tenant.getId(), e.getMessage());
                }
            }

            if (!processedAny) {
                break;
            }
            loopCount++;
            if (loopCount > 100) {
                log.warn("Soft-delete safety loop limit reached");
                break;
            }
        } while (tenantsPage.hasNext());
    }

    /**
     * Purges (hard deletes) tenants in ELIGIBLE_FOR_DELETION state, respecting legalHold.
     */
    public void purgeEligibleTenants() {
        log.info("Step 8: Hard purging eligible tenants (respecting legalHold)");
        Set<Long> processedIds = new HashSet<>();
        Page<Tenant> tenantsPage;
        int loopCount = 0;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForPurge(
                TenantDataStatus.ELIGIBLE_FOR_DELETION, PageRequest.of(0, batchSize)
            );

            if (tenantsPage.isEmpty()) {
                break;
            }

            boolean processedAny = false;
            for (Tenant tenant : tenantsPage.getContent()) {
                if (processedIds.contains(tenant.getId())) {
                    continue;
                }
                processedIds.add(tenant.getId());
                processedAny = true;

                try {
                    if (tenant.isLegalHold()) {
                        log.info("Skipping purge for tenant ID {} due to active legalHold", tenant.getId());
                        continue;
                    }

                    log.info("Purging tenant ID {} from platform", tenant.getId());
                    
                    // Log compliance deletion trace event before deletion (audit log persists separately)
                    eventLogService.recordEvent(
                        tenant,
                        null,
                        "ELIGIBLE_FOR_DELETION",
                        "PURGED",
                        com.sonixhr.enums.TriggerSource.SYSTEM,
                        null,
                        "Tenant permanently purged from the platform."
                    );

                    platformTenantService.deleteTenant(tenant.getId());
                    log.info("Tenant ID {} hard-purged successfully", tenant.getId());
                } catch (Exception e) {
                    log.error("Failed to purge tenant ID {}: {}", tenant.getId(), e.getMessage());
                }
            }

            if (!processedAny) {
                break;
            }
            loopCount++;
            if (loopCount > 100) {
                log.warn("Purge safety loop limit reached");
                break;
            }
        } while (tenantsPage.hasNext());
    }

    private void enterGracePeriodSafe(Long subscriptionId, LocalDateTime now) {
        try {
            subscriptionService.enterGracePeriod(subscriptionId, now);
        } catch (Exception ex) {
            log.error("Failed to transition subscription ID: {} to grace period: {}", subscriptionId, ex.getMessage());
        }
    }

    /**
     * Sends final data reminder emails to archived tenants 11 months after expiration.
     */
    public void sendFinalDataReminders(LocalDateTime now) {
        log.info("Sending final data reminders");
        LocalDateTime thresholdDate = now.minusMonths(finalReminderMonths);
        int pageIndex = 0;
        Page<Tenant> tenantsPage;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForFinalReminder(
                TenantDataStatus.ARCHIVED, thresholdDate, PageRequest.of(pageIndex, batchSize)
            );

            for (Tenant tenant : tenantsPage.getContent()) {
                try {
                    String planName = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getName() : "Standard Plan";
                    emailService.sendFinalDataReminderEmail(
                        tenant.getAdminEmail(),
                        tenant.getCompanyName(),
                        planName,
                        true
                    );
                    tenant.setFinalReminderSentAt(now);
                    tenantRepository.save(tenant);
                    log.info("Sent final data reminder email to tenant: {}", tenant.getCompanyName());
                } catch (Exception e) {
                    log.error("Failed to send final reminder email for tenant ID {}: {}", tenant.getId(), e.getMessage());
                }
            }
            pageIndex++;
        } while (tenantsPage.hasNext());
    }

    // =====================================================
    // TEST WRAPPER METHODS (FOR BACKWARD COMPATIBILITY)
    // =====================================================

    public void checkArchiveWarnings() {
        sendArchivalWarnings(LocalDateTime.now(ZoneId.of("UTC")));
    }

    public void processTenantArchivals() {
        archiveExpiredTenants(LocalDateTime.now(ZoneId.of("UTC")));
    }

    public void checkFinalDataReminders() {
        sendFinalDataReminders(LocalDateTime.now(ZoneId.of("UTC")));
    }

    public void processTenantSoftDeleteTransition() {
        softDeleteArchivedTenants(LocalDateTime.now(ZoneId.of("UTC")));
    }
}
