package com.sonixhr.service.platform;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSchedulerService {

    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;

    /**
     * Runs every day at 1:00 AM to check for subscriptions expiring soon and send reminders,
     * and automatically deactivates expired subscriptions and tenants.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void checkExpiringSubscriptions() {
        log.info("Subscription expiry check scheduler triggered");
        List<TenantSubscription> allSubs = subscriptionRepository.findAll();

        LocalDateTime now = LocalDateTime.now();

        for (TenantSubscription sub : allSubs) {
            if (sub.getIsActive() != null && sub.getIsActive() && sub.getTenant() != null) {
                String toEmail = sub.getTenant().getAdminEmail();
                String companyName = sub.getTenant().getCompanyName();
                String planName = sub.getPlanName();

                if (sub.getEndsAt() != null) {
                    if (sub.getEndsAt().isBefore(now)) {
                        // Subscription expired!
                        log.info("Subscription for tenant {} has expired on {}. Deactivating subscription and tenant.", companyName, sub.getEndsAt());
                        
                        sub.setIsActive(false);
                        subscriptionRepository.save(sub);

                        Tenant tenant = sub.getTenant();
                        tenant.suspend("Subscription expired");
                        tenantRepository.save(tenant);

                        emailService.sendSubscriptionExpiredEmail(toEmail, companyName, planName);
                    } else {
                        // Subscription active, check reminders
                        long days = ChronoUnit.DAYS.between(now, sub.getEndsAt());
                        if (days == 7 || days == 3 || days == 1) {
                            log.info("Subscription for tenant {} expiring in {} days. Sending reminder.", companyName, days);
                            emailService.sendSubscriptionReminderEmail(toEmail, companyName, planName, (int) days);
                        }
                    }
                }
            }
        }
    }
}
