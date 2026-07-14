package com.sonixhr.event.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionRenewedEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final TenantSubscription previousSubscription;

    public SubscriptionRenewedEvent(TenantSubscription subscription, TenantSubscription previousSubscription) {
        super(subscription);
        this.subscription = subscription;
        this.previousSubscription = previousSubscription;
    }
}
