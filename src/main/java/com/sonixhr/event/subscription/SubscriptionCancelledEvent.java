package com.sonixhr.event.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.CancellationType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionCancelledEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final CancellationType cancellationType;
    private final String reason;

    public SubscriptionCancelledEvent(TenantSubscription subscription, CancellationType cancellationType, String reason) {
        super(subscription);
        this.subscription = subscription;
        this.cancellationType = cancellationType;
        this.reason = reason;
    }
}
