package com.sonixhr.controller.payroll;

import com.sonixhr.dto.payroll.PayslipResponse;
import com.sonixhr.dto.payroll.PayslipSummaryResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.Payrun;
import com.sonixhr.service.payroll.PayslipService;
import com.sonixhr.service.payroll.PayrollCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Payslip / Salary Slip Management", description = "APIs for viewing organization and individual salary slips")
public class PayslipController {

    private final PayslipService payslipService;
    private final PayrollCalculationService payrollCalculationService;

    @PostMapping("/api/payroll/payruns")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Execute payrun for the tenant", description = "Executes payroll calculations and persists payslips for all active employees.")
    public ResponseEntity<PayrunResponseDto> executePayrun(
            @RequestBody PayrunRequestDto request,
            @AuthenticationPrincipal Employee currentEmployee) {
        
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to execute payrun for tenant {} for {}/{}", tenantId, request.getMonth(), request.getYear());
        
        Payrun payrun = payrollCalculationService.processPayrun(
                tenantId, 
                request.getMonth(), 
                request.getYear()
        );
        
        return ResponseEntity.ok(new PayrunResponseDto(payrun.getId(), payrun.getStatus(), payrun.getProcessedAt().toString()));
    }

    @Data
    public static class PayrunRequestDto {
        private int month;
        private int year;
    }

    @Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PayrunResponseDto {
        private UUID id;
        private String status;
        private String processedAt;
    }


    @GetMapping("/api/payroll/payslips/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get detailed payslip by id", description = "Retrieves itemized earnings and deductions breakdown for a specific payslip.")
    public ResponseEntity<PayslipResponse> getPayslip(
            @Parameter(description = "Payslip ID", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal Employee currentEmployee) {
        
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get payslip {} by employee {}", id, currentEmployee.getId());
        PayslipResponse response = payslipService.getPayslip(tenantId, id, currentEmployee);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/employees/{employeeId}/payslips")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM') or #employeeId == principal.id")
    @Operation(summary = "Get employee payslips history", description = "Retrieves history of all generated payslips for a given employee.")
    public ResponseEntity<List<PayslipSummaryResponse>> getEmployeePayslips(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long employeeId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get payslip history for employee {} by {}", employeeId, currentEmployee.getId());
        List<PayslipSummaryResponse> response = payslipService.getEmployeePayslips(tenantId, employeeId, currentEmployee);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/payroll/payslips/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get currently authenticated employee payslips", description = "Retrieves history of all generated payslips for the current user.")
    public ResponseEntity<List<PayslipSummaryResponse>> getMyPayslips(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();
        log.info("REST request to get own payslip history for employee {}", employeeId);
        List<PayslipSummaryResponse> response = payslipService.getEmployeePayslips(tenantId, employeeId, currentEmployee);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/payroll/payslips")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM', 'SETTINGS_VIEW')")
    @Operation(summary = "Get tenant payslips for a specific payrun month and year", 
               description = "Retrieves all payslips generated for a tenant's payrun.")
    public ResponseEntity<List<PayslipSummaryResponse>> getTenantPayslips(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get payslips for tenant {} in {}/{} by {}", tenantId, month, year, currentEmployee.getId());
        List<PayslipSummaryResponse> response = payslipService.getTenantPayslips(tenantId, month, year, currentEmployee);
        return ResponseEntity.ok(response);
    }
}
