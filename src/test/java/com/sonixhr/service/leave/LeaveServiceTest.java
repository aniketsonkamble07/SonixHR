package com.sonixhr.service.leave;

import com.sonixhr.dto.leave.LeavePolicyDTO;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
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

        mockSettings.setLeavePolicies(convertPolicies(policies));

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        when(leaveRepository.getUsedLeaveDaysByType(eq(101L), anyInt())).thenReturn(java.util.Collections.emptyList());

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
        mockSettings.setLeavePolicies(convertPolicies(policies));


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
        mockSettings.setLeavePolicies(convertPolicies(policies));


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
        mockSettings.setLeavePolicies(convertPolicies(policies));


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
        mockSettings.setLeavePolicies(convertPolicies(policies));


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
        mockSettings.setLeavePolicies(convertPolicies(policies));


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
        mockSettings.setLeavePolicies(convertPolicies(policies));


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
        mockSettings.setLeavePolicies(convertPolicies(policies));


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

    @Test
    void approveLeave_shouldRejectIfSelfApproval() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(999L);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setEmployee(mockEmployee);
        leave.setTenant(mockEmployee.getTenant());

        when(leaveRepository.findById(999L)).thenReturn(Optional.of(leave));
        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                leaveService.approveLeave(999L, 101L, "John Doe")
        );
        assertTrue(ex.getMessage().contains("You cannot approve your own leave request"));
    }

    @Test
    void rejectLeave_shouldRejectIfSelfRejection() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(999L);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setEmployee(mockEmployee);
        leave.setTenant(mockEmployee.getTenant());

        when(leaveRepository.findById(999L)).thenReturn(Optional.of(leave));
        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                leaveService.rejectLeave(999L, "Not needed", 101L, "John Doe")
        );
        assertTrue(ex.getMessage().contains("You cannot reject your own leave request"));
    }

    @Test
    void requestLeaveWithTenantSettings_shouldExcludeWeekends_whenCountWeekendsAsLeaveIsFalse() {
        // Set countWeekendsAsLeave to false
        mockSettings.setCountWeekendsAsLeave(false);
        mockSettings.setCountHolidaysAsLeave(false);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        when(leaveRepository.getUsedLeaveDays(eq(101L), eq(LeaveType.CASUAL), anyInt())).thenReturn(0.0);

        // Define Saturday & Sunday. 2026-06-20 is Saturday, 2026-06-21 is Sunday.
        LocalDate sat = LocalDate.of(2026, 6, 20);
        LocalDate sun = LocalDate.of(2026, 6, 21);
        LocalDate mon = LocalDate.of(2026, 6, 22);

        when(leaveConfigService.isWeekendForEmployee(sat, mockEmployee, mockSettings)).thenReturn(true);
        when(leaveConfigService.isWeekendForEmployee(sun, mockEmployee, mockSettings)).thenReturn(true);
        when(leaveConfigService.isWeekendForEmployee(mon, mockEmployee, mockSettings)).thenReturn(false);

        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(1001L);
            return req;
        });

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(sat)
                .endDate(mon) // Saturday, Sunday, Monday
                .reason("Weekend test")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(1.0, response.getTotalDays()); // Only Monday is counted
    }

    @Test
    void requestLeaveWithTenantSettings_shouldIncludeWeekends_whenCountWeekendsAsLeaveIsTrue() {
        // Set countWeekendsAsLeave to true
        mockSettings.setCountWeekendsAsLeave(true);
        mockSettings.setCountHolidaysAsLeave(false);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        when(leaveRepository.getUsedLeaveDays(eq(101L), eq(LeaveType.CASUAL), anyInt())).thenReturn(0.0);

        LocalDate sat = LocalDate.of(2026, 6, 20);
        LocalDate sun = LocalDate.of(2026, 6, 21);
        LocalDate mon = LocalDate.of(2026, 6, 22);

        when(leaveConfigService.isWeekendForEmployee(sat, mockEmployee, mockSettings)).thenReturn(true);
        when(leaveConfigService.isWeekendForEmployee(sun, mockEmployee, mockSettings)).thenReturn(true);
        when(leaveConfigService.isWeekendForEmployee(mon, mockEmployee, mockSettings)).thenReturn(false);

        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(1002L);
            return req;
        });

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(sat)
                .endDate(mon)
                .reason("Weekend test")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(3.0, response.getTotalDays()); // Saturday, Sunday, Monday are all counted
    }

    @Test
    void requestLeaveWithTenantSettings_shouldReject_whenExceedsMaxConsecutiveLeaveDays() {
        mockSettings.setMaxConsecutiveLeaveDays(5);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(10)) // 10 days
                .reason("Long leave")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee)
        );
        assertTrue(ex.getMessage().contains("Cannot request more than 5 consecutive leave days"));
    }

    @Test
    void requestLeaveWithTenantSettings_shouldAutoApprove_whenLeaveApprovalRequiredIsFalse() {
        mockSettings.setLeaveApprovalRequired(false);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        when(leaveRepository.getUsedLeaveDays(eq(101L), eq(LeaveType.CASUAL), anyInt())).thenReturn(0.0);
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(1003L);
            return req;
        });

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .reason("No approval needed")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(LeaveStatus.APPROVED, response.getStatus());
        assertEquals(101L, response.getApprovedBy());
    }

    @Test
    void requestLeaveWithTenantSettings_shouldAutoApprove_whenRequesterIsManagerAndAutoApproveEnabled() {
        mockSettings.setLeaveApprovalRequired(true);
        mockSettings.setAutoApproveForManager(true);

        // Employee is a manager. Let's mock a manager.
        com.sonixhr.entity.tenant.TenantRole managerRole = new com.sonixhr.entity.tenant.TenantRole();
        managerRole.setName("MANAGER");
        mockEmployee.setRoles(Set.of(managerRole));

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(mockEmployee));
        when(leaveConfigService.getTenantSettings(1L)).thenReturn(mockSettings);
        when(leaveRepository.getUsedLeaveDays(eq(101L), eq(LeaveType.CASUAL), anyInt())).thenReturn(0.0);
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(1004L);
            return req;
        });

        LeaveRequestDTO request = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .reason("Manager auto approve")
                .build();

        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(101L, request, mockEmployee);

        assertNotNull(response);
        assertEquals(LeaveStatus.APPROVED, response.getStatus());
        assertEquals(101L, response.getApprovedBy());
    }

    @Test
    void approveLeave_shouldApprove_whenApproverIsDepartmentManager() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(999L);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setEmployee(mockEmployee);
        leave.setTenant(mockEmployee.getTenant());
        leave.setStartDate(LocalDate.now().plusDays(1));
        leave.setEndDate(LocalDate.now().plusDays(2));
        leave.setLeaveType(LeaveType.CASUAL);

        com.sonixhr.entity.department.Department dept = new com.sonixhr.entity.department.Department();
        dept.setId(5L);
        mockEmployee.setDepartment(dept);

        Employee approver = new Employee();
        approver.setId(202L);
        approver.setFirstName("Jane");
        approver.setLastName("Manager");
        approver.setTenant(mockEmployee.getTenant());
        approver.setDepartment(dept);

        // Approver has LEAVE_APPROVE_DEPARTMENT permission
        com.sonixhr.entity.tenant.TenantRole deptApproverRole = new com.sonixhr.entity.tenant.TenantRole();
        com.sonixhr.entity.tenant.TenantPermission perm = new com.sonixhr.entity.tenant.TenantPermission();
        perm.setPermission("LEAVE_APPROVE_DEPARTMENT");
        deptApproverRole.setPermissions(Set.of(perm));
        approver.setRoles(Set.of(deptApproverRole));

        when(leaveRepository.findById(999L)).thenReturn(Optional.of(leave));
        when(employeeRepository.findById(202L)).thenReturn(Optional.of(approver));
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(mockSettings));
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveResponseDTO response = leaveService.approveLeave(999L, 202L, "Jane Manager");

        assertNotNull(response);
        assertEquals(LeaveStatus.APPROVED, response.getStatus());
        assertEquals(202L, response.getApprovedBy());
    }

    @Test
    void approveLeave_shouldReject_whenApproverIsNotAuthorized() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(999L);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setEmployee(mockEmployee);
        leave.setTenant(mockEmployee.getTenant());
        leave.setStartDate(LocalDate.now().plusDays(1));
        leave.setEndDate(LocalDate.now().plusDays(2));
        leave.setLeaveType(LeaveType.CASUAL);

        com.sonixhr.entity.department.Department dept1 = new com.sonixhr.entity.department.Department();
        dept1.setId(5L);
        mockEmployee.setDepartment(dept1);

        com.sonixhr.entity.department.Department dept2 = new com.sonixhr.entity.department.Department();
        dept2.setId(6L);

        Employee approver = new Employee();
        approver.setId(202L);
        approver.setTenant(mockEmployee.getTenant());
        approver.setDepartment(dept2);

        // Approver only has LEAVE_APPROVE_DEPARTMENT but for a different department
        com.sonixhr.entity.tenant.TenantRole deptApproverRole = new com.sonixhr.entity.tenant.TenantRole();
        com.sonixhr.entity.tenant.TenantPermission perm = new com.sonixhr.entity.tenant.TenantPermission();
        perm.setPermission("LEAVE_APPROVE_DEPARTMENT");
        deptApproverRole.setPermissions(Set.of(perm));
        approver.setRoles(Set.of(deptApproverRole));

        when(leaveRepository.findById(999L)).thenReturn(Optional.of(leave));
        when(employeeRepository.findById(202L)).thenReturn(Optional.of(approver));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                leaveService.approveLeave(999L, 202L, "Jane Manager")
        );
        assertTrue(ex.getMessage().contains("You are not authorized to approve this leave request"));
    }

    private Map<String, LeavePolicyDTO> convertPolicies(Map<String, Object> rawPolicies) {
        Map<String, LeavePolicyDTO> typedPolicies = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawPolicies.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) entry.getValue();
                
                Integer daysPerYear = null;
                Object dpy = map.get("daysPerYear");
                if (dpy instanceof Number) {
                    daysPerYear = ((Number) dpy).intValue();
                }

                Integer maxCarryForwardDays = null;
                Object mcf = map.get("maxCarryForwardDays");
                if (mcf instanceof Number) {
                    maxCarryForwardDays = ((Number) mcf).intValue();
                }

                Integer minimumServiceMonths = null;
                Object msm = map.get("minimumServiceMonths");
                if (msm instanceof Number) {
                    minimumServiceMonths = ((Number) msm).intValue();
                }

                LeavePolicyDTO dto = LeavePolicyDTO.builder()
                        .allowed((Boolean) map.get("allowed"))
                        .daysPerYear(daysPerYear)
                        .carryForward((Boolean) map.get("carryForward"))
                        .maxCarryForwardDays(maxCarryForwardDays)
                        .minimumServiceMonths(minimumServiceMonths)
                        .genderEligibility((String) map.get("genderEligibility"))
                        .probationPeriodAllowed((Boolean) map.get("probationPeriodAllowed"))
                        .prorated((Boolean) map.get("prorated"))
                        .build();
                typedPolicies.put(entry.getKey(), dto);
            }
        }
        return typedPolicies;
    }
}


