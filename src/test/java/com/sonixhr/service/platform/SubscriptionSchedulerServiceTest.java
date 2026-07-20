package com.sonixhr.service.platform;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.tenant.TenantSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SubscriptionSchedulerServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantSubscriptionService subscriptionService;
    @Mock private EmailService emailService;
    @Mock private com.sonixhr.repository.tenant.TenantSubscriptionRepository subscriptionRepository;
    @Mock private PlatformTenantService platformTenantService;
    @Mock private com.sonixhr.service.tenant.SubscriptionEventLogService eventLogService;

    private SubscriptionSchedulerService schedulerService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        schedulerService = new SubscriptionSchedulerService(
                subscriptionRepository,
                subscriptionService,
                emailService,
                tenantRepository,
                platformTenantService,
                eventLogService
        );
        ReflectionTestUtils.setField(schedulerService, "batchSize", 100);
    }

    @Test
    public void testCheckArchiveWarnings() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setAdminEmail("admin@warn.com");
        tenant.setCompanyName("Warn Inc");
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        // mock return page
        when(tenantRepository.findTenantsEligibleForArchiveWarning(
                eq(TenantDataStatus.RETAINED), any(LocalDateTime.class), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(Collections.singletonList(tenant)));

        schedulerService.checkArchiveWarnings();

        // Verify notification email sent
        verify(emailService, times(1)).sendArchiveWarningEmail(
                eq("admin@warn.com"), eq("Warn Inc"), any(), eq(7)
        );
        // Verify tenant flag is updated
        assertNotNull(tenant.getArchiveWarningNotifiedAt());
        verify(tenantRepository, times(1)).save(tenant);
    }

    @Test
    public void testProcessTenantArchivals() {
        Tenant tenant = new Tenant();
        tenant.setId(20L);
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findTenantsEligibleForArchive(
                eq(TenantDataStatus.RETAINED), any(LocalDateTime.class), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(Collections.singletonList(tenant)))
          .thenReturn(new PageImpl<>(Collections.emptyList())); // Stop loop next page

        schedulerService.processTenantArchivals();

        // Verify transition to ARCHIVED
        assertEquals(TenantDataStatus.ARCHIVED, tenant.getDataStatus());
        assertNotNull(tenant.getArchivedAt());
        verify(tenantRepository, times(1)).save(tenant);

        // Verify cache invalidation post-commit
        verify(subscriptionService, times(1)).invalidateTenantCachesPostCommit(20L);
    }

    @Test
    public void testCheckFinalDataReminders() {
        Tenant tenant = new Tenant();
        tenant.setId(30L);
        tenant.setAdminEmail("admin@final.com");
        tenant.setCompanyName("Final Inc");
        tenant.setDataStatus(TenantDataStatus.ARCHIVED);

        when(tenantRepository.findTenantsEligibleForFinalReminder(
                eq(TenantDataStatus.ARCHIVED), any(LocalDateTime.class), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(Collections.singletonList(tenant)));

        schedulerService.checkFinalDataReminders();

        // Verify final reminder email sent
        verify(emailService, times(1)).sendFinalDataReminderEmail(
                eq("admin@final.com"), eq("Final Inc"), any(), eq(true)
        );
        // Verify reminder timestamp updated
        assertNotNull(tenant.getFinalReminderSentAt());
        verify(tenantRepository, times(1)).save(tenant);
    }

    @Test
    public void testProcessTenantSoftDeleteTransition() {
        Tenant tenant = new Tenant();
        tenant.setId(40L);
        tenant.setDataStatus(TenantDataStatus.ARCHIVED);

        when(tenantRepository.findTenantsEligibleForSoftDelete(
                eq(TenantDataStatus.ARCHIVED), any(LocalDateTime.class), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(Collections.singletonList(tenant)))
          .thenReturn(new PageImpl<>(Collections.emptyList())); // Stop loop

        schedulerService.processTenantSoftDeleteTransition();

        // Verify transition to ELIGIBLE_FOR_DELETION (Soft Delete)
        assertEquals(TenantDataStatus.ELIGIBLE_FOR_DELETION, tenant.getDataStatus());
        assertEquals(com.sonixhr.enums.UserStatus.DELETED, tenant.getStatus());
        assertNotNull(tenant.getDeletedAt());
        verify(tenantRepository, times(1)).save(tenant);
    }
}
