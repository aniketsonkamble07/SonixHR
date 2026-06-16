package com.sonixhr.service.leave;

import com.sonixhr.dto.leave.LeaveRequestDTO;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.Gender;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.LeaveType;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.LeavePoliciesNotConfiguredException;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.leave.LeaveRequestRepository;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import com.sonixhr.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @InjectMocks
    private LeaveService leaveService;

    @Mock
    private LeaveRequestRepository leaveRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ManualAttendanceRepository attendanceRepository;

    @Mock
    private TenantLeaveSettingsRepository settingsRepository;

    @Mock
    private PublicHolidayRepository holidayRepository;

    @Mock
    private LeaveConfigurationService leaveConfigService;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    private Employee mockEmployee;
    private TenantLeaveSettings mockSettings;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);

        mockEmployee = new Employee();
        mockEmployee.setId(101L);
        mockEmployee.setFirstName("John");
        mockEmployee.setLastName("Doe");
        mockEmployee.setEmail("john.doe@example.com");
        mockEmployee.setGender(Gender.MALE);
        mockEmployee.setHireDate(LocalDate.now().minusMonths(12)); // 1 year tenure
        mockEmployee.setTenant(tenant);

        mockSettings = new TenantLeaveSettings();
        mockSettings.setTenantId(1L);
        mockSettings.setLeavePolicies(TenantLeaveSettings.createDefaultPolicies());
        mockSettings.setMaxConsecutiveLeaveDays(30);
        mockSettings.setLeaveApprovalRequired(true);
        mockSettings.setAutoApproveForManager(false);
        mockSettings.setPoliciesConfigured(true);
    }

    @Test
    void getLeaveBalanceWithTenantSettings_shouldFilterAllowedTypesAndGenderEligibility() {
        // Prepare policies
        Map<String, Object> policies = new HashMap<>();
        // CASUAL - allowed, ALL
        policies.put("CASUAL", Map.of(
                "allowed", true,
                "daysPerYear", 12,
                "carryForward", false,
                "genderEligibility", "ALL"
        ));
        // SICK - disallowed
        policies.put("SICK", Map.of(
                "allowed", false,
                "daysPerYear", 12,
                "carryForward", false,
                "genderEligibility", "ALL"
        ));
        // MATERNITY - allowed, FEMALE (MALE employee should NOT see this)
        policies.put("MATERNITY", Map.of(
                "allowed", true,
                "daysPerYear", 84,
                "carryForward", false,
                "genderEligibility", "FEMALE"
        ));
        // PATERNITY - allowed, MALE (MALE employee SHOULD see this)
        policies.put("PATERNITY", Map.of(
                "allowed", true,
                "daysPerYear", 5,
                "carryForward", false,
                "genderEligibility", "MALE"
        ));

        mockSettings.setLeavePolicies(policies);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        when(leaveRepository.getUsedLeaveDays(eq(101L), any(LeaveType.class), anyInt())).thenReturn(0.0);

        Map<String, Object> balance = leaveService.getLeaveBalanceWithTenantSettings(101L, 1L);

        assertNotNull(balance);
        assertTrue(balance.containsKey("CASUAL"));
        assertTrue(balance.containsKey("PATERNITY"));
        assertFalse(balance.containsKey("SICK"));
        assertFalse(balance.containsKey("MATERNITY"));

        // Check CASUAL properties
        Map<?, ?> casualVal = (Map<?, ?>) balance.get("CASUAL");
        assertEquals(12.0, casualVal.get("total"));
        assertEquals(12.0, casualVal.get("remaining"));

        // Check summary properties (Total: CASUAL (12) + PATERNITY (5) = 17)
        Map<?, ?> summaryVal = (Map<?, ?>) balance.get("summary");
        assertEquals(17.0, summaryVal.get("totalAvailable"));
        assertEquals(0.0, summaryVal.get("totalUsed"));
    }

    @Test
    void requestLeaveWithTenantSettings_shouldRejectIfNotAllowed() {
        Map<String, Object> policies = new HashMap<>();
        policies.put("CASUAL", Map.of(
                "allowed", false,
                "daysPerYear", 12,
                "carryForward", false,
                "genderEligibility", "ALL"
        ));
        mockSettings.setLeavePolicies(policies);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Vacation")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee)
        );
        assertTrue(ex.getMessage().contains("is not enabled for this tenant"));
    }

    @Test
    void requestLeaveWithTenantSettings_shouldRejectIfGenderIneligible() {
        Map<String, Object> policies = new HashMap<>();
        policies.put("MATERNITY", Map.of(
                "allowed", true,
                "daysPerYear", 84,
                "carryForward", false,
                "genderEligibility", "FEMALE"
        ));
        mockSettings.setLeavePolicies(policies);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.MATERNITY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Maternity leave")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee)
        );
        assertTrue(ex.getMessage().contains("is not eligible for Maternity Leave based on gender"));
    }

    @Test
    void requestLeaveWithTenantSettings_shouldRejectIfTenureIneligible() {
        Map<String, Object> policies = new HashMap<>();
        policies.put("EARNED", Map.of(
                "allowed", true,
                "daysPerYear", 15,
                "carryForward", true,
                "minimumServiceMonths", 6,
                "genderEligibility", "ALL"
        ));
        mockSettings.setLeavePolicies(policies);

        // Employee with 2 months service (hired 2 months ago)
        mockEmployee.setHireDate(LocalDate.now().minusMonths(2));

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.EARNED)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Earned leave")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee)
        );
        assertTrue(ex.getMessage().contains("does not meet the minimum service requirement of 6 months"));
    }

    @Test
    void requestLeaveWithTenantSettings_shouldSucceedIfEligible() {
        Map<String, Object> policies = new HashMap<>();
        policies.put("EARNED", Map.of(
                "allowed", true,
                "daysPerYear", 15,
                "carryForward", true,
                "minimumServiceMonths", 6,
                "genderEligibility", "ALL"
        ));
        mockSettings.setLeavePolicies(policies);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        
        // Mocks for database balance checks and save
        when(leaveRepository.getUsedLeaveDays(eq(101L), eq(LeaveType.EARNED), anyInt())).thenReturn(0.0);
        
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(500L);
            return req;
        });

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.EARNED)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Earned leave")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(LeaveStatus.PENDING, response.getStatus());
    }

    @Test
    void requestLeaveWithTenantSettings_shouldApplyCarryForwardSuccessfully() {
        // Policy allows carry forward up to 10 days
        Map<String, Object> policies = new HashMap<>();
        policies.put("EARNED", Map.of(
                "allowed", true,
                "daysPerYear", 15,
                "carryForward", true,
                "maxCarryForwardDays", 10,
                "minimumServiceMonths", 0,
                "genderEligibility", "ALL"
        ));
        mockSettings.setLeavePolicies(policies);

        // Employee was hired 2 years ago (so eligible for prev year calculations)
        mockEmployee.setHireDate(LocalDate.now().minusYears(2));

        int currentYear = LocalDate.now().getYear();
        int prevYear = currentYear - 1;

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        
        // Mock 5 days used in previous year (leaving 15 - 5 = 10 days unused)
        when(leaveRepository.getUsedLeaveDays(101L, LeaveType.EARNED, prevYear)).thenReturn(5.0);
        // Mock 0 days used in current year
        when(leaveRepository.getUsedLeaveDays(101L, LeaveType.EARNED, currentYear)).thenReturn(0.0);

        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(600L);
            return req;
        });

        // Requesting 20 days (which exceeds current year base of 15, but is within 15 + 10 = 25 total days with carry-over)
        // Set dates so total working days calculated = 20 (since we mock weekends/holidays calculation, let's use a 20-day range)
        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.EARNED)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(20)) // 20 days
                .reason("Long Earned leave with carry forward")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(600L, response.getId());
        assertEquals(LeaveStatus.PENDING, response.getStatus());
    }

    @Test
    void requestLeaveWithTenantSettings_shouldRejectIfPoliciesNotConfigured() {
        mockSettings.setPoliciesConfigured(false);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Vacation")
                .build();

        LeavePoliciesNotConfiguredException ex = assertThrows(LeavePoliciesNotConfiguredException.class, () ->
                leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee)
        );
        assertEquals("Leave policies have not been configured for your company yet. Please ask your administrator to configure leave settings first.", ex.getMessage());
    }

    @Test
    void requestLeaveWithTenantSettings_shouldBypassLimitForCompensatoryLeave() {
        Map<String, Object> policies = new HashMap<>();
        policies.put("COMPENSATORY", Map.of(
                "allowed", true,
                "daysPerYear", 0,
                "carryForward", false,
                "genderEligibility", "ALL"
        ));
        mockSettings.setLeavePolicies(policies);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(700L);
            return req;
        });

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.COMPENSATORY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Compensatory leave")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(700L, response.getId());
        assertEquals(LeaveStatus.PENDING, response.getStatus());
        // Verify balance check is bypassed (hasLimit returns false)
        verify(leaveRepository, never()).getUsedLeaveDays(eq(101L), eq(LeaveType.COMPENSATORY), anyInt());
    }

    @Test
    void requestLeaveWithTenantSettings_shouldCalculateRecursiveCarryForward() {
        // Policy allows carry forward up to 10 days
        Map<String, Object> policies = new HashMap<>();
        policies.put("EARNED", Map.of(
                "allowed", true,
                "daysPerYear", 15.0,
                "carryForward", true,
                "maxCarryForwardDays", 10.0,
                "minimumServiceMonths", 0,
                "genderEligibility", "ALL"
        ));
        mockSettings.setLeavePolicies(policies);

        // Employee hired in currentYear - 3
        int currentYear = LocalDate.now().getYear();
        int yearMinus1 = currentYear - 1;
        int yearMinus2 = currentYear - 2;
        int yearMinus3 = currentYear - 3;
        mockEmployee.setHireDate(LocalDate.now().withYear(yearMinus3));

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);

        // Stub used leave days for previous years:
        // yearMinus3 (hire year): base is 15. Employee used 5. Remaining is 10. Carried over to yearMinus2 = min(10, 10) = 10.
        // yearMinus2: base is 15 + 10 = 25. Employee used 18. Remaining is 7. Carried over to yearMinus1 = min(7, 10) = 7.
        // yearMinus1: base is 15 + 7 = 22. Employee used 14. Remaining is 8. Carried over to currentYear = min(8, 10) = 8.
        // currentYear: base is 15 + 8 = 23.
        when(leaveRepository.getUsedLeaveDays(101L, LeaveType.EARNED, yearMinus3)).thenReturn(5.0);
        when(leaveRepository.getUsedLeaveDays(101L, LeaveType.EARNED, yearMinus2)).thenReturn(18.0);
        when(leaveRepository.getUsedLeaveDays(101L, LeaveType.EARNED, yearMinus1)).thenReturn(14.0);
        when(leaveRepository.getUsedLeaveDays(101L, LeaveType.EARNED, currentYear)).thenReturn(0.0);

        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(800L);
            return req;
        });

        // Requesting 23 days for currentYear (which is exactly our calculated available balance of 23)
        // Let's set start and end date to match 23 working days.
        mockSettings.setCountWeekendsAsLeave(true);
        mockSettings.setCountHolidaysAsLeave(true);

        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusDays(22); // 23 days total inclusive

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.EARNED)
                .startDate(startDate)
                .endDate(endDate)
                .reason("Multi-year carry forward leave request")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(800L, response.getId());
        assertEquals(LeaveStatus.PENDING, response.getStatus());
    }
}

