package com.sonixhr.events.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.TriggerSource;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionExpiredEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final TriggerSource triggerSource;
    private final String reason;

    public SubscriptionExpiredEvent(TenantSubscription subscription) {
        this(subscription, TriggerSource.SYSTEM, "Subscription expired");
    }

    public SubscriptionExpiredEvent(TenantSubscription subscription, TriggerSource triggerSource, String reason) {
        super(subscription);
        this.subscription = subscription;
        this.triggerSource = triggerSource != null ? triggerSource : TriggerSource.SYSTEM;
        this.reason = reason;
    }
}
