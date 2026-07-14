package com.sonixhr.event.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionActivatedEvent extends ApplicationEvent {
    private final TenantSubscription subscription;

    public SubscriptionActivatedEvent(TenantSubscription subscription) {
        super(subscription);
        this.subscription = subscription;
    }
}
