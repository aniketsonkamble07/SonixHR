package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.security.TenantContext;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.attendance.ShiftConfigurationService;
import com.sonixhr.service.employee.EmployeeCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TenantRegistrationServiceTest {

    @InjectMocks
    private TenantRegistrationService tenantRegistrationService;

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantSubscriptionRepository subscriptionRepository;
    @Mock
    private TenantRoleRepository roleRepository;
    @Mock
    private TenantPermissionRepository permissionRepository;
    @Mock
    private ActivationTokenService activationTokenService;
    @Mock
    private WelcomeTenantEmailService welcomeTenantEmailService;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeCodeGenerator employeeCodeGenerator;
    @Mock
    private ShiftConfigurationService shiftConfigurationService;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tenantRegistrationService, "baseUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(tenantRegistrationService, "defaultTrialDays", 14);
        ReflectionTestUtils.setField(tenantRegistrationService, "bypassActivation", false);
    }

    @Test
    void registerTenant_shouldCreateDefaultShiftConfiguration() {
        // Given
        TenantRegistrationRequest request = TenantRegistrationRequest.builder()
                .companyName("Test Company")
                .adminEmail("admin@test.com")
                .adminName("Test Admin")
                .adminPhone("1234567890")
                .planType("basic")
                .build();

        SubscriptionPlan mockPlan = SubscriptionPlan.builder()
                .code("basic")
                .name("Basic Plan")
                .monthlyPrice(49.00)
                .maxEmployees(100)
                .maxStorageMb(1024)
                .trialDays(0)
                .isTrial(false)
                .isActive(true)
                .build();

        when(subscriptionPlanRepository.findByCodeIgnoreCase("basic")).thenReturn(Optional.of(mockPlan));
        when(tenantRepository.existsByCompanyName(any())).thenReturn(false);
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(tenantRepository.existsByTenantCode(any())).thenReturn(false);

        Tenant mockTenant = new Tenant();
        mockTenant.setId(10L);
        mockTenant.setCompanyName("Test Company");
        mockTenant.setTenantCode("TEST-COMPANY");
        mockTenant.setPlanType("basic");
        mockTenant.setStatus(UserStatus.PENDING_VERIFICATION);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(mockTenant);

        TenantPermission permission = new TenantPermission();
        permission.setId(1L);
        permission.setPermission("LEAVE_VIEW");
        when(permissionRepository.findAll()).thenReturn(List.of(permission));

        TenantRole mockRole = new TenantRole();
        mockRole.setId(20L);
        when(roleRepository.save(any(TenantRole.class))).thenReturn(mockRole);

        Employee mockEmployee = new Employee();
        mockEmployee.setId(100L);
        mockEmployee.setFirstName("Test");
        mockEmployee.setLastName("Admin");
        mockEmployee.setEmail("admin@test.com");
        when(employeeCodeGenerator.generateEmployeeCode(any())).thenReturn("EMP001");
        when(passwordEncoder.encode(any())).thenReturn("hashed-pwd");
        when(employeeRepository.save(any(Employee.class))).thenReturn(mockEmployee);

        when(activationTokenService.generateTokenForEmployee(eq(100L))).thenReturn("activation-token");

        // Clear TenantContext before starting
        TenantContext.clear();

        // When
        TenantRegistrationResponse response = tenantRegistrationService.registerTenant(request);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(10L, response.getTenantId());
        assertEquals(100L, response.getSuperAdminEmployeeId());

        // Verify that createShiftConfiguration was called under TenantContext of tenant ID 10
        verify(shiftConfigurationService).createShiftConfiguration(
                argThat(dto -> dto.getShiftName().equals("General 9-5") &&
                        dto.getShiftCode().equals("GENERAL_9-5") &&
                        dto.getWeeklyOffs().containsAll(List.of("SATURDAY", "SUNDAY"))
                ),
                eq(10L),
                eq(100L)
        );

        // Verify that TenantContext is cleared after execution
        assertNull(TenantContext.getCurrentTenant());
    }
}
