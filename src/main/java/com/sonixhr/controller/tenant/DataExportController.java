package com.sonixhr.controller.tenant;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.Payslip;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.PayslipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DataExportController {

    private final EmployeeRepository employeeRepository;
    private final PayslipRepository payslipRepository;

    @GetMapping({"/api/export/employees", "/api/employees/export"})
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN') or @permissionEvaluator.hasPermission(authentication, 'EMPLOYEE_EXPORT')")
    public ResponseEntity<byte[]> exportEmployees(@AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to export employees for tenant ID: {}", currentEmployee.getTenantId());
        Long tenantId = currentEmployee.getTenantId();

        List<Employee> employees = employeeRepository.findByTenant_Id(tenantId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Employee Code,First Name,Last Name,Email,Status,Department,Manager,Hire Date\n");

        for (Employee emp : employees) {
            csv.append(emp.getId()).append(",")
               .append(escapeCsv(emp.getEmployeeCode())).append(",")
               .append(escapeCsv(emp.getFirstName())).append(",")
               .append(escapeCsv(emp.getLastName())).append(",")
               .append(escapeCsv(emp.getEmail())).append(",")
               .append(emp.getStatus() != null ? emp.getStatus().name() : "").append(",")
               .append(emp.getDepartment() != null ? escapeCsv(emp.getDepartment().getName()) : "").append(",")
               .append(emp.getManager() != null ? escapeCsv(emp.getManager().getEmail()) : "").append(",")
               .append(emp.getHireDate() != null ? emp.getHireDate().toString() : "")
               .append("\n");
        }

        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employees-export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/api/export/payroll")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN') or @permissionEvaluator.hasPermission(authentication, 'REPORT_EXPORT')")
    public ResponseEntity<byte[]> exportPayroll(@AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to export payroll for tenant ID: {}", currentEmployee.getTenantId());
        Long tenantId = currentEmployee.getTenantId();

        List<Payslip> payslips = payslipRepository.findByTenant_Id(tenantId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Employee Code,Employee Email,Payrun ID,Gross Earnings,Total Deductions,Net Pay,Wages Base\n");

        for (Payslip slip : payslips) {
            csv.append(slip.getId()).append(",")
               .append(slip.getEmployee() != null ? escapeCsv(slip.getEmployee().getEmployeeCode()) : "").append(",")
               .append(slip.getEmployee() != null ? escapeCsv(slip.getEmployee().getEmail()) : "").append(",")
               .append(slip.getPayrunId()).append(",")
               .append(slip.getGrossEarnings()).append(",")
               .append(slip.getTotalDeductions()).append(",")
               .append(slip.getNetPay()).append(",")
               .append(slip.getWagesBase())
               .append("\n");
        }

        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payroll-export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
