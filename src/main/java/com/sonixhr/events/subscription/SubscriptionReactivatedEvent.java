package com.sonixhr.events.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.TriggerSource;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionReactivatedEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final TenantSubscription expiredSubscription;
    private final TriggerSource triggerSource;
    private final Long triggeredBy;
    private final String reason;

    public SubscriptionReactivatedEvent(TenantSubscription subscription, TenantSubscription expiredSubscription) {
        this(subscription, expiredSubscription, TriggerSource.USER, null, "Subscription reactivated");
    }

    public SubscriptionReactivatedEvent(TenantSubscription subscription, TenantSubscription expiredSubscription,
                                        TriggerSource triggerSource, Long triggeredBy, String reason) {
        super(subscription);
        this.subscription = subscription;
        this.expiredSubscription = expiredSubscription;
        this.triggerSource = triggerSource != null ? triggerSource : TriggerSource.USER;
        this.triggeredBy = triggeredBy;
        this.reason = reason;
    }
}