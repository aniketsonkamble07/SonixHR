package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantSubscriptionResponseDTO;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.security.TenantRLSService;
import com.sonixhr.service.common.CacheEvictionService;
import com.sonixhr.service.common.AuditLogService;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.service.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TenantSubscriptionServiceTest {

    @Mock private TenantSubscriptionRepository subscriptionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private TenantRLSService tenantRLSService;
    @Mock private CacheEvictionService cacheEvictionService;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private PlatformUserRepository platformUserRepository;

    private TenantSubscriptionService subscriptionService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        subscriptionService = new TenantSubscriptionService(
                subscriptionRepository,
                tenantRepository,
                subscriptionPlanRepository,
                tenantRLSService,
                cacheEvictionService,
                emailService,
                eventPublisher,
                auditLogService,
                platformUserRepository
        );
    }

    @Test
    public void testTenantExpireIdempotency() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test-tenant");
        tenant.setCompanyName("Test Company");
        tenant.setAdminEmail("admin@test.com");
        tenant.setAdminName("Admin");

        // First expiration call
        tenant.expire("Overdue payment");
        assertNotNull(tenant.getExpiredAt());
        assertEquals(TenantDataStatus.RETAINED, tenant.getDataStatus());
        assertEquals(UserStatus.SUSPENDED, tenant.getStatus());

        LocalDateTime expiredTime = tenant.getExpiredAt();

        // Second call (e.g. repeat run or retry)
        tenant.expire("Overdue payment retry");
        // Verify state remains RETAINED, and expiredAt is not changed
        assertEquals(expiredTime, tenant.getExpiredAt());
        assertEquals(TenantDataStatus.RETAINED, tenant.getDataStatus());
    }

    @Test
    public void testResetSubscriptionLifecycle() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test-tenant");
        tenant.setCompanyName("Test Company");
        tenant.setAdminEmail("admin@test.com");
        tenant.setAdminName("Admin");
        tenant.expire("Overdue payment");
        tenant.setDataStatus(TenantDataStatus.ARCHIVED);
        tenant.setArchivedAt(LocalDateTime.now());
        tenant.setArchiveWarningNotifiedAt(LocalDateTime.now());
        tenant.setFinalReminderSentAt(LocalDateTime.now());
        tenant.setExpirationNotifiedAt(LocalDateTime.now());

        tenant.resetSubscriptionLifecycle();

        assertNull(tenant.getExpiredAt());
        assertNull(tenant.getArchivedAt());
        assertNull(tenant.getArchiveWarningNotifiedAt());
        assertNull(tenant.getFinalReminderSentAt());
        assertNull(tenant.getExpirationNotifiedAt());
        assertEquals(TenantDataStatus.RETAINED, tenant.getDataStatus());
        assertEquals(UserStatus.ACTIVE, tenant.getStatus());
        assertTrue(tenant.getIsActive());
    }

    @Test
    public void testReactivateExpiredSubscription_ArchivedBlock() {
        Tenant tenant = new Tenant();
        tenant.setDataStatus(TenantDataStatus.ARCHIVED);

        TenantSubscription expiredSub = new TenantSubscription();
        expiredSub.setStatus(PlanStatus.EXPIRED);
        expiredSub.setTenant(tenant);

        assertThrows(BusinessException.class, () -> {
            subscriptionService.reactivateExpiredSubscription(expiredSub);
        });
    }

    @Test
    public void testRestoreArchivedTenant() {
        Long tenantId = 1L;
        Long planId = 2L;

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setTenantCode("archived-tenant");
        tenant.setCompanyName("Archived Company");
        tenant.setAdminEmail("admin@archived.com");
        tenant.setAdminName("Admin");
        tenant.setDataStatus(TenantDataStatus.ARCHIVED);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(planId)
                .name("Premium Plan")
                .price(BigDecimal.valueOf(100))
                .validityMonths(12)
                .build();

        TenantSubscription oldSub = new TenantSubscription();
        oldSub.setIsCurrent(true);
        oldSub.setIsActive(true);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)).thenReturn(Optional.of(oldSub));
        when(subscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantSubscriptionResponseDTO result = subscriptionService.restoreArchivedTenant(tenantId, planId, "Support restore notes");

        assertNotNull(result);
        assertEquals("Premium Plan", result.getPlanName());
        assertEquals(PlanStatus.ACTIVE, result.getPlanStatus());
        assertEquals(TenantDataStatus.RETAINED, tenant.getDataStatus());

        // Verify the old subscription is deactivated and saved/flushed first
        assertFalse(oldSub.getIsCurrent());
        assertFalse(oldSub.getIsActive());
        verify(subscriptionRepository, times(1)).saveAndFlush(oldSub);

        // Verify audit log call
        verify(auditLogService, times(1)).log(
                eq(tenant),
                eq("TENANT_RESTORE"),
                eq("dataStatus"),
                eq("ARCHIVED"),
                eq("RETAINED"),
                any(Long.class),
                contains("Support restore notes")
        );
    }

    @Test
    public void testRestoreArchivedTenant_DeletedBlock() {
        Long tenantId = 1L;
        Long planId = 2L;

        // Case A: UserStatus is DELETED
        Tenant tenant1 = new Tenant();
        tenant1.setId(tenantId);
        tenant1.setStatus(UserStatus.DELETED);
        tenant1.setDataStatus(TenantDataStatus.ARCHIVED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant1));

        assertThrows(BusinessException.class, () -> {
            subscriptionService.restoreArchivedTenant(tenantId, planId, "Restore attempt");
        });

        // Case B: TenantDataStatus is DELETED
        Tenant tenant2 = new Tenant();
        tenant2.setId(tenantId);
        tenant2.setStatus(UserStatus.SUSPENDED);
        tenant2.setDataStatus(TenantDataStatus.DELETED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant2));

        assertThrows(BusinessException.class, () -> {
            subscriptionService.restoreArchivedTenant(tenantId, planId, "Restore attempt");
        });
    }

    @Test
    public void testRestoreArchivedTenant_ActiveBlock() {
        Long tenantId = 1L;
        Long planId = 2L;

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThrows(BusinessException.class, () -> {
            subscriptionService.restoreArchivedTenant(tenantId, planId, "Restore attempt");
        });
    }

    @Test
    public void testCancelSubscription() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setActive(true);
        
        TenantSubscription sub = new TenantSubscription();
        sub.setId(10L);
        sub.setTenant(tenant);
        sub.setStatus(PlanStatus.ACTIVE);
        sub.setIsActive(true);
        sub.setIsCurrent(true);
        
        when(subscriptionRepository.findByTenantIdAndIsCurrentTrue(tenantId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        TenantSubscriptionResponseDTO result = subscriptionService.cancelSubscription(tenantId);
        
        assertNotNull(result);
        assertEquals(PlanStatus.TERMINATED, result.getPlanStatus());
        assertEquals(UserStatus.INACTIVE, tenant.getStatus());
        assertFalse(tenant.getIsActive());
        
        verify(subscriptionRepository, times(1)).save(sub);
        verify(tenantRepository, times(1)).save(tenant);
    }
}
