package com.sonixhr.controller.employee;

import com.sonixhr.dto.employee.DepartmentStat;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeCreateResponse;
import com.sonixhr.dto.employee.EmployeeUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.employee.EmployeeSearchResponse;
import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import com.sonixhr.dto.employee.EmployeeDropdownDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.service.employee.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@SuppressWarnings("null")
@Tag(name = "Employee Management", description = "APIs for managing employees")
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;

    // =====================================================
    // CREATE EMPLOYEE
    // =====================================================

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_CREATE')")
    @Operation(summary = "Create a new employee", description = "Creates a new employee for the authenticated tenant")
    public ResponseEntity<EmployeeCreateResponse> createEmployee(
            @Valid @RequestBody EmployeeCreateRequest request,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("REST request to create employee for tenant: {} by employee: {}", tenantId, employeeId);
        EmployeeCreateResponse response = employeeService.createEmployee(tenantId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // =====================================================
    // GET EMPLOYEE BY ID
    // =====================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM') or #id == principal.id")
    @Operation(summary = "Get employee by ID", description = "Retrieves employee details by ID")
    public ResponseEntity<EmployeeResponse> getEmployeeById(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get employee with id: {} for tenant: {}", id, tenantId);
        EmployeeResponse response = employeeService.getEmployeeById(id, tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEE BY CODE
    // =====================================================

    @GetMapping("/code/{employeeCode}")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM') or #employeeCode == principal.employeeCode")
    @Operation(summary = "Get employee by code", description = "Retrieves employee details by employee code")
    public ResponseEntity<EmployeeResponse> getEmployeeByCode(
            @Parameter(description = "Employee Code", required = true)
            @PathVariable String employeeCode,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get employee with code: {} for tenant: {}", employeeCode, tenantId);
        EmployeeResponse response = employeeService.getEmployeeByCode(tenantId, employeeCode);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEE BY EMAIL
    // =====================================================

    @GetMapping("/email/{email}")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM') or #email == principal.email")
    @Operation(summary = "Get employee by email", description = "Retrieves employee details by email")
    public ResponseEntity<EmployeeResponse> getEmployeeByEmail(
            @Parameter(description = "Email address", required = true)
            @PathVariable String email,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get employee with email: {} for tenant: {}", email, tenantId);
        EmployeeResponse response = employeeService.getEmployeeByEmail(tenantId, email);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET CURRENT EMPLOYEE (ME)
    // =====================================================

    @GetMapping("/me")
    @Operation(summary = "Get current employee", description = "Retrieves the authenticated employee's details")
    public ResponseEntity<EmployeeResponse> getCurrentEmployee(
            @AuthenticationPrincipal Employee currentEmployee) {

        log.info("REST request to get current employee: {}", currentEmployee.getEmail());
        EmployeeResponse response = employeeService.getEmployeeById(currentEmployee.getId(), currentEmployee.getTenantId());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET LIGHTWEIGHT LIST FOR DROPDOWN
    // =====================================================

    @GetMapping("/dropdown")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM')")
    @Operation(summary = "Get active employees for dropdown", description = "Retrieves a lightweight list of all active employees for selection dropdowns")
    public ResponseEntity<List<EmployeeDropdownDTO>> getActiveEmployeesForDropdown(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get lightweight employee list for dropdown for tenant: {}", tenantId);
        List<EmployeeDropdownDTO> list = employeeService.getActiveEmployeesForDropdown(tenantId);
        return ResponseEntity.ok(list);
    }

    // =====================================================
    // GET ALL EMPLOYEES (PAGINATED)
    // =====================================================

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get all employees", description = "Retrieves all employees for the tenant with pagination")
    public ResponseEntity<Page<EmployeeSummaryResponse>> getAllEmployees(
            @AuthenticationPrincipal Employee currentEmployee,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get all employees for tenant: {}", tenantId);
        Page<EmployeeSummaryResponse> response = employeeService.getAllEmployees(tenantId, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEES BY STATUS
    // =====================================================

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get employees by status", description = "Retrieves employees filtered by status")
    public ResponseEntity<Page<EmployeeSummaryResponse>> getEmployeesByStatus(
            @Parameter(description = "Employee Status", required = true)
            @PathVariable EmployeeStatus status,
            @AuthenticationPrincipal Employee currentEmployee,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get employees with status: {} for tenant: {}", status, tenantId);
        Page<EmployeeSummaryResponse> response = employeeService.getEmployeesByStatus(tenantId, status, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET EMPLOYEES BY DEPARTMENT
    // =====================================================

    @GetMapping("/department/name/{departmentName}")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'EMPLOYEE_VIEW_TEAM')")
    @Operation(summary = "Get employees by department name", description = "Retrieves employees filtered by department name")
    public ResponseEntity<List<EmployeeSummaryResponse>> getEmployeesByDepartmentName(
            @Parameter(description = "Department name", required = true)
            @PathVariable String departmentName,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get employees in department: {} for tenant: {}", departmentName, tenantId);
        List<EmployeeSummaryResponse> response = employeeService.getEmployeesByDepartmentName(tenantId, departmentName);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET TEAM MEMBERS (Paginated)
    // =====================================================

    @GetMapping("/managers/{managerId}/team")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL') or #managerId == principal.id")
    @Operation(summary = "Get team members", description = "Retrieves all employees reporting to a manager")
    public ResponseEntity<Page<EmployeeSummaryResponse>> getTeamMembers(
            @Parameter(description = "Manager ID", required = true)
            @PathVariable Long managerId,
            @AuthenticationPrincipal Employee currentEmployee,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get team members for manager: {} in tenant: {}", managerId, tenantId);
        Page<EmployeeSummaryResponse> response = employeeService.getTeamMembersPaginated(tenantId, managerId, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET MY TEAM (Current employee's team)
    // =====================================================

    @GetMapping("/my-team")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get my team", description = "Retrieves all employees reporting to the current employee")
    public ResponseEntity<Page<EmployeeSummaryResponse>> getMyTeam(
            @AuthenticationPrincipal Employee currentEmployee,
            @PageableDefault(size = 20) Pageable pageable) {

        Long tenantId = currentEmployee.getTenantId();
        Long managerId = currentEmployee.getId();
        log.info("REST request to get team for manager: {} in tenant: {}", managerId, tenantId);
        Page<EmployeeSummaryResponse> response = employeeService.getTeamMembersPaginated(tenantId, managerId, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ORGANIZATION CHART
    // =====================================================

    @GetMapping("/organization-chart")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get organization chart", description = "Retrieves the complete organization hierarchy")
    public ResponseEntity<List<EmployeeSummaryResponse>> getOrganizationChart(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get organization chart for tenant: {}", tenantId);
        List<EmployeeSummaryResponse> response = employeeService.getOrganizationChart(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPDATE EMPLOYEE
    // =====================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT') or #id == principal.id")
    @Operation(summary = "Update employee", description = "Updates an existing employee")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequest request,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();
        log.info("REST request to update employee with id: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        EmployeeResponse response = employeeService.updateEmployee(id, tenantId, request);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPDATE EMPLOYEE STATUS
    // =====================================================

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_EDIT', 'ROLE_ASSIGN')")
    @Operation(summary = "Update employee status", description = "Updates the status of an employee")
    public ResponseEntity<Void> updateEmployeeStatus(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "New status", required = true)
            @RequestParam EmployeeStatus status,
            @Parameter(description = "Reason for status change")
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();
        log.info("REST request to update employee status for id: {} to: {} for tenant: {} by employee: {}",
                id, status, tenantId, employeeId);
        employeeService.updateEmployeeStatus(id, tenantId, status, reason);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // CONFIRM EMPLOYEE (END PROBATION)
    // =====================================================

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_EDIT', 'ROLE_ASSIGN')")
    @Operation(summary = "Confirm employee", description = "Confirms an employee after probation period")
    public ResponseEntity<Void> confirmEmployee(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();
        log.info("REST request to confirm employee with id: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        employeeService.confirmEmployee(id, tenantId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // SEARCH EMPLOYEES
    // =====================================================

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Search employees", description = "Searches employees by name, email, code, etc.")
    public ResponseEntity<Page<EmployeeSummaryResponse>> searchEmployees(
            @Parameter(description = "Search term", required = true)
            @RequestParam String searchTerm,
            @AuthenticationPrincipal Employee currentEmployee,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to search employees with term: {} for tenant: {}", searchTerm, tenantId);
        Page<EmployeeSummaryResponse> response = employeeService.searchEmployees(tenantId, searchTerm, pageable);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // DELETE EMPLOYEE (SOFT DELETE)
    // =====================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_DELETE')")
    @Operation(summary = "Delete employee", description = "Soft deletes an employee")
    public ResponseEntity<Void> deleteEmployee(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();
        log.info("REST request to delete employee with id: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        employeeService.deleteEmployee(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // GET DEPARTMENT STATISTICS
    // =====================================================

    @GetMapping("/statistics/departments")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get department statistics", description = "Retrieves employee count by department")
    public ResponseEntity<List<DepartmentStat>> getDepartmentStatistics(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get department statistics for tenant: {}", tenantId);
        List<DepartmentStat> response = employeeService.getDepartmentStatistics(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET UPCOMING BIRTHDAYS
    // =====================================================

    @GetMapping("/upcoming/birthdays")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get upcoming birthdays", description = "Retrieves employees with birthdays in next N days")
    public ResponseEntity<List<EmployeeSummaryResponse>> getUpcomingBirthdays(
            @Parameter(description = "Number of days to look ahead", example = "30")
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get upcoming birthdays for tenant: {} in next {} days", tenantId, days);
        List<EmployeeSummaryResponse> response = employeeService.getUpcomingBirthdays(tenantId, days);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET UPCOMING ANNIVERSARIES
    // =====================================================

    @GetMapping("/upcoming/anniversaries")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get upcoming work anniversaries", description = "Retrieves employees with work anniversaries in next N days")
    public ResponseEntity<List<EmployeeSummaryResponse>> getUpcomingAnniversaries(
            @Parameter(description = "Number of days to look ahead", example = "30")
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get upcoming anniversaries for tenant: {} in next {} days", tenantId, days);
        List<EmployeeSummaryResponse> response = employeeService.getUpcomingAnniversaries(tenantId, days);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // SEARCH EMPLOYEES FOR ASSIGNMENT (Dropdown)
    // =====================================================

    @GetMapping("/search/assignment")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Search employees for manager assignment",
            description = "Searches employees by name, email, or code for assignment dropdown")
    public ResponseEntity<Page<EmployeeSearchResponse>> searchEmployeesForAssignment(
            @Parameter(description = "Search term (name, email, or employee code)", required = true)
            @RequestParam String query,
            @Parameter(description = "Number of results to return")
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Pageable pageable = PageRequest.of(0, size);
        Page<EmployeeSearchResponse> results = employeeService.searchEmployeesForAssignment(tenantId, query, pageable);
        return ResponseEntity.ok(results);
    }

    // =====================================================
    // ASSIGN MANAGER BY EMPLOYEE CODE (User-Friendly)
    // =====================================================

    @PutMapping("/by-code/{employeeCode}/manager")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    @Operation(summary = "Assign manager by employee code",
            description = "Assigns a manager to an employee using their employee codes")
    public ResponseEntity<EmployeeResponse> assignManagerByCode(
            @Parameter(description = "Employee code of the employee", required = true)
            @PathVariable String employeeCode,
            @Parameter(description = "Employee code of the manager (leave empty to remove manager)")
            @RequestParam(required = false) String managerCode,
            @Parameter(description = "Reason for manager change")
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        EmployeeResponse response = employeeService.assignManagerByCode(employeeCode, managerCode, tenantId, reason);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // REMOVE MANAGER
    // =====================================================

    @DeleteMapping("/by-code/{employeeCode}/manager")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    @Operation(summary = "Remove manager",
            description = "Removes the manager from an employee")
    public ResponseEntity<Void> removeManager(
            @Parameter(description = "Employee code", required = true)
            @PathVariable String employeeCode,
            @Parameter(description = "Reason for removing manager")
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        employeeService.removeManager(employeeCode, tenantId, reason);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // GET TEAM MEMBERS LIST (Direct Reports)
    // =====================================================

    @GetMapping("/{managerId}/team/list")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL') or #managerId == principal.id")
    @Operation(summary = "Get team members list",
            description = "Retrieves all direct reports of a manager")
    public ResponseEntity<List<EmployeeSummaryResponse>> getTeamMembersList(
            @Parameter(description = "Manager ID", required = true)
            @PathVariable Long managerId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        List<EmployeeSummaryResponse> team = employeeService.getTeamMembers(managerId, tenantId);
        return ResponseEntity.ok(team);
    }

    // =====================================================
    // GET ALL SUBORDINATES (Complete hierarchy)
    // =====================================================

    @GetMapping("/{managerId}/subordinates")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL') or #managerId == principal.id")
    @Operation(summary = "Get all subordinates",
            description = "Retrieves all employees under a manager (complete hierarchy)")
    public ResponseEntity<List<EmployeeSummaryResponse>> getAllSubordinates(
            @Parameter(description = "Manager ID", required = true)
            @PathVariable Long managerId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        List<EmployeeSummaryResponse> subordinates = employeeService.getAllSubordinates(managerId, tenantId);
        return ResponseEntity.ok(subordinates);
    }

    // =====================================================
    // GET MANAGER CHAIN (Reporting hierarchy)
    // =====================================================

    @GetMapping("/{employeeId}/manager-chain")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL') or #employeeId == principal.id")
    @Operation(summary = "Get manager chain",
            description = "Retrieves the complete reporting chain of an employee")
    public ResponseEntity<List<EmployeeSummaryResponse>> getManagerChain(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long employeeId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        List<EmployeeSummaryResponse> chain = employeeService.getManagerChain(employeeId, tenantId);
        return ResponseEntity.ok(chain);
    }

    // =====================================================
    // CHECK IF EMPLOYEE IS MANAGER
    // =====================================================

    @GetMapping("/{employeeId}/is-manager")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL') or #employeeId == principal.id")
    @Operation(summary = "Check if employee is manager",
            description = "Returns true if the employee has direct reports")
    public ResponseEntity<Boolean> isManager(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long employeeId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        boolean isManager = employeeService.isManager(employeeId, tenantId);
        return ResponseEntity.ok(isManager);
    }

    // =====================================================
    // GET EMPLOYEES WITH NO MANAGER (Top level)
    // =====================================================

    @GetMapping("/no-manager")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    @Operation(summary = "Get employees with no manager",
            description = "Retrieves all employees who don't report to anyone")
    public ResponseEntity<List<EmployeeSummaryResponse>> getEmployeesWithNoManager(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        List<EmployeeSummaryResponse> employees = employeeService.getEmployeesWithNoManager(tenantId);
        return ResponseEntity.ok(employees);
    }

    @PostMapping("/process-offboarding")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    @Operation(summary = "Process offboarded employees manually",
            description = "Deactivates all employees whose notice periods or last working dates have passed")
    public ResponseEntity<Void> processOffboarding(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to process offboarded employees for tenant: {}", tenantId);
        employeeService.processOffboardedEmployees(tenantId);
        return ResponseEntity.ok().build();
    }
}