package com.sonixhr.controller.department;

import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.department.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tenant/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final EmployeeRepository employeeRepository;

    // =====================================================
    // HELPER METHOD TO GET CURRENT EMPLOYEE
    // =====================================================

    private Employee getCurrentEmployee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Employee) {
            return (Employee) principal;
        } else if (principal instanceof UserDetails) {
            String email = ((UserDetails) principal).getUsername();
            return employeeRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Employee not found with email: " + email));
        } else {
            throw new RuntimeException("Unknown principal type: " + principal.getClass().getName());
        }
    }

    // =====================================================
    // CREATE DEPARTMENT
    // =====================================================

    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(
            @Valid @RequestBody DepartmentRequest request) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Creating department for tenant: {} by employee: {}", tenantId, employeeId);
        log.debug("Department name: {}, code: {}", request.getName(), request.getCode());

        DepartmentResponse response = departmentService.createDepartment(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =====================================================
    // GET DEPARTMENT BY ID
    // =====================================================

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable Long id) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting department by ID: {} for tenant: {}", id, tenantId);

        DepartmentResponse response = departmentService.getDepartmentById(id, tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET DEPARTMENT WITH STATS
    // =====================================================

    @GetMapping("/{id}/stats")
    public ResponseEntity<DepartmentResponse> getDepartmentWithStats(@PathVariable Long id) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting department with stats: {} for tenant: {}", id, tenantId);

        DepartmentResponse response = departmentService.getDepartmentWithStats(id, tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ALL DEPARTMENTS (PAGINATED)
    // =====================================================

    @GetMapping
    public ResponseEntity<Page<DepartmentResponse>> getAllDepartments(
            @PageableDefault(size = 20) Pageable pageable) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting all departments for tenant: {} with pageable: {}", tenantId, pageable);

        Page<DepartmentResponse> response = departmentService.getAllDepartments(tenantId, pageable);
        log.debug("Found {} departments", response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ALL DEPARTMENTS AS LIST
    // =====================================================

    @GetMapping("/list")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartmentsList() {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting all departments list for tenant: {}", tenantId);

        List<DepartmentResponse> response = departmentService.getAllDepartmentsList(tenantId);
        log.debug("Found {} departments", response.size());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ALL DEPARTMENTS WITH BULK COUNTS
    // =====================================================

    @GetMapping("/bulk-stats")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartmentsWithBulkCounts() {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting all departments with bulk counts for tenant: {}", tenantId);

        List<DepartmentResponse> response = departmentService.getAllDepartmentsWithBulkCounts(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPDATE DEPARTMENT
    // =====================================================

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Updating department: {} for tenant: {} by employee: {}", id, tenantId, employeeId);

        DepartmentResponse response = departmentService.updateDepartment(id, tenantId, request);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // DELETE DEPARTMENT
    // =====================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Deleting department: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        departmentService.deleteDepartment(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // EMPLOYEE COUNT ENDPOINTS
    // =====================================================

    @GetMapping("/{id}/employee-count/total")
    public ResponseEntity<Long> getTotalEmployeeCount(@PathVariable Long id) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting total employee count for department: {}", id);

        Long count = departmentService.getTotalEmployeeCount(id, tenantId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{id}/employee-count/active")
    public ResponseEntity<Long> getActiveEmployeeCount(@PathVariable Long id) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting active employee count for department: {}", id);

        Long count = departmentService.getActiveEmployeeCount(id, tenantId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{id}/employee-count/probation")
    public ResponseEntity<Long> getOnProbationCount(@PathVariable Long id) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting on-probation count for department: {}", id);

        Long count = departmentService.getOnProbationCount(id, tenantId);
        return ResponseEntity.ok(count);
    }

    // =====================================================
    // DEPARTMENT DASHBOARD
    // =====================================================

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDepartmentDashboard() {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Getting department dashboard for tenant: {}", tenantId);

        Map<String, Object> dashboard = departmentService.getDepartmentDashboard(tenantId);
        return ResponseEntity.ok(dashboard);
    }

    // =====================================================
    // SEARCH DEPARTMENTS
    // =====================================================

    @GetMapping("/search")
    public ResponseEntity<Page<DepartmentResponse>> searchDepartments(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {

        Employee currentEmployee = getCurrentEmployee();
        Long tenantId = currentEmployee.getTenantId();

        log.debug("Searching departments for tenant: {} with query: {}", tenantId, query);

        Page<DepartmentResponse> response = departmentService.searchDepartments(tenantId, query, pageable);
        return ResponseEntity.ok(response);
    }
}