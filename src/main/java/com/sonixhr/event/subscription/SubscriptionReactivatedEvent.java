package com.sonixhr.event.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionReactivatedEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final TenantSubscription expiredSubscription;

    public SubscriptionReactivatedEvent(TenantSubscription subscription, TenantSubscription expiredSubscription) {
        super(subscription);
        this.subscription = subscription;
        this.expiredSubscription = expiredSubscription;
    }
}
