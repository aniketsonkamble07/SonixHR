package com.sonixhr.controller.tenant;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.Payslip;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.PayslipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DataExportControllerTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private PayslipRepository payslipRepository;

    private DataExportController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new DataExportController(employeeRepository, payslipRepository);
    }

    @Test
    public void testExportEmployees_Success() {
        Employee currentEmployee = new Employee();
        currentEmployee.setTenantId(10L);

        Employee emp1 = new Employee();
        emp1.setId(101L);
        emp1.setEmployeeCode("EMP101");
        emp1.setFirstName("John");
        emp1.setLastName("Doe, Jr.");
        emp1.setEmail("john.doe@company.com");
        emp1.setHireDate(LocalDate.of(2025, 1, 15));

        when(employeeRepository.findByTenant_Id(10L)).thenReturn(List.of(emp1));

        ResponseEntity<byte[]> response = controller.exportEmployees(currentEmployee);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("employees-export.csv"));

        String csvContent = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("ID,Employee Code,First Name,Last Name,Email,Status,Department,Manager,Hire Date"));
        assertTrue(csvContent.contains("101,EMP101,John,\"Doe, Jr.\",john.doe@company.com"));
        assertTrue(csvContent.contains("2025-01-15"));
    }

    @Test
    public void testExportPayroll_Success() {
        Employee currentEmployee = new Employee();
        currentEmployee.setTenantId(10L);

        Employee emp = new Employee();
        emp.setEmployeeCode("EMP101");
        emp.setEmail("john.doe@company.com");

        Payslip slip = new Payslip();
        slip.setId(UUID.randomUUID());
        slip.setEmployee(emp);
        slip.setPayrunId(UUID.randomUUID());
        slip.setGrossEarnings(BigDecimal.valueOf(5000));
        slip.setTotalDeductions(BigDecimal.valueOf(1000));
        slip.setNetPay(BigDecimal.valueOf(4000));
        slip.setWagesBase(BigDecimal.valueOf(4500));

        when(payslipRepository.findByTenant_Id(10L)).thenReturn(List.of(slip));

        ResponseEntity<byte[]> response = controller.exportPayroll(currentEmployee);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("payroll-export.csv"));

        String csvContent = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("ID,Employee Code,Employee Email,Payrun ID,Gross Earnings,Total Deductions,Net Pay,Wages Base"));
        assertTrue(csvContent.contains("EMP101,john.doe@company.com"));
        assertTrue(csvContent.contains("5000,1000,4000,4500"));
    }
}
