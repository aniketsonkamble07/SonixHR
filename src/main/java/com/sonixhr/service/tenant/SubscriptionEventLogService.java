package com.sonixhr.service.tenant;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.tenant.TenantSubscriptionEvent;
import com.sonixhr.enums.TriggerSource;
import com.sonixhr.repository.tenant.TenantSubscriptionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEventLogService {

    private final TenantSubscriptionEventRepository eventRepository;

    @Transactional
    public void recordEvent(Tenant tenant, TenantSubscription sub, String fromStatus, String toStatus, TriggerSource source, Long operatorId, String reason) {
        log.info("Recording subscription event for tenant {}: {} -> {} (Source: {}, Reason: {})", 
                tenant.getId(), fromStatus, toStatus, source, reason);
        TenantSubscriptionEvent event = TenantSubscriptionEvent.builder()
                .tenant(tenant)
                .tenantSubscription(sub)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .triggeredBy(source)
                .triggeredById(operatorId)
                .reason(reason)
                .build();
        eventRepository.save(event);
    }
}
