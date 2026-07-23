package com.sonixhr.events.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.CancellationType;
import com.sonixhr.enums.TriggerSource;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionCancelledEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final CancellationType cancellationType;
    private final String reason;
    private final TriggerSource triggerSource;
    private final Long triggeredBy;

    public SubscriptionCancelledEvent(TenantSubscription subscription, CancellationType cancellationType, String reason) {
        this(subscription, cancellationType, reason, TriggerSource.USER, null);
    }

    public SubscriptionCancelledEvent(TenantSubscription subscription, CancellationType cancellationType,
                                      String reason, TriggerSource triggerSource, Long triggeredBy) {
        super(subscription);
        this.subscription = subscription;
        this.cancellationType = cancellationType;
        this.reason = reason;
        this.triggerSource = triggerSource != null ? triggerSource : TriggerSource.USER;
        this.triggeredBy = triggeredBy;
    }
}