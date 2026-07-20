package com.sonixhr.service.employee;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EmployeeServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private EmployeeService employeeService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        employeeService = new EmployeeService(
                employeeRepository,
                mock(com.sonixhr.repository.tenant.TenantRepository.class),
                mock(EmployeeCodeGenerator.class),
                mock(com.sonixhr.service.EmailService.class),
                mock(com.sonixhr.service.ActivationTokenService.class),
                mock(com.sonixhr.repository.department.DepartmentRepository.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(com.sonixhr.repository.tenant.TenantRoleRepository.class),
                mock(com.sonixhr.repository.attendance.ShiftConfigurationRepository.class),
                mock(com.sonixhr.service.common.AuditLogService.class),
                mock(com.sonixhr.repository.payroll.EmployeeSalaryProfileRepository.class),
                eventPublisher
        );
    }

    @Test
    public void testProcessOffboardedEmployees() {
        Employee employee = new Employee();
        employee.setId(100L);
        employee.setEmail("test@company.com");
        employee.setActive(true);
        employee.setLastWorkingDate(LocalDate.now().minusDays(1));

        when(employeeRepository.findActiveEmployeesWithExpiredLastWorkingDate(any(LocalDate.class)))
                .thenReturn(Collections.singletonList(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        employeeService.processOffboardedEmployees(1L);

        assertFalse(employee.isActive());
        verify(employeeRepository, times(1)).save(employee);
        verify(eventPublisher, times(1)).publishEvent(any(com.sonixhr.events.EmployeeUpdatedEvent.class));
    }

    @Test
    public void testResignationFlow_SubmitAcceptAndDeactivate() {
        // 1. Setup Employee
        com.sonixhr.entity.tenant.Tenant tenant = new com.sonixhr.entity.tenant.Tenant();
        tenant.setId(1L);

        Employee employee = new Employee();
        employee.setId(100L);
        employee.setTenant(tenant);
        employee.setEmail("emp@tenant.com");
        employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE);
        employee.setActive(true);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 2. Submit Resignation
        employeeService.submitResignation(100L, 1L, "Relocating", LocalDate.now().plusDays(30));
        assertEquals(com.sonixhr.enums.employee.EmployeeStatus.RESIGNED, employee.getStatus());
        assertEquals(com.sonixhr.enums.employee.ResignationStatus.SUBMITTED, employee.getResignationStatus());
        assertEquals("Relocating", employee.getResignationReason());
        assertTrue(employee.isActive());

        // 3. Accept Resignation
        LocalDate approvedLWD = LocalDate.now().plusDays(30);
        employeeService.acceptResignation(100L, 1L, approvedLWD);
        assertEquals(com.sonixhr.enums.employee.ResignationStatus.APPROVED, employee.getResignationStatus());
        assertEquals(approvedLWD, employee.getApprovedLastWorkingDate());
        assertEquals(approvedLWD, employee.getLastWorkingDate());
        assertTrue(employee.isActive());

        // 4. Scheduler Deactivation (simulate day passing)
        employee.setLastWorkingDate(LocalDate.now().minusDays(1)); // set back so scheduler picks it up
        when(employeeRepository.findActiveEmployeesWithExpiredLastWorkingDate(any(LocalDate.class)))
                .thenReturn(Collections.singletonList(employee));

        employeeService.deactivateOffboardedEmployees();
        assertFalse(employee.isActive());
        verify(eventPublisher, atLeastOnce()).publishEvent(any(com.sonixhr.events.EmployeeUpdatedEvent.class));
    }

    @Test
    public void testResignationFlow_RejectAndWithdraw() {
        // Setup
        com.sonixhr.entity.tenant.Tenant tenant = new com.sonixhr.entity.tenant.Tenant();
        tenant.setId(1L);

        Employee employee = new Employee();
        employee.setId(101L);
        employee.setTenant(tenant);
        employee.setEmail("emp2@tenant.com");
        employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE);
        employee.setActive(true);

        when(employeeRepository.findById(101L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Submit
        employeeService.submitResignation(101L, 1L, "Career growth", LocalDate.now().plusDays(15));

        // Reject
        employeeService.rejectResignation(101L, 1L);
        assertEquals(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE, employee.getStatus());
        assertEquals(com.sonixhr.enums.employee.ResignationStatus.REJECTED, employee.getResignationStatus());
        assertNull(employee.getResignationReason());

        // Submit again
        employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE);
        employeeService.submitResignation(101L, 1L, "New opportunity", LocalDate.now().plusDays(15));

        // Withdraw
        employeeService.withdrawResignation(101L, 1L);
        assertEquals(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE, employee.getStatus());
        assertEquals(com.sonixhr.enums.employee.ResignationStatus.WITHDRAWN, employee.getResignationStatus());
    }
}

