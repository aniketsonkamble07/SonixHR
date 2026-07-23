// events/subscription/SubscriptionHistoryEventListener.java
package com.sonixhr.events.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.entity.tenant.SubscriptionHistory;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TriggerSource;
import com.sonixhr.repository.tenant.SubscriptionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SubscriptionHistoryEventListener {

    private final SubscriptionHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionActivated(SubscriptionActivatedEvent event) {
        try {
            TenantSubscription subscription = event.getSubscription();
            log.info("Recording activation history for subscription: {}", subscription.getId());

            SubscriptionPlan plan = subscription.getSubscriptionPlan();

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .tenantId(subscription.getTenant().getId())
                    .subscriptionId(subscription.getId().toString())
                    .planId(plan != null ? plan.getId() : null)
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : null)
                    .planType(plan != null ? plan.getCode() : null)
                    .planStatus(PlanStatus.ACTIVE)
                    .eventType("ACTIVATED")
                    .eventDate(LocalDateTime.now())
                    .startDate(subscription.getStartedAt())
                    .endDate(subscription.getBillingPeriodEnd())
                    .amount(subscription.getAmount())
                    .currency(subscription.getCurrency())
                    .triggerSource(event.getTriggerSource())
                    .triggeredById(event.getTriggeredBy())
                    .notes(event.getNotes() != null ? event.getNotes() : "Subscription activated")
                    .metadata(buildMetadata(subscription))
                    .createdBy(getCreatedBy(event))
                    .build();

            historyRepository.save(history);
            log.info("Activation history recorded for subscription: {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to record activation history: {}", e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionCancelled(SubscriptionCancelledEvent event) {
        try {
            TenantSubscription subscription = event.getSubscription();
            log.info("Recording cancellation history for subscription: {}", subscription.getId());

            SubscriptionPlan plan = subscription.getSubscriptionPlan();

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .tenantId(subscription.getTenant().getId())
                    .subscriptionId(subscription.getId().toString())
                    .planId(plan != null ? plan.getId() : null)
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : subscription.getPlanName())
                    .planType(plan != null ? plan.getCode() : null)
                    .planStatus(PlanStatus.CANCELLED)
                    .eventType("CANCELLED")
                    .eventDate(LocalDateTime.now())
                    .endDate(subscription.getBillingPeriodEnd())
                    .amount(subscription.getAmount())
                    .currency(subscription.getCurrency())
                    .reason(event.getReason())
                    .cancellationType(event.getCancellationType().name())
                    .triggerSource(event.getTriggerSource())
                    .triggeredById(event.getTriggeredBy())
                    .notes(String.format("Subscription cancelled: %s", event.getReason()))
                    .metadata(buildCancellationMetadata(subscription, event))
                    .createdBy(getCreatedBy(event))
                    .build();

            historyRepository.save(history);
            log.info("Cancellation history recorded for subscription: {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to record cancellation history: {}", e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionExpired(SubscriptionExpiredEvent event) {
        try {
            TenantSubscription subscription = event.getSubscription();
            log.info("Recording expiration history for subscription: {}", subscription.getId());

            SubscriptionPlan plan = subscription.getSubscriptionPlan();

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .tenantId(subscription.getTenant().getId())
                    .subscriptionId(subscription.getId().toString())
                    .planId(plan != null ? plan.getId() : null)
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : subscription.getPlanName())
                    .planType(plan != null ? plan.getCode() : null)
                    .planStatus(PlanStatus.EXPIRED)
                    .eventType("EXPIRED")
                    .eventDate(LocalDateTime.now())
                    .endDate(subscription.getBillingPeriodEnd())
                    .amount(subscription.getAmount())
                    .currency(subscription.getCurrency())
                    .triggerSource(event.getTriggerSource())
                    .reason(event.getReason())
                    .notes(String.format("Subscription expired: %s", event.getReason()))
                    .metadata(buildExpirationMetadata(subscription))
                    .createdBy("SYSTEM")
                    .build();

            historyRepository.save(history);
            log.info("Expiration history recorded for subscription: {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to record expiration history: {}", e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionRenewed(SubscriptionRenewedEvent event) {
        try {
            TenantSubscription subscription = event.getSubscription();
            TenantSubscription previous = event.getPreviousSubscription();
            log.info("Recording renewal history for subscription: {}", subscription.getId());

            SubscriptionPlan plan = subscription.getSubscriptionPlan();
            SubscriptionPlan previousPlan = previous != null ? previous.getSubscriptionPlan() : null;

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .tenantId(subscription.getTenant().getId())
                    .subscriptionId(subscription.getId().toString())
                    .planId(plan != null ? plan.getId() : null)
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : null)
                    .planType(plan != null ? plan.getCode() : null)
                    .planStatus(PlanStatus.ACTIVE)
                    .eventType("RENEWED")
                    .eventDate(LocalDateTime.now())
                    .startDate(subscription.getStartedAt())
                    .endDate(subscription.getBillingPeriodEnd())
                    .amount(subscription.getAmount())
                    .currency(subscription.getCurrency())
                    .isAutoRenew(event.isAutoRenew())
                    .previousPlanName(previousPlan != null ? previousPlan.getName() :
                            (previous != null ? previous.getPlanName() : null))
                    .previousPlanCode(previousPlan != null ? previousPlan.getCode() : null)
                    .previousPrice(previous != null ? previous.getAmount() : null)
                    .newPrice(subscription.getAmount())
                    .triggerSource(event.getTriggerSource())
                    .triggeredById(event.getTriggeredBy())
                    .notes(event.isAutoRenew() ? "Auto-renewed" : "Manually renewed")
                    .metadata(buildRenewalMetadata(subscription, previous))
                    .createdBy(getCreatedBy(event))
                    .build();

            historyRepository.save(history);
            log.info("Renewal history recorded for subscription: {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to record renewal history: {}", e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionReactivated(SubscriptionReactivatedEvent event) {
        try {
            TenantSubscription subscription = event.getSubscription();
            TenantSubscription expired = event.getExpiredSubscription();
            log.info("Recording reactivation history for subscription: {}", subscription.getId());

            SubscriptionPlan plan = subscription.getSubscriptionPlan();
            SubscriptionPlan expiredPlan = expired != null ? expired.getSubscriptionPlan() : null;

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .tenantId(subscription.getTenant().getId())
                    .subscriptionId(subscription.getId().toString())
                    .planId(plan != null ? plan.getId() : null)
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : null)
                    .planType(plan != null ? plan.getCode() : null)
                    .planStatus(PlanStatus.ACTIVE)
                    .eventType("REACTIVATED")
                    .eventDate(LocalDateTime.now())
                    .startDate(subscription.getStartedAt())
                    .endDate(subscription.getBillingPeriodEnd())
                    .amount(subscription.getAmount())
                    .currency(subscription.getCurrency())
                    .previousPlanName(expiredPlan != null ? expiredPlan.getName() :
                            (expired != null ? expired.getPlanName() : null))
                    .previousPlanCode(expiredPlan != null ? expiredPlan.getCode() : null)
                    .previousPrice(expired != null ? expired.getAmount() : null)
                    .newPrice(subscription.getAmount())
                    .triggerSource(event.getTriggerSource())
                    .triggeredById(event.getTriggeredBy())
                    .reason(event.getReason())
                    .notes(String.format("Subscription reactivated: %s", event.getReason()))
                    .metadata(buildReactivationMetadata(subscription, expired))
                    .createdBy(getCreatedBy(event))
                    .build();

            historyRepository.save(history);
            log.info("Reactivation history recorded for subscription: {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to record reactivation history: {}", e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionUpgraded(SubscriptionUpgradedEvent event) {
        try {
            TenantSubscription subscription = event.getSubscription();
            TenantSubscription previous = event.getPreviousSubscription();
            log.info("Recording upgrade history for subscription: {}", subscription.getId());

            SubscriptionPlan plan = subscription.getSubscriptionPlan();
            SubscriptionPlan oldPlan = event.getOldPlan();
            SubscriptionPlan newPlan = event.getNewPlan();

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .tenantId(subscription.getTenant().getId())
                    .subscriptionId(subscription.getId().toString())
                    .planId(plan != null ? plan.getId() : null)
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : null)
                    .planType(plan != null ? plan.getCode() : null)
                    .planStatus(PlanStatus.ACTIVE)
                    .eventType("UPGRADED")
                    .eventDate(LocalDateTime.now())
                    .startDate(subscription.getStartedAt())
                    .endDate(subscription.getBillingPeriodEnd())
                    .amount(subscription.getAmount())
                    .currency(subscription.getCurrency())
                    .previousPlanId(oldPlan != null ? oldPlan.getId() : null)
                    .previousPlanCode(oldPlan != null ? oldPlan.getCode() : null)
                    .previousPlanName(oldPlan != null ? oldPlan.getName() :
                            (previous != null ? previous.getPlanName() : null))
                    .previousPrice(previous != null ? previous.getAmount() : null)
                    .newPrice(subscription.getAmount())
                    .previousMaxEmployees(previous != null ? previous.getMaxEmployees() : null)
                    .newMaxEmployees(subscription.getMaxEmployees())
                    .fieldChanged("planType,price,maxEmployees")
                    .oldValue(String.format("%s - $%.2f - %d employees",
                            oldPlan != null ? oldPlan.getCode() : "N/A",
                            previous != null && previous.getAmount() != null ? previous.getAmount() : 0,
                            previous != null ? previous.getMaxEmployees() : 0))
                    .newValue(String.format("%s - $%.2f - %d employees",
                            newPlan != null ? newPlan.getCode() : "N/A",
                            subscription.getAmount() != null ? subscription.getAmount() : 0,
                            subscription.getMaxEmployees() != null ? subscription.getMaxEmployees() : 0))
                    .triggerSource(event.getTriggerSource())
                    .triggeredById(event.getTriggeredBy())
                    .notes(String.format("Upgraded from %s to %s",
                            oldPlan != null ? oldPlan.getName() : "N/A",
                            newPlan != null ? newPlan.getName() : "N/A"))
                    .metadata(buildPlanChangeMetadata(subscription, previous, oldPlan, newPlan))
                    .createdBy(getCreatedBy(event))
                    .build();

            historyRepository.save(history);
            log.info("Upgrade history recorded for subscription: {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to record upgrade history: {}", e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionDowngraded(SubscriptionDowngradedEvent event) {
        try {
            TenantSubscription subscription = event.getSubscription();
            TenantSubscription previous = event.getPreviousSubscription();
            log.info("Recording downgrade history for subscription: {}", subscription.getId());

            SubscriptionPlan plan = subscription.getSubscriptionPlan();
            SubscriptionPlan oldPlan = event.getOldPlan();
            SubscriptionPlan newPlan = event.getNewPlan();

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .tenantId(subscription.getTenant().getId())
                    .subscriptionId(subscription.getId().toString())
                    .planId(plan != null ? plan.getId() : null)
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : null)
                    .planType(plan != null ? plan.getCode() : null)
                    .planStatus(PlanStatus.ACTIVE)
                    .eventType("DOWNGRADED")
                    .eventDate(LocalDateTime.now())
                    .startDate(subscription.getStartedAt())
                    .endDate(subscription.getBillingPeriodEnd())
                    .amount(subscription.getAmount())
                    .currency(subscription.getCurrency())
                    .previousPlanId(oldPlan != null ? oldPlan.getId() : null)
                    .previousPlanCode(oldPlan != null ? oldPlan.getCode() : null)
                    .previousPlanName(oldPlan != null ? oldPlan.getName() :
                            (previous != null ? previous.getPlanName() : null))
                    .previousPrice(previous != null ? previous.getAmount() : null)
                    .newPrice(subscription.getAmount())
                    .previousMaxEmployees(previous != null ? previous.getMaxEmployees() : null)
                    .newMaxEmployees(subscription.getMaxEmployees())
                    .fieldChanged("planType,price,maxEmployees")
                    .oldValue(String.format("%s - $%.2f - %d employees",
                            oldPlan != null ? oldPlan.getCode() : "N/A",
                            previous != null && previous.getAmount() != null ? previous.getAmount() : 0,
                            previous != null ? previous.getMaxEmployees() : 0))
                    .newValue(String.format("%s - $%.2f - %d employees",
                            newPlan != null ? newPlan.getCode() : "N/A",
                            subscription.getAmount() != null ? subscription.getAmount() : 0,
                            subscription.getMaxEmployees() != null ? subscription.getMaxEmployees() : 0))
                    .triggerSource(event.getTriggerSource())
                    .triggeredById(event.getTriggeredBy())
                    .notes(String.format("Downgraded from %s to %s",
                            oldPlan != null ? oldPlan.getName() : "N/A",
                            newPlan != null ? newPlan.getName() : "N/A"))
                    .metadata(buildPlanChangeMetadata(subscription, previous, oldPlan, newPlan))
                    .createdBy(getCreatedBy(event))
                    .build();

            historyRepository.save(history);
            log.info("Downgrade history recorded for subscription: {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to record downgrade history: {}", e.getMessage(), e);
        }
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private String getCreatedBy(Object event) {
        if (event instanceof SubscriptionActivatedEvent e) {
            return e.getTriggerSource() == TriggerSource.SYSTEM ? "SYSTEM" : "USER_" + e.getTriggeredBy();
        } else if (event instanceof SubscriptionCancelledEvent e) {
            return e.getTriggerSource() == TriggerSource.SYSTEM ? "SYSTEM" : "USER_" + e.getTriggeredBy();
        } else if (event instanceof SubscriptionRenewedEvent e) {
            return e.getTriggerSource() == TriggerSource.SYSTEM ? "SYSTEM" : "USER_" + e.getTriggeredBy();
        } else if (event instanceof SubscriptionReactivatedEvent e) {
            return e.getTriggerSource() == TriggerSource.SYSTEM ? "SYSTEM" : "USER_" + e.getTriggeredBy();
        } else if (event instanceof SubscriptionUpgradedEvent e) {
            return e.getTriggerSource() == TriggerSource.SYSTEM ? "SYSTEM" : "USER_" + e.getTriggeredBy();
        } else if (event instanceof SubscriptionDowngradedEvent e) {
            return e.getTriggerSource() == TriggerSource.SYSTEM ? "SYSTEM" : "USER_" + e.getTriggeredBy();
        }
        return "SYSTEM";
    }

    private String buildMetadata(TenantSubscription subscription) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId());
        metadata.put("tenantId", subscription.getTenant().getId());
        if (subscription.getSubscriptionPlan() != null) {
            metadata.put("planId", subscription.getSubscriptionPlan().getId());
            metadata.put("validityMonths", subscription.getSubscriptionPlan().getValidityMonths());
        }
        metadata.put("autoRenew", subscription.getAutoRenew());
        return convertToJson(metadata);
    }

    private String buildCancellationMetadata(TenantSubscription subscription, SubscriptionCancelledEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId());
        metadata.put("cancellationType", event.getCancellationType().name());
        metadata.put("cancelledAtEndOfPeriod", subscription.getCancelledAtEndOfPeriod());
        metadata.put("cancellationReason", event.getReason());
        return convertToJson(metadata);
    }

    private String buildExpirationMetadata(TenantSubscription subscription) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId());
        metadata.put("expiredAt", LocalDateTime.now());
        metadata.put("billingPeriodEnd", subscription.getBillingPeriodEnd());
        return convertToJson(metadata);
    }

    private String buildRenewalMetadata(TenantSubscription subscription, TenantSubscription previous) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId());
        metadata.put("previousSubscriptionId", previous != null ? previous.getId() : null);
        metadata.put("previousEndDate", previous != null ? previous.getBillingPeriodEnd() : null);
        metadata.put("newEndDate", subscription.getBillingPeriodEnd());
        metadata.put("autoRenew", subscription.getAutoRenew());
        return convertToJson(metadata);
    }

    private String buildReactivationMetadata(TenantSubscription subscription, TenantSubscription expired) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId());
        metadata.put("expiredSubscriptionId", expired != null ? expired.getId() : null);
        metadata.put("expiredAt", expired != null ? expired.getEndedAt() : null);
        metadata.put("reactivatedAt", LocalDateTime.now());
        return convertToJson(metadata);
    }

    private String buildPlanChangeMetadata(TenantSubscription subscription, TenantSubscription previous,
                                         SubscriptionPlan oldPlan, SubscriptionPlan newPlan) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId());
        metadata.put("previousSubscriptionId", previous != null ? previous.getId() : null);
        metadata.put("oldPlan", oldPlan != null ? oldPlan.getCode() : null);
        metadata.put("newPlan", newPlan != null ? newPlan.getCode() : null);
        metadata.put("oldPrice", previous != null ? previous.getAmount() : null);
        metadata.put("newPrice", subscription.getAmount());
        metadata.put("oldMaxEmployees", previous != null ? previous.getMaxEmployees() : null);
        metadata.put("newMaxEmployees", subscription.getMaxEmployees());
        return convertToJson(metadata);
    }

    private String convertToJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return metadata.toString();
        }
    }
}