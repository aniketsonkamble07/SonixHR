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
        log.info("REST request to create department for tenant: {}", tenantId);
        DepartmentResponse response = departmentService.createDepartment(tenantId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> getDepartmentById(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {
        log.info("REST request to get department: {} for tenant: {}", id, tenantId);
        DepartmentResponse response = departmentService.getDepartmentById(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DepartmentResponse>> getAllDepartments(
            @PathVariable UUID tenantId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("REST request to get all departments for tenant: {}", tenantId);
        Page<DepartmentResponse> response = departmentService.getAllDepartments(tenantId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartmentsList(
            @PathVariable UUID tenantId) {
        log.info("REST request to get all departments list for tenant: {}", tenantId);
        List<DepartmentResponse> response = departmentService.getAllDepartmentsList(tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable UUID tenantId,
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {
        log.info("REST request to update department: {} for tenant: {}", id, tenantId);
        DepartmentResponse response = departmentService.updateDepartment(id, tenantId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {
        log.info("REST request to delete department: {} for tenant: {}", id, tenantId);
        departmentService.deleteDepartment(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/employee-count")
    public ResponseEntity<Long> getEmployeeCount(
            @PathVariable UUID tenantId,
            @PathVariable Long id) {
        log.info("REST request to get employee count for department: {}", id);
        Long count = departmentService.getEmployeeCount(id, tenantId);
        return ResponseEntity.ok(count);
    }
}