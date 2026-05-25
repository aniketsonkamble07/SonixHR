package com.sonixhr.controller.employee;

import com.sonixhr.dto.employee.DepartmentStat;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.service.employee.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tenants/{tenantId}/employees")
@RequiredArgsConstructor
@Tag(name = "Employee Management", description = "APIs for managing employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    // =====================================================
    // CREATE EMPLOYEE
    // =====================================================

    @PostMapping
    @Operation(summary = "Create a new employee", description = "Creates a new employee for the specified tenant")
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee details", required = true)
            @Valid @RequestBody EmployeeCreateRequest request) {

        log.info("REST request to create employee for tenant: {}", tenantId);
        EmployeeResponse response = employeeService.createEmployee(tenantId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // =====================================================
    // GET EMPLOYEE BY ID
    // =====================================================

    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID", description = "Retrieves employee details by ID")
    public ResponseEntity<EmployeeResponse> getEmployeeById(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id) {

        log.info("REST request to get employee with id: {} for tenant: {}", id, tenantId);
        EmployeeResponse response = employeeService.getEmployeeById(id, tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEE BY CODE
    // =====================================================

    @GetMapping("/code/{employeeCode}")
    @Operation(summary = "Get employee by code", description = "Retrieves employee details by employee code")
    public ResponseEntity<EmployeeResponse> getEmployeeByCode(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee Code", required = true)
            @PathVariable String employeeCode) {

        log.info("REST request to get employee with code: {} for tenant: {}", employeeCode, tenantId);
        EmployeeResponse response = employeeService.getEmployeeByCode(tenantId, employeeCode);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEE BY EMAIL
    // =====================================================

    @GetMapping("/email/{email}")
    @Operation(summary = "Get employee by email", description = "Retrieves employee details by email")
    public ResponseEntity<EmployeeResponse> getEmployeeByEmail(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Email address", required = true)
            @PathVariable String email) {

        log.info("REST request to get employee with email: {} for tenant: {}", email, tenantId);
        EmployeeResponse response = employeeService.getEmployeeByEmail(tenantId, email);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ALL EMPLOYEES (PAGINATED)
    // =====================================================

    @GetMapping
    @Operation(summary = "Get all employees", description = "Retrieves all employees for the tenant with pagination")
    public ResponseEntity<Page<EmployeeResponse>> getAllEmployees(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("REST request to get all employees for tenant: {}", tenantId);
        Page<EmployeeResponse> response = employeeService.getAllEmployees(tenantId, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEES BY STATUS
    // =====================================================

    @GetMapping("/status/{status}")
    @Operation(summary = "Get employees by status", description = "Retrieves employees filtered by status")
    public ResponseEntity<Page<EmployeeResponse>> getEmployeesByStatus(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee Status", required = true)
            @PathVariable EmployeeStatus status,

            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("REST request to get employees with status: {} for tenant: {}", status, tenantId);
        Page<EmployeeResponse> response = employeeService.getEmployeesByStatus(tenantId, status, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEES BY DEPARTMENT
    // =====================================================

    @GetMapping("/department/name/{departmentName}")
    @Operation(summary = "Get employees by department name", description = "Retrieves employees filtered by department name")
    public ResponseEntity<List<EmployeeResponse>> getEmployeesByDepartmentName(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Department name", required = true)
            @PathVariable String departmentName) {

        log.info("REST request to get employees in department: {} for tenant: {}", departmentName, tenantId);
        List<EmployeeResponse> response = employeeService.getEmployeesByDepartmentName(tenantId, departmentName);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET TEAM MEMBERS
    // =====================================================

    @GetMapping("/managers/{managerId}/team")
    @Operation(summary = "Get team members", description = "Retrieves all employees reporting to a manager")
    public ResponseEntity<Page<EmployeeResponse>> getTeamMembers(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Manager ID", required = true)
            @PathVariable Long managerId,

            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("REST request to get team members for manager: {} in tenant: {}", managerId, tenantId);
        Page<EmployeeResponse> response = employeeService.getTeamMembers(tenantId, managerId, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ORGANIZATION CHART
    // =====================================================

    @GetMapping("/organization-chart")
    @Operation(summary = "Get organization chart", description = "Retrieves the complete organization hierarchy")
    public ResponseEntity<List<EmployeeResponse>> getOrganizationChart(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId) {

        log.info("REST request to get organization chart for tenant: {}", tenantId);
        List<EmployeeResponse> response = employeeService.getOrganizationChart(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPDATE EMPLOYEE
    // =====================================================

    @PutMapping("/{id}")
    @Operation(summary = "Update employee", description = "Updates an existing employee")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "Updated employee details", required = true)
            @Valid @RequestBody EmployeeCreateRequest request) {

        log.info("REST request to update employee with id: {} for tenant: {}", id, tenantId);
        EmployeeResponse response = employeeService.updateEmployee(id, tenantId, request);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPDATE EMPLOYEE STATUS
    // =====================================================

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update employee status", description = "Updates the status of an employee")
    public ResponseEntity<Void> updateEmployeeStatus(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "New status", required = true)
            @RequestParam EmployeeStatus status,

            @Parameter(description = "Reason for status change")
            @RequestParam(required = false) String reason) {

        log.info("REST request to update employee status for id: {} to: {} for tenant: {}", id, status, tenantId);
        employeeService.updateEmployeeStatus(id, tenantId, status, reason);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // CONFIRM EMPLOYEE (END PROBATION)
    // =====================================================

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm employee", description = "Confirms an employee after probation period")
    public ResponseEntity<Void> confirmEmployee(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id) {

        log.info("REST request to confirm employee with id: {} for tenant: {}", id, tenantId);
        employeeService.confirmEmployee(id, tenantId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // SEARCH EMPLOYEES
    // =====================================================

    @GetMapping("/search")
    @Operation(summary = "Search employees", description = "Searches employees by name, email, code, etc.")
    public ResponseEntity<Page<EmployeeResponse>> searchEmployees(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Search term", required = true)
            @RequestParam String searchTerm,

            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("REST request to search employees with term: {} for tenant: {}", searchTerm, tenantId);
        Page<EmployeeResponse> response = employeeService.searchEmployees(tenantId, searchTerm, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // DELETE EMPLOYEE (SOFT DELETE)
    // =====================================================

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete employee", description = "Soft deletes an employee")
    public ResponseEntity<Void> deleteEmployee(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id) {

        log.info("REST request to delete employee with id: {} for tenant: {}", id, tenantId);
        employeeService.deleteEmployee(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // GET DEPARTMENT STATISTICS
    // =====================================================

    @GetMapping("/statistics/departments")
    @Operation(summary = "Get department statistics", description = "Retrieves employee count by department")
    public ResponseEntity<List<DepartmentStat>> getDepartmentStatistics(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId) {

        log.info("REST request to get department statistics for tenant: {}", tenantId);
        List<DepartmentStat> response = employeeService.getDepartmentStatistics(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // BULK UPDATE MANAGER
    // =====================================================

    @PatchMapping("/bulk/manager")
    @Operation(summary = "Bulk update manager", description = "Updates manager for multiple employees")
    public ResponseEntity<Void> bulkUpdateManager(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "List of employee IDs", required = true)
            @RequestParam List<Long> employeeIds,

            @Parameter(description = "New manager ID", required = true)
            @RequestParam Long managerId) {

        log.info("REST request to bulk update manager for {} employees", employeeIds.size());
        employeeService.bulkUpdateManager(tenantId, employeeIds, managerId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // BULK UPDATE STATUS
    // =====================================================

    @PatchMapping("/bulk/status")
    @Operation(summary = "Bulk update status", description = "Updates status for multiple employees")
    public ResponseEntity<Void> bulkUpdateStatus(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "List of employee IDs", required = true)
            @RequestParam List<Long> employeeIds,

            @Parameter(description = "New status", required = true)
            @RequestParam EmployeeStatus status) {

        log.info("REST request to bulk update status for {} employees to {}", employeeIds.size(), status);
        employeeService.bulkUpdateStatus(tenantId, employeeIds, status);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // GET UPCOMING BIRTHDAYS
    // =====================================================

    @GetMapping("/upcoming/birthdays")
    @Operation(summary = "Get upcoming birthdays", description = "Retrieves employees with birthdays in next N days")
    public ResponseEntity<List<EmployeeResponse>> getUpcomingBirthdays(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Number of days to look ahead", example = "30")
            @RequestParam(defaultValue = "30") int days) {

        log.info("REST request to get upcoming birthdays for tenant: {} in next {} days", tenantId, days);
        List<EmployeeResponse> response = employeeService.getUpcomingBirthdays(tenantId, days);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET UPCOMING ANNIVERSARIES
    // =====================================================

    @GetMapping("/upcoming/anniversaries")
    @Operation(summary = "Get upcoming work anniversaries", description = "Retrieves employees with work anniversaries in next N days")
    public ResponseEntity<List<EmployeeResponse>> getUpcomingAnniversaries(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable UUID tenantId,

            @Parameter(description = "Number of days to look ahead", example = "30")
            @RequestParam(defaultValue = "30") int days) {

        log.info("REST request to get upcoming anniversaries for tenant: {} in next {} days", tenantId, days);
        List<EmployeeResponse> response = employeeService.getUpcomingAnniversaries(tenantId, days);
        return ResponseEntity.ok(response);
    }
}