package com.sonixhr.events.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.TriggerSource;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionActivatedEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final TriggerSource triggerSource;
    private final Long triggeredBy;
    private final String notes;

    public SubscriptionActivatedEvent(TenantSubscription subscription) {
        this(subscription, TriggerSource.SYSTEM, null, null);
    }

    public SubscriptionActivatedEvent(TenantSubscription subscription, TriggerSource triggerSource,
                                      Long triggeredBy, String notes) {
        super(subscription);
        this.subscription = subscription;
        this.triggerSource = triggerSource != null ? triggerSource : TriggerSource.SYSTEM;
        this.triggeredBy = triggeredBy;
        this.notes = notes;
    }
}