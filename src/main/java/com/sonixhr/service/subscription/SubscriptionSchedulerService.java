package com.sonixhr.service.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.platform.PlatformTenantService;
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



    /**
     * Runs every day at 1:00 AM to process all subscription lifecycle stages.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void runSubscriptionLifecycle() {
        log.info("Subscription lifecycle process started");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        // Steps 1 & 2: Send Expiration Reminders & Upcoming Renewal Notifications
        try {
            sendExpirationRemindersAndRenewals(now);
        } catch (Exception e) {
            log.error("Fatal error in sendExpirationRemindersAndRenewals step: {}", e.getMessage(), e);
        }

        // Step 3: Auto-renewal attempts
        try {
            renewSubscriptions(now);
        } catch (Exception e) {
            log.error("Fatal error in renewSubscriptions step: {}", e.getMessage(), e);
        }

        // Step 4: Immediate demotion to EXPIRED for active subscriptions (No Grace Period)
        try {
            demoteExpiredActiveSubscriptions(now);
        } catch (Exception e) {
            log.error("Fatal error in demoteExpiredActiveSubscriptions step: {}", e.getMessage(), e);
        }

        // Step 5: Expire Cancelled subscriptions
        try {
            expireCancelledSubscriptions(now);
        } catch (Exception e) {
            log.error("Fatal error in expireCancelledSubscriptions step: {}", e.getMessage(), e);
        }

        // Step 6: Archival transitions
        try {
            archiveExpiredTenants(now);
        } catch (Exception e) {
            log.error("Fatal error in archiveExpiredTenants step: {}", e.getMessage(), e);
        }







        log.info("Subscription lifecycle process completed");
    }

    /**
     * Steps 1 & 2: Sends subscription expiration reminders and upcoming auto-renewal warnings.
     */
    @Transactional
    public void sendExpirationRemindersAndRenewals(LocalDateTime now) {
        log.info("Steps 1 & 2: Sending Expiration Reminders and Upcoming Renewal Notifications");
        
        // 7 days before expiry (only if autoRenew = false)
        LocalDateTime start7 = now.plusDays(7).toLocalDate().atStartOfDay();
        LocalDateTime end7 = now.plusDays(7).toLocalDate().atTime(23, 59, 59);
        java.util.List<TenantSubscription> expiringIn7 = subscriptionRepository.findActiveSubscriptionsExpiringBetween(start7, end7);
        for (TenantSubscription sub : expiringIn7) {
            if (sub.getTenant() != null && (sub.getAutoRenew() == null || !sub.getAutoRenew())) {
                emailService.sendSubscriptionExpirationReminderEmail(
                        sub.getTenant().getAdminEmail(),
                        sub.getTenant().getCompanyName(),
                        sub.getSubscriptionPlan() != null ? sub.getSubscriptionPlan().getName() : "Standard Plan",
                        7
                );
            }
        }

        // 3 days before expiry (either renewal warning or expiration warning)
        LocalDateTime start3 = now.plusDays(3).toLocalDate().atStartOfDay();
        LocalDateTime end3 = now.plusDays(3).toLocalDate().atTime(23, 59, 59);
        java.util.List<TenantSubscription> expiringIn3 = subscriptionRepository.findActiveSubscriptionsExpiringBetween(start3, end3);
        for (TenantSubscription sub : expiringIn3) {
            if (sub.getTenant() != null) {
                String planName = sub.getSubscriptionPlan() != null ? sub.getSubscriptionPlan().getName() : "Standard Plan";
                if (sub.getAutoRenew() != null && sub.getAutoRenew()) {
                    emailService.sendUpcomingRenewalEmail(
                            sub.getTenant().getAdminEmail(),
                            sub.getTenant().getCompanyName(),
                            planName,
                            sub.getBillingPeriodEnd().toLocalDate().toString()
                    );
                } else {
                    emailService.sendSubscriptionExpirationReminderEmail(
                            sub.getTenant().getAdminEmail(),
                            sub.getTenant().getCompanyName(),
                            planName,
                            3
                    );
                }
            }
        }

        // 1 day before expiry (only if autoRenew = false)
        LocalDateTime start1 = now.plusDays(1).toLocalDate().atStartOfDay();
        LocalDateTime end1 = now.plusDays(1).toLocalDate().atTime(23, 59, 59);
        java.util.List<TenantSubscription> expiringIn1 = subscriptionRepository.findActiveSubscriptionsExpiringBetween(start1, end1);
        for (TenantSubscription sub : expiringIn1) {
            if (sub.getTenant() != null && (sub.getAutoRenew() == null || !sub.getAutoRenew())) {
                emailService.sendSubscriptionExpirationReminderEmail(
                        sub.getTenant().getAdminEmail(),
                        sub.getTenant().getCompanyName(),
                        sub.getSubscriptionPlan() != null ? sub.getSubscriptionPlan().getName() : "Standard Plan",
                        1
                );
            }
        }
    }

    /**
     * Attempts to auto-renew active subscriptions before they expire.
     */
    @Transactional
    public void renewSubscriptions(LocalDateTime now) {
        log.info("Step 3: Attempting auto-renewals");
        LocalDateTime oneDayFromNow = now.plusDays(1);
        Set<Long> processedIds = new HashSet<>();
        Page<TenantSubscription> autoRenewPage;
        int loopCount = 0;

        do {
            autoRenewPage = subscriptionRepository.findAutoRenewSubscriptionsBefore(
                    PlanStatus.ACTIVE, oneDayFromNow, PageRequest.of(0, batchSize)
            );

            if (autoRenewPage == null || autoRenewPage.isEmpty()) {
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
                    // Skip free plans
                    if (sub.getSubscriptionPlan() != null &&
                            sub.getSubscriptionPlan().getPrice() != null &&
                            sub.getSubscriptionPlan().getPrice().compareTo(java.math.BigDecimal.ZERO) == 0) {
                        log.info("Skipping renewal check for free subscription ID: {}", sub.getId());
                        continue;
                    }

                    subscriptionService.renewSubscriptionIsolation(sub.getId());
                    log.info("Auto-renewed subscription ID: {}", sub.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-renew subscription ID: {}: {}. Expiring immediately.",
                            sub.getId(), e.getMessage());
                    
                    // Payment failed: Send payment failed email & expire subscription immediately
                    try {
                        if (sub.getTenant() != null) {
                            emailService.sendPaymentFailedEmail(
                                    sub.getTenant().getAdminEmail(),
                                    sub.getTenant().getCompanyName(),
                                    sub.getSubscriptionPlan() != null ? sub.getSubscriptionPlan().getName() : "Standard Plan"
                            );
                        }
                    } catch (Exception mailEx) {
                        log.error("Failed to send payment failed email", mailEx);
                    }
                    try {
                        subscriptionService.handleSubscriptionExpiration(sub.getId());
                    } catch (Exception expEx) {
                        log.error("Failed to expire subscription ID after payment failure: {}", sub.getId(), expEx);
                    }
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
     * Handles active subscriptions that have passed their end date.
     */
    @Transactional
    public void demoteExpiredActiveSubscriptions(LocalDateTime now) {
        log.info("Step 4: Expiring expired ACTIVE subscriptions immediately (No Grace Period)");
        Set<Long> processedIds = new HashSet<>();
        Page<TenantSubscription> expiredPage;
        int loopCount = 0;

        do {
            expiredPage = subscriptionRepository.findExpiredSubscriptionsBefore(
                    PlanStatus.ACTIVE, now, PageRequest.of(0, batchSize)
            );

            if (expiredPage == null || expiredPage.isEmpty()) {
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
                    log.info("Active subscription expired. Expiring immediately. [subscriptionId={}, tenantId={}]",
                            sub.getId(), sub.getTenant() != null ? sub.getTenant().getId() : null);
                    subscriptionService.handleSubscriptionExpiration(sub.getId());
                } catch (Exception e) {
                    log.error("Error expiring active subscription ID {}: {}", sub.getId(), e.getMessage());
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
     * Expire CANCELLED subscriptions once their paid period ends.
     */
    @Transactional
    public void expireCancelledSubscriptions(LocalDateTime now) {
        log.info("Step 4: Expiring cancelled subscriptions");
        Set<Long> processedIds = new HashSet<>();
        Page<TenantSubscription> expiredCancelledPage;
        int loopCount = 0;

        do {
            expiredCancelledPage = subscriptionRepository.findExpiredSubscriptionsBefore(
                    PlanStatus.CANCELLED, now, PageRequest.of(0, batchSize)
            );

            if (expiredCancelledPage == null || expiredCancelledPage.isEmpty()) {
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
                    log.info("Cancelled subscription period ended. Changing status to EXPIRED. [subscriptionId={}]",
                            sub.getId());
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
     * Sends emails to tenants approaching the archival threshold.
     */
    @Transactional
    public void sendArchivalWarnings(LocalDateTime now) {
        log.info("Step 5: Sending archival warning notifications");
        LocalDateTime thresholdDate = now.minusDays(archivalWarningDays);
        int pageIndex = 0;
        Page<Tenant> tenantsPage;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForArchiveWarning(
                    TenantDataStatus.RETAINED, thresholdDate, PageRequest.of(pageIndex, batchSize)
            );

            if (tenantsPage == null || tenantsPage.isEmpty()) {
                break;
            }

            for (Tenant tenant : tenantsPage.getContent()) {
                try {
                    String planName = tenant.getSubscriptionPlan() != null ?
                            tenant.getSubscriptionPlan().getName() : "Standard Plan";

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
                    log.error("Failed to send archive warning email for tenant ID {}: {}",
                            tenant.getId(), e.getMessage());
                }
            }
            pageIndex++;
        } while (tenantsPage.hasNext());
    }

    /**
     * Transition expired tenants from RETAINED to ELIGIBLE_FOR_DELETION (soft-deleted) status.
     */
    @Transactional
    public void archiveExpiredTenants(LocalDateTime now) {
        log.info("Step 6: Processing soft-delete transitions");
        LocalDateTime thresholdDate = now.minusDays(archivalDays);
        Set<Long> processedIds = new HashSet<>();
        Page<Tenant> tenantsPage;
        int loopCount = 0;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForArchive(
                    TenantDataStatus.RETAINED, thresholdDate, PageRequest.of(0, batchSize)
            );

            if (tenantsPage == null || tenantsPage.isEmpty()) {
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
                    tenant.setArchivedAt(now);
                    Tenant saved = tenantRepository.save(tenant);

                    // ✅ CORRECT: Pass PlanStatus enums
                    eventLogService.recordEvent(
                            saved,
                            null,
                            PlanStatus.EXPIRED,          // ✅ PlanStatus enum
                            PlanStatus.EXPIRED,          // ✅ PlanStatus enum
                            com.sonixhr.enums.TriggerSource.SYSTEM,
                            null,
                            "Tenant data soft-deleted (ELIGIBLE_FOR_DELETION) after 30 days of subscription expiration."
                    );

                    try {
                        emailService.sendArchiveNotificationEmail(
                                tenant.getAdminEmail(),
                                tenant.getCompanyName(),
                                tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getName() : "Standard Plan"
                        );
                    } catch (Exception mailEx) {
                        log.error("Failed to send soft-delete notification email for tenant ID {}", tenant.getId(), mailEx);
                    }

                    subscriptionService.invalidateTenantCachesPostCommit(tenant.getId());
                    log.info("Tenant ID {} transitioned to ELIGIBLE_FOR_DELETION (soft-deleted)", tenant.getId());
                } catch (Exception e) {
                    log.error("Failed to soft-delete tenant ID {}: {}", tenant.getId(), e.getMessage());
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


    @Transactional
    public void purgeEligibleTenants(LocalDateTime now) {
        log.info("Step 8: Hard purging eligible tenants (respecting legalHold)");
        Set<Long> processedIds = new HashSet<>();
        Page<Tenant> tenantsPage;
        int loopCount = 0;

        do {
            tenantsPage = tenantRepository.findTenantsEligibleForPurge(
                    TenantDataStatus.ELIGIBLE_FOR_DELETION, now, PageRequest.of(0, batchSize)
            );

            if (tenantsPage == null || tenantsPage.isEmpty()) {
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

                    // ✅ CORRECT: Pass PlanStatus enums
                    eventLogService.recordEvent(
                            tenant,
                            null,
                            PlanStatus.EXPIRED,          // ✅ PlanStatus enum - previous status
                            PlanStatus.EXPIRED,          // ✅ PlanStatus enum - current status
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

}