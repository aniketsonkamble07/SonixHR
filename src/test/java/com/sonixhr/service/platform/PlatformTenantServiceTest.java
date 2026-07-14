package com.sonixhr.service.platform;

import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.platform.TenantDeletionLog;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.repository.platform.TenantDeletionLogRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.security.TenantRLSService;
import com.sonixhr.service.common.AuditLogService;
import com.sonixhr.service.common.CacheEvictionService;
import com.sonixhr.service.employee.EmployeeDetailsService;
import com.sonixhr.service.tenant.TenantRegistrationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PlatformTenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantSubscriptionRepository subscriptionRepository;
    @Mock
    private EmployeeDetailsService employeeDetailsService;
    @Mock
    private TenantRegistrationService tenantRegistrationService;
    @Mock
    private TenantRLSService tenantRLSService;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CacheEvictionService cacheEvictionService;
    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private TenantDeletionLogRepository tenantDeletionLogRepository;
    @Mock
    private EntityManager entityManager;

    private PlatformTenantService platformTenantService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        platformTenantService = new PlatformTenantService(
                tenantRepository,
                subscriptionRepository,
                employeeDetailsService,
                tenantRegistrationService,
                tenantRLSService,
                subscriptionPlanRepository,
                auditLogService,
                cacheEvictionService,
                platformUserRepository,
                tenantDeletionLogRepository,
                entityManager);
    }

    @Test
    public void testDeleteTenantComplianceLogging() {
        Long tenantId = 100L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setTenantCode("test-tenant");
        tenant.setCompanyName("Test Company");

        PlatformUser adminUser = new PlatformUser();
        adminUser.setId(1L);
        adminUser.setEmail("admin@sonixhr.com");

        // Mock dependencies
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(auditLogService.getCurrentUserId()).thenReturn(1L);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(Optional.empty());

        // Mock EntityManager calls for row counts
        TypedQuery<Long> mockQuery = mock(TypedQuery.class);
        when(mockQuery.setParameter(eq("tenantId"), eq(tenantId))).thenReturn(mockQuery);
        when(mockQuery.getSingleResult()).thenReturn(5L); // Assume 5 rows for each count query
        when(entityManager.createQuery(any(String.class), eq(Long.class))).thenReturn(mockQuery);

        // Mock native and JPQL queries for deletion
        jakarta.persistence.Query mockDeleteQuery = mock(jakarta.persistence.Query.class);
        when(mockDeleteQuery.setParameter(any(String.class), any())).thenReturn(mockDeleteQuery);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(mockDeleteQuery);
        when(entityManager.createQuery(any(String.class))).thenReturn(mockDeleteQuery);

        // Execute delete
        platformTenantService.deleteTenant(tenantId);

        // Verify deletion log is saved
        ArgumentCaptor<TenantDeletionLog> logCaptor = ArgumentCaptor.forClass(TenantDeletionLog.class);
        verify(tenantDeletionLogRepository, times(1)).save(logCaptor.capture());

        TenantDeletionLog savedLog = logCaptor.getValue();
        assertNotNull(savedLog);
        assertEquals(tenantId, savedLog.getTenantId());
        assertEquals("test-tenant", savedLog.getTenantCode());
        assertEquals("Test Company", savedLog.getCompanyName());
        assertEquals(1L, savedLog.getDeletedByAdminId());
        assertEquals("admin@sonixhr.com", savedLog.getDeletedByAdminEmail());

        // Assert json manifest properties
        String manifest = savedLog.getDataManifestJson();
        assertTrue(manifest.contains("\"tenantId\":100"));
        assertTrue(manifest.contains("\"employeesCount\":5"));
        assertTrue(manifest.contains("\"departmentsCount\":5"));

        // Assert SHA-256 hash is generated correctly and is 64 hex chars
        assertNotNull(savedLog.getDataManifestHash());
        assertEquals(64, savedLog.getDataManifestHash().length());

        // Verify delete called on tenant
        verify(tenantRepository, times(1)).delete(tenant);
    }
}
