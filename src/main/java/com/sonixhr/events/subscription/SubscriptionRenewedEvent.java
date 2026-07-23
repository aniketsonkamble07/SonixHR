package com.sonixhr.events.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.TriggerSource;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionRenewedEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final TenantSubscription previousSubscription;
    private final TriggerSource triggerSource;
    private final Long triggeredBy;
    private final boolean autoRenew;

    public SubscriptionRenewedEvent(TenantSubscription subscription, TenantSubscription previousSubscription) {
        this(subscription, previousSubscription, TriggerSource.SYSTEM, null, true);
    }

    public SubscriptionRenewedEvent(TenantSubscription subscription, TenantSubscription previousSubscription,
                                    TriggerSource triggerSource, Long triggeredBy, boolean autoRenew) {
        super(subscription);
        this.subscription = subscription;
        this.previousSubscription = previousSubscription;
        this.triggerSource = triggerSource != null ? triggerSource : TriggerSource.SYSTEM;
        this.triggeredBy = triggeredBy;
        this.autoRenew = autoRenew;
    }
}
