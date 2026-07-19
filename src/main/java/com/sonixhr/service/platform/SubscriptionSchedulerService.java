package com.sonixhr.service.platform;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.tenant.TenantSubscriptionService;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSchedulerService {

    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantSubscriptionService subscriptionService;
    private final EmailService emailService;
    private final TenantRepository tenantRepository;
    private final PlatformTenantService platformTenantService;

    @Value("${app.subscription.batch-size:100}")
    private int batchSize;

    /**
     * Runs every day at 1:00 AM to check for subscriptions expiring soon and send reminders,
     * manage grace periods, and handle automatic renewals.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkExpiringSubscriptions() {
        log.info("Subscription expiry check scheduler triggered");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime sevenDaysFromNow = now.plusDays(7);
        LocalDateTime oneDayFromNow = now.plusDays(1);

        // 1. Send reminders for subscriptions expiring soon (in batches)
        int pageIndex = 0;
        Page<TenantSubscription> expiringSoonPage;
        do {
            expiringSoonPage = subscriptionRepository.findExpiringSubscriptionsBetween(
                PlanStatus.ACTIVE, now, sevenDaysFromNow, PageRequest.of(pageIndex, batchSize)
            );
            
            for (TenantSubscription sub : expiringSoonPage.getContent()) {
                if (sub.getTenant() != null) {
                    long daysUntilExpiry = ChronoUnit.DAYS.between(now, sub.getBillingPeriodEnd());
                    if (daysUntilExpiry <= 7 && daysUntilExpiry > 0) {
                        try {
                            emailService.sendSubscriptionReminderEmail(
                                sub.getTenant().getAdminEmail(),
                                sub.getTenant().getCompanyName(),
                                sub.getPlanName(),
                                (int) daysUntilExpiry
                            );
                        } catch (Exception e) {
                            log.error("Failed to send subscription reminder email for sub ID: {}: {}", sub.getId(), e.getMessage());
                        }
                    }
                }
            }
            pageIndex++;
        } while (expiringSoonPage.hasNext());

        // 2. Handle expired active subscriptions (enter grace period)
        Page<TenantSubscription> expiredPage;
        do {
            // Keep fetching page 0 because processed items change status from ACTIVE to PAST_DUE or EXPIRED and disappear from active results
            expiredPage = subscriptionRepository.findExpiredSubscriptionsBefore(
                PlanStatus.ACTIVE, now, PageRequest.of(0, batchSize)
            );
            
            if (expiredPage.isEmpty()) {
                break;
            }
            
            for (TenantSubscription sub : expiredPage.getContent()) {
                try {
                    subscriptionService.processExpiredSubscription(sub.getId(), now);
                } catch (Exception e) {
                    log.error("Error processing expired subscription ID: {}: {}", sub.getId(), e.getMessage());
                }
            }
        } while (expiredPage.hasNext());

        // 2b. Handle expired cancelled subscriptions (expire immediately without grace period)
        Page<TenantSubscription> expiredCancelledPage;
        do {
            expiredCancelledPage = subscriptionRepository.findExpiredSubscriptionsBefore(
                PlanStatus.CANCELLED, now, PageRequest.of(0, batchSize)
            );
            
            if (expiredCancelledPage.isEmpty()) {
                break;
            }
            
            for (TenantSubscription sub : expiredCancelledPage.getContent()) {
                try {
                    subscriptionService.handleSubscriptionExpiration(sub.getId());
                } catch (Exception e) {
                    log.error("Error processing expired cancelled subscription ID: {}: {}", sub.getId(), e.getMessage());
                }
            }
        } while (expiredCancelledPage.hasNext());

        // 3. Handle PAST_DUE subscriptions whose grace period has ended
        Page<TenantSubscription> graceExpiredPage;
        do {
            // Keep fetching page 0 because processed items change status from PAST_DUE to EXPIRED and disappear from results
            graceExpiredPage = subscriptionRepository.findGracePeriodExpiredSubscriptions(
                PlanStatus.PAST_DUE, now, PageRequest.of(0, batchSize)
            );
            
            if (graceExpiredPage.isEmpty()) {
                break;
            }
            
            for (TenantSubscription sub : graceExpiredPage.getContent()) {
                try {
                    subscriptionService.handleSubscriptionExpiration(sub.getId());
                } catch (Exception e) {
                    log.error("Failed to expire grace-period subscription ID: {}: {}", sub.getId(), e.getMessage());
                }
            }
        } while (graceExpiredPage.hasNext());

        // 4. Auto-renew subscriptions where possible
        Page<TenantSubscription> autoRenewPage;
        do {
            // Keep fetching page 0 because processed items will either renew (endsAt pushes out of target window) or fail and become PAST_DUE
            autoRenewPage = subscriptionRepository.findAutoRenewSubscriptionsBefore(
                PlanStatus.ACTIVE, oneDayFromNow, PageRequest.of(0, batchSize)
            );
            
            if (autoRenewPage.isEmpty()) {
                break;
            }
            
            for (TenantSubscription sub : autoRenewPage.getContent()) {
                try {
                    // Try to renew with isolated propagation (propagation = Propagation.REQUIRES_NEW)
                    subscriptionService.renewSubscriptionIsolation(sub.getId());
                    log.info("Auto-renewed subscription ID: {}", sub.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-renew subscription ID: {}: {}. Entering grace period.", sub.getId(), e.getMessage());
                    enterGracePeriodSafe(sub.getId(), now);
                }
            }
        } while (autoRenewPage.hasNext());
    }



    /**
     * Runs daily at 1:30 AM to check for tenants approaching their 30-day archival threshold
     * (notified at 23 days post-expiration in RETAINED status).
     */
    @Scheduled(cron = "0 30 1 * * ?")
    @Transactional
    public void checkArchiveWarnings() {
        log.info("Subscription archive warnings job triggered");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime thresholdDate = now.minusDays(23);

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
                        7 // 7 days until archiving
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
     * Runs daily at 2:00 AM to archive expired tenant workspaces that have exceeded
     * the 30-day grace/reactivation self-serve window (transition RETAINED to ARCHIVED).
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void processTenantArchivals() {
        log.info("Tenant archivals transition job triggered");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime thresholdDate = now.minusDays(30);

        Page<Tenant> tenantsPage;
        do {
            // Keep fetching page 0 because transitioned tenants leave the RETAINED state
            tenantsPage = tenantRepository.findTenantsEligibleForArchive(
                TenantDataStatus.RETAINED, thresholdDate, PageRequest.of(0, batchSize)
            );

            if (tenantsPage.isEmpty()) {
                break;
            }

            for (Tenant tenant : tenantsPage.getContent()) {
                try {
                    tenant.setDataStatus(TenantDataStatus.ARCHIVED);
                    tenant.setArchivedAt(now);
                    tenantRepository.save(tenant);

                    subscriptionService.invalidateTenantCachesPostCommit(tenant.getId());
                    log.info("Tenant ID {} transitioned to ARCHIVED", tenant.getId());
                } catch (Exception e) {
                    log.error("Failed to archive tenant ID {}: {}", tenant.getId(), e.getMessage());
                }
            }
        } while (tenantsPage.hasNext());
    }

    /**
     * Runs daily at 2:30 AM to send a final data notification reminder to tenants
     * approaching their 1-year deletion eligibility mark (at 11 months post-expiration in ARCHIVED status).
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional
    public void checkFinalDataReminders() {
        log.info("Subscription final data reminders job triggered");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime thresholdDate = now.minusMonths(11);

        int pageIndex = 0;
        Page<Tenant> tenantsPage;
        do {
            tenantsPage = tenantRepository.findTenantsEligibleForFinalReminder(
                TenantDataStatus.ARCHIVED,
                thresholdDate,
                PageRequest.of(pageIndex, batchSize)
            );

            for (Tenant tenant : tenantsPage.getContent()) {
                try {
                    String planName = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getName() : "Standard Plan";
                    emailService.sendFinalDataReminderEmail(
                        tenant.getAdminEmail(),
                        tenant.getCompanyName(),
                        planName,
                        true // isArchived = true
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

    /**
     * Runs daily at 3:00 AM to transition expired tenant workspaces to soft-delete (ELIGIBLE_FOR_DELETION)
     * after 1 year in ARCHIVED status.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void processTenantSoftDeleteTransition() {
        log.info("Tenant soft-delete transition job triggered");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime thresholdDate = now.minusYears(1);

        Page<Tenant> tenantsPage;
        do {
            // Keep fetching page 0 because transitioned tenants leave the ARCHIVED state
            tenantsPage = tenantRepository.findTenantsEligibleForSoftDelete(
                TenantDataStatus.ARCHIVED, thresholdDate, PageRequest.of(0, batchSize)
            );

            if (tenantsPage.isEmpty()) {
                break;
            }

            for (Tenant tenant : tenantsPage.getContent()) {
                try {
                    tenant.setDataStatus(TenantDataStatus.ELIGIBLE_FOR_DELETION);
                    tenant.setStatus(com.sonixhr.enums.UserStatus.DELETED);
                    tenant.setDeletedAt(now);
                    tenantRepository.save(tenant);

                    subscriptionService.invalidateTenantCachesPostCommit(tenant.getId());
                    log.info("Tenant ID {} transitioned to Soft Delete (ELIGIBLE_FOR_DELETION)", tenant.getId());
                } catch (Exception e) {
                    log.error("Failed to transition tenant ID {} to soft delete: {}", tenant.getId(), e.getMessage());
                }
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
}
