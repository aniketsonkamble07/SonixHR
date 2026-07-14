package com.sonixhr.event.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionExpiredEvent extends ApplicationEvent {
    private final TenantSubscription subscription;

    public SubscriptionExpiredEvent(TenantSubscription subscription) {
        super(subscription);
        this.subscription = subscription;
    }
}
