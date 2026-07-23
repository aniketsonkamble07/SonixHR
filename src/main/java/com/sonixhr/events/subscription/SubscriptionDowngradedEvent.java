package com.sonixhr.events.subscription;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.TriggerSource;
import com.sonixhr.entity.platform.SubscriptionPlan;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionDowngradedEvent extends ApplicationEvent {
    private final TenantSubscription subscription;
    private final TenantSubscription previousSubscription;
    private final SubscriptionPlan oldPlan;
    private final SubscriptionPlan newPlan;
    private final TriggerSource triggerSource;
    private final Long triggeredBy;

    public SubscriptionDowngradedEvent(TenantSubscription subscription, TenantSubscription previousSubscription,
                                       SubscriptionPlan oldPlan, SubscriptionPlan newPlan) {
        this(subscription, previousSubscription, oldPlan, newPlan, TriggerSource.USER, null);
    }

    public SubscriptionDowngradedEvent(TenantSubscription subscription, TenantSubscription previousSubscription,
                                       SubscriptionPlan oldPlan, SubscriptionPlan newPlan,
                                       TriggerSource triggerSource, Long triggeredBy) {
        super(subscription);
        this.subscription = subscription;
        this.previousSubscription = previousSubscription;
        this.oldPlan = oldPlan;
        this.newPlan = newPlan;
        this.triggerSource = triggerSource != null ? triggerSource : TriggerSource.USER;
        this.triggeredBy = triggeredBy;
    }
}