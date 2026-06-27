package com.sonixhr.controller.employee;

import com.sonixhr.dto.employee.EmployeeCompensationRequest;
import com.sonixhr.dto.employee.EmployeeCompensationResponse;
import com.sonixhr.dto.employee.EmployeeCompensationPeriodResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.service.employee.EmployeeCompensationService;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/employees/{id}/compensation")
@RequiredArgsConstructor
@Tag(name = "Employee Compensation Management", description = "APIs for managing employee salary profiles and bank details")
public class EmployeeCompensationController {

    private final EmployeeCompensationService employeeCompensationService;

    @PutMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    @Operation(summary = "Update or register employee compensation", 
               description = "Registers or updates the employee salary profile, overrides, and bank details")
    public ResponseEntity<EmployeeCompensationResponse> updateCompensation(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody EmployeeCompensationRequest request,
            @AuthenticationPrincipal Employee currentEmployee) {
        
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update compensation for employee: {} in tenant: {} by {}", id, tenantId, currentEmployee.getEmail());
        EmployeeCompensationResponse response = employeeCompensationService.updateCompensation(tenantId, id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM') or #id == principal.id")
    @Operation(summary = "Get employee compensation details", 
               description = "Retrieves active salary profile, components overrides, bank details, and history for an employee")
    public ResponseEntity<EmployeeCompensationResponse> getCompensation(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get compensation for employee: {} in tenant: {}", id, tenantId);
        EmployeeCompensationResponse response = employeeCompensationService.getCompensation(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/period")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM') or #id == principal.id")
    @Operation(summary = "Get employee active salary profiles for a specific time period", 
               description = "Retrieves salary profiles and components overrides active during a given start-to-end date range")
    public ResponseEntity<EmployeeCompensationPeriodResponse> getCompensationForPeriod(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get compensation period for employee: {} in tenant: {} between {} and {}", id, tenantId, startDate, endDate);
        EmployeeCompensationPeriodResponse response = 
                employeeCompensationService.getCompensationForPeriod(tenantId, id, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
