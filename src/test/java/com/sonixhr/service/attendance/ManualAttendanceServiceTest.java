package com.sonixhr.service.attendance;

import com.sonixhr.dto.attendance.ManualTeamMemberAttendanceDTO;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.attendance.AttendanceStatus;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.leave.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ManualAttendanceServiceTest {

    @Mock
    private ManualAttendanceRepository attendanceRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    private ManualAttendanceService attendanceService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        attendanceService = new ManualAttendanceService(
                attendanceRepository,
                employeeRepository,
                leaveRequestRepository);
    }

    @Test
    public void testGetTeamWithTodayAttendance_IncludesLeaves() {
        Long managerId = 1L;
        Long tenantId = 10L;
        LocalDate testDate = LocalDate.of(2026, 7, 16);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        Employee manager = new Employee();
        manager.setId(managerId);
        manager.setTenant(tenant);

        Employee emp1 = new Employee();
        emp1.setId(101L);
        emp1.setFirstName("Alice");
        emp1.setLastName("Smith");

        Employee emp2 = new Employee();
        emp2.setId(102L);
        emp2.setFirstName("Bob");
        emp2.setLastName("Jones");

        when(employeeRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(employeeRepository.findByManagerIdAndTenantId(managerId, tenantId)).thenReturn(List.of(emp1, emp2));

        // Stub attendance: Alice marked PRESENT
        AttendanceRecord aliceRecord = new AttendanceRecord();
        aliceRecord.setEmployee(emp1);
        aliceRecord.setTenant(tenant);
        aliceRecord.setStatus(AttendanceStatus.PRESENT);
        aliceRecord.setAttendanceDate(testDate);
        aliceRecord.setMarkedBy(100L);
        aliceRecord.setMarkedByName("John Doe");
        aliceRecord.setMarkedByRole("MANAGER");

        when(attendanceRepository.findByTenantIdAndEmployeeIdInAndAttendanceDateBetween(
                any(), any(), any(), any())).thenReturn(List.of(aliceRecord));

        // Stub leave: Bob on approved leave
        LeaveRequest bobLeave = new LeaveRequest();
        bobLeave.setEmployee(emp2);
        bobLeave.setTenant(tenant);
        bobLeave.setStartDate(testDate);
        bobLeave.setEndDate(testDate);

        when(leaveRequestRepository.findAllApprovedLeavesInDateRange(
                any(), any(), any())).thenReturn(List.of(bobLeave));

        // Call service
        List<ManualTeamMemberAttendanceDTO> result = attendanceService.getTeamWithTodayAttendance(managerId, testDate);

        assertEquals(2, result.size());

        // Verify Alice: status PRESENT, marked = true
        ManualTeamMemberAttendanceDTO aliceDTO = result.stream().filter(d -> d.getEmployeeId().equals(101L)).findFirst()
                .orElseThrow();
        assertEquals(AttendanceStatus.PRESENT, aliceDTO.getTodayStatus());
        assertEquals(AttendanceStatus.PRESENT, aliceDTO.getStatus());
        assertTrue(aliceDTO.isMarked());
        assertEquals(100L, aliceDTO.getMarkedBy());
        assertEquals("John Doe", aliceDTO.getMarkedByName());

        // Verify Bob: status ON_LEAVE, reason "Approved Leave", marked = false
        ManualTeamMemberAttendanceDTO bobDTO = result.stream().filter(d -> d.getEmployeeId().equals(102L)).findFirst()
                .orElseThrow();
        assertEquals(AttendanceStatus.ON_LEAVE, bobDTO.getTodayStatus());
        assertEquals(AttendanceStatus.ON_LEAVE, bobDTO.getStatus());
        assertEquals("Approved Leave", bobDTO.getReason());
        assertFalse(bobDTO.isMarked());
    }
}
