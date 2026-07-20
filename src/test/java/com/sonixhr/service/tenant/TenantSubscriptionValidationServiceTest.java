package com.sonixhr.service.tenant;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.tenant.TenantSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TenantSubscriptionValidationServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private com.sonixhr.service.platform.FeatureAccessService featureAccessService;

    private TenantSubscriptionValidationService validationService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        validationService = new TenantSubscriptionValidationService(
                tenantRepository,
                featureAccessService
        );
        lenient().when(featureAccessService.hasFeature(any(), any())).thenReturn(true);
    }

    @Test
    public void testValidateSubscription_Active() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(LocalDateTime.now().plusDays(10));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertDoesNotThrow(() -> {
            validationService.validateSubscription(tenantId, "/api/employees", Collections.emptyList());
        });
    }

    @Test
    public void testValidateSubscription_PastDue_Employee_ReadOnly_Allowed() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.PAST_DUE);
        tenant.setEndsAt(LocalDateTime.now().minusDays(1));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));

        // GET request is read-only, so it should be allowed
        assertDoesNotThrow(() -> {
            validationService.validateSubscription(tenantId, "/api/employees", "GET", authorities, null);
        });

        // POST request is a write, so it should be blocked
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/employees", "POST", authorities, null);
        });
        assertTrue(exception.getMessage().contains("The workspace is read-only"));
    }

    @Test
    public void testValidateSubscription_PastDue_Admin_NonAllowedPath_Blocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.PAST_DUE);
        tenant.setEndsAt(LocalDateTime.now().minusDays(1));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MANAGE_SUBSCRIPTION"), new SimpleGrantedAuthority("VIEW_BILLING"));

        // Admin cannot access workspace paths like /api/employees
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/employees", "GET", authorities, null);
        });
        assertTrue(exception.getMessage().contains("Company Admin can only access"));
    }

    @Test
    public void testValidateSubscription_PastDue_Admin_AllowedPaths() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.PAST_DUE);
        tenant.setEndsAt(LocalDateTime.now().minusDays(1));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MANAGE_SUBSCRIPTION"), new SimpleGrantedAuthority("VIEW_BILLING"));

        // Billing path should be allowed
        assertDoesNotThrow(() -> {
            validationService.validateSubscription(tenantId, "/api/tenant/subscriptions/current", "GET", authorities, null);
        });

        // Export path should be allowed
        assertDoesNotThrow(() -> {
            validationService.validateSubscription(tenantId, "/api/export/all", "GET", authorities, null);
        });

        // Support path should be allowed
        assertDoesNotThrow(() -> {
            validationService.validateSubscription(tenantId, "/api/employee/support-tickets", "POST", authorities, null);
        });
    }

    @Test
    public void testValidateSubscription_PastDue_ApiAndWebhooks_Blocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.PAST_DUE);
        tenant.setEndsAt(LocalDateTime.now().minusDays(1));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MANAGE_SUBSCRIPTION"), new SimpleGrantedAuthority("VIEW_BILLING"));

        // Path containing webhook should be blocked
        BusinessException exception1 = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/webhooks/trigger", "POST", authorities, null);
        });
        assertTrue(exception1.getMessage().contains("Webhook access is blocked"));

        // Request with X-API-Key header should be blocked
        jakarta.servlet.http.HttpServletRequest mockRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(mockRequest.getHeader("X-API-Key")).thenReturn("test-key");

        BusinessException exception2 = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/tenant/subscriptions/current", "GET", authorities, mockRequest);
        });
        assertTrue(exception2.getMessage().contains("API access is blocked"));
    }

    @Test
    public void testValidateSubscription_Expired_Retained_SelfServeAllowed() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(false);
        tenant.setStatus(UserStatus.SUSPENDED);
        tenant.setPlanStatus(PlanStatus.EXPIRED);
        tenant.setEndsAt(LocalDateTime.now().minusDays(5));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MANAGE_SUBSCRIPTION"), new SimpleGrantedAuthority("VIEW_BILLING"));

        // Company Admin can access billing/renewal paths to self-serve renew even if tenant is inactive/suspended
        assertDoesNotThrow(() -> {
            validationService.validateSubscription(tenantId, "/api/tenant/subscriptions/current", "GET", authorities, null);
        });

        // Other paths (e.g. employee list) are blocked for Admin
        BusinessException exceptionAdmin = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/employees", "GET", authorities, null);
        });
        assertTrue(exceptionAdmin.getMessage().contains("Subscription has expired. Please log in and renew online."));

        // Regular employee is blocked from all paths (even renewal paths)
        List<GrantedAuthority> employeeAuth = List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        BusinessException exceptionEmployee = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/tenant/subscriptions/current", "GET", employeeAuth, null);
        });
        assertTrue(exceptionEmployee.getMessage().contains("Subscription has expired. Please log in and renew online."));
    }

    @Test
    public void testValidateSubscription_Expired_Archived_Blocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(false);
        tenant.setStatus(UserStatus.SUSPENDED);
        tenant.setPlanStatus(PlanStatus.EXPIRED);
        tenant.setEndsAt(LocalDateTime.now().minusDays(40));
        tenant.setDataStatus(TenantDataStatus.ARCHIVED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MANAGE_SUBSCRIPTION"), new SimpleGrantedAuthority("VIEW_BILLING"));

        // Even billing/renewal path is blocked for Admin when archived
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/tenant/subscriptions/current", "GET", authorities, null);
        });
        assertTrue(exception.getMessage().contains("workspace is archived. Please contact support to restore"));
    }

    @Test
    public void testValidateSubscription_Expired_EligibleForDeletion_Blocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(false);
        tenant.setStatus(UserStatus.SUSPENDED);
        tenant.setPlanStatus(PlanStatus.EXPIRED);
        tenant.setEndsAt(LocalDateTime.now().minusYears(2));
        tenant.setDataStatus(TenantDataStatus.ELIGIBLE_FOR_DELETION);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MANAGE_SUBSCRIPTION"), new SimpleGrantedAuthority("VIEW_BILLING"));

        // Marked for deletion error
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/tenant/subscriptions/current", "GET", authorities, null);
        });
        assertTrue(exception.getMessage().contains("workspace is marked for deletion"));
    }

    @Test
    public void testValidateSubscription_ApiAccess_FeatureBlocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(LocalDateTime.now().plusDays(10));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(featureAccessService.hasFeature(tenantId, "API_ACCESS")).thenReturn(false);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/public/some-endpoint", "GET", authorities, null);
        });
        assertTrue(exception.getMessage().contains("API access is not enabled"));
    }

    @Test
    public void testValidateSubscription_WebhookAccess_FeatureBlocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(LocalDateTime.now().plusDays(10));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(featureAccessService.hasFeature(tenantId, "WEBHOOK_ACCESS")).thenReturn(false);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/webhooks/trigger", "POST", authorities, null);
        });
        assertTrue(exception.getMessage().contains("Webhook access is not enabled"));
    }

    @Test
    public void testValidateSubscription_Payroll_FeatureBlocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(LocalDateTime.now().plusDays(10));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(featureAccessService.hasFeature(tenantId, "PAYROLL")).thenReturn(false);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/payroll/payruns", "POST", authorities, null);
        });
        assertTrue(exception.getMessage().contains("Payroll feature is not enabled"));
    }

    @Test
    public void testValidateSubscription_Leave_FeatureBlocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(LocalDateTime.now().plusDays(10));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(featureAccessService.hasFeature(tenantId, "LEAVE_MANAGEMENT")).thenReturn(false);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/employees/leaves/settings", "GET", authorities, null);
        });
        assertTrue(exception.getMessage().contains("Leave Management feature is not enabled"));
    }

    @Test
    public void testValidateSubscription_Attendance_FeatureBlocked() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setActive(true);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenant.setEndsAt(LocalDateTime.now().plusDays(10));
        tenant.setDataStatus(TenantDataStatus.RETAINED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(featureAccessService.hasFeature(tenantId, "ATTENDANCE")).thenReturn(false);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            validationService.validateSubscription(tenantId, "/api/attendance/checkin", "POST", authorities, null);
        });
        assertTrue(exception.getMessage().contains("Attendance tracking feature is not enabled"));
    }
}
