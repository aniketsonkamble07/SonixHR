package com.sonixhr.controller.department;

import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.service.department.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tenants/{tenantId}/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(
            @PathVariable UUID tenantId,
            @Valid @RequestBody DepartmentRequest request) {

        // DEBUG: Log the request details
        log.info("========== CREATE DEPARTMENT DEBUG ==========");
        log.info("Tenant ID received: {}", tenantId);
        log.info("Request Body - Name: {}", request.getName());
        log.info("Request Body - Code: {}", request.getCode());
        log.info("Request Body - Description: {}", request.getDescription());
        log.info("============================================");

        try {
            DepartmentResponse response = departmentService.createDepartment(tenantId, request);
            log.info("Department created successfully with ID: {}", response.getId());
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error creating department: {}", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> getDepartmentById(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {

        log.debug("REST request to get department: {} for tenant: {}", id, tenantId);
        DepartmentResponse response = departmentService.getDepartmentById(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<DepartmentResponse> getDepartmentWithStats(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {

        log.debug("REST request to get department with stats: {} for tenant: {}", id, tenantId);
        DepartmentResponse response = departmentService.getDepartmentWithStats(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DepartmentResponse>> getAllDepartments(
            @PathVariable UUID tenantId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("REST request to get all departments for tenant: {} with pageable: {}", tenantId, pageable);
        Page<DepartmentResponse> response = departmentService.getAllDepartments(tenantId, pageable);
        log.debug("Found {} departments", response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartmentsList(
            @PathVariable UUID tenantId) {

        log.debug("REST request to get all departments list for tenant: {}", tenantId);
        List<DepartmentResponse> response = departmentService.getAllDepartmentsList(tenantId);
        log.debug("Found {} departments", response.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bulk-stats")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartmentsWithBulkCounts(
            @PathVariable UUID tenantId) {

        log.debug("REST request to get all departments with bulk counts for tenant: {}", tenantId);
        List<DepartmentResponse> response = departmentService.getAllDepartmentsWithBulkCounts(tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable UUID tenantId,
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {

        log.debug("REST request to update department: {} for tenant: {}", id, tenantId);
        DepartmentResponse response = departmentService.updateDepartment(id, tenantId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {

        log.debug("REST request to delete department: {} for tenant: {}", id, tenantId);
        departmentService.deleteDepartment(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/employee-count/total")
    public ResponseEntity<Long> getTotalEmployeeCount(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {

        log.debug("REST request to get total employee count for department: {}", id);
        Long count = departmentService.getTotalEmployeeCount(id, tenantId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{id}/employee-count/active")
    public ResponseEntity<Long> getActiveEmployeeCount(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {

        log.debug("REST request to get active employee count for department: {}", id);
        Long count = departmentService.getActiveEmployeeCount(id, tenantId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{id}/employee-count/probation")
    public ResponseEntity<Long> getOnProbationCount(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {

        log.debug("REST request to get on-probation count for department: {}", id);
        Long count = departmentService.getOnProbationCount(id, tenantId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDepartmentDashboard(
            @PathVariable UUID tenantId) {

        log.debug("REST request to get department dashboard for tenant: {}", tenantId);
        Map<String, Object> dashboard = departmentService.getDepartmentDashboard(tenantId);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DepartmentResponse>> searchDepartments(
            @PathVariable UUID tenantId,
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("REST request to search departments for tenant: {} with query: {}", tenantId, query);
        Page<DepartmentResponse> response = departmentService.searchDepartments(tenantId, query, pageable);
        return ResponseEntity.ok(response);
    }
}