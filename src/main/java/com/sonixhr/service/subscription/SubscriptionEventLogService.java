package com.sonixhr.service.subscription;

import com.sonixhr.entity.tenant.SubscriptionEventLog;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TriggerSource;
import com.sonixhr.repository.tenant.SubscriptionEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEventLogService {

    private final SubscriptionEventLogRepository eventLogRepository;

    @Transactional
    public void recordEvent(Tenant tenant, TenantSubscription subscription,
                            PlanStatus previousStatus,
                            PlanStatus newStatus,
                            TriggerSource triggerSource,
                            Long userId,
                            String details) {
        String derivedType = deriveEventType(previousStatus, newStatus, details);
        recordEvent(tenant, subscription, derivedType, previousStatus, newStatus, triggerSource, userId, details);
    }

    @Transactional
    public void recordEvent(Tenant tenant, TenantSubscription subscription,
                            String eventType,
                            PlanStatus previousStatus,
                            PlanStatus newStatus,
                            TriggerSource triggerSource,
                            Long userId,
                            String details) {
        try {
            if (tenant == null) {
                log.warn("Cannot record subscription event: tenant is null");
                return;
            }
            log.info("Recording subscription event for tenant: {}", tenant.getId());

            // Convert PlanStatus to String for the entity (using the code)
            String previousStatusStr = previousStatus != null ? previousStatus.getCode() : null;
            String newStatusStr = newStatus != null ? newStatus.getCode() : null;

            SubscriptionEventLog eventLog = SubscriptionEventLog.builder()
                    .tenant(tenant)
                    .subscription(subscription)
                    .eventType(eventType != null ? eventType : "SUBSCRIPTION_CREATED")
                    .previousStatus(previousStatusStr)
                    .newStatus(newStatusStr)
                    .triggerSource(triggerSource != null ? triggerSource : TriggerSource.SYSTEM)
                    .userId(userId)
                    .details(details != null ? details : "Subscription event recorded")
                    .build();

            eventLogRepository.save(eventLog);
            log.info("Subscription event recorded successfully for tenant: {}", tenant.getId());

        } catch (Exception e) {
            log.error("Failed to record subscription event: {}", e.getMessage(), e);
            // Don't throw - just log to prevent transaction rollback
        }
    }

    private String deriveEventType(PlanStatus previousStatus, PlanStatus newStatus, String details) {
        if (previousStatus != null && newStatus != null) {
            if (newStatus == PlanStatus.CANCELLED) {
                return "SUBSCRIPTION_CANCELLED";
            } else if (newStatus == PlanStatus.EXPIRED) {
                return "SUBSCRIPTION_EXPIRED";
            } else if (newStatus == PlanStatus.SUSPENDED) {
                return "SUBSCRIPTION_SUSPENDED";
            } else if (newStatus == PlanStatus.ACTIVE) {
                if (previousStatus == PlanStatus.CANCELLED || previousStatus == PlanStatus.EXPIRED || previousStatus == PlanStatus.SUSPENDED) {
                    return "SUBSCRIPTION_REACTIVATED";
                } else if (previousStatus == PlanStatus.ACTIVE) {
                    String detailsLower = details != null ? details.toLowerCase() : "";
                    if (detailsLower.contains("upgrade")) {
                        return "SUBSCRIPTION_UPGRADED";
                    } else if (detailsLower.contains("downgrade")) {
                        return "SUBSCRIPTION_DOWNGRADED";
                    } else {
                        return "SUBSCRIPTION_RENEWED";
                    }
                }
                return "SUBSCRIPTION_ACTIVATED";
            }
        }
        return "SUBSCRIPTION_CREATED";
    }
}