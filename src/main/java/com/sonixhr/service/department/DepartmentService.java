package com.sonixhr.service.department;

import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.department.DepartmentRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;

    // =====================================================
    // CREATE DEPARTMENT
    // =====================================================

    @Transactional
    public DepartmentResponse createDepartment(UUID tenantId, DepartmentRequest request) {
        log.info("Creating department for tenant: {}", tenantId);

        // Validate unique name
        if (departmentRepository.existsByTenant_IdAndName(tenantId, request.getName())) {
            throw new BusinessException("Department with name '" + request.getName() + "' already exists");
        }

        // Validate unique code
        if (departmentRepository.existsByTenant_IdAndCode(tenantId, request.getCode())) {
            throw new BusinessException("Department with code '" + request.getCode() + "' already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Department department = Department.builder()
                .tenant(tenant)
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .isActive(true)
                .build();

        Department savedDepartment = departmentRepository.save(department);
        log.info("Department created with id: {}", savedDepartment.getId());

        return convertToResponse(savedDepartment);
    }

    // =====================================================
    // UPDATE DEPARTMENT
    // =====================================================

    @Transactional
    public DepartmentResponse updateDepartment(Long id, UUID tenantId, DepartmentRequest request) {
        log.info("Updating department: {} for tenant: {}", id, tenantId);

        Department department = departmentRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        // Check name uniqueness (if changed)
        if (!department.getName().equals(request.getName()) &&
                departmentRepository.existsByTenant_IdAndName(tenantId, request.getName())) {
            throw new BusinessException("Department with name '" + request.getName() + "' already exists");
        }

        // Check code uniqueness (if changed)
        if (!department.getCode().equals(request.getCode()) &&
                departmentRepository.existsByTenant_IdAndCode(tenantId, request.getCode())) {
            throw new BusinessException("Department with code '" + request.getCode() + "' already exists");
        }

        department.setName(request.getName());
        department.setCode(request.getCode().toUpperCase());
        department.setDescription(request.getDescription());

        Department updatedDepartment = departmentRepository.save(department);
        log.info("Department updated: {}", id);

        return convertToResponse(updatedDepartment);
    }

    // =====================================================
    // GET DEPARTMENT BY ID (WITH EMPLOYEE COUNTS)
    // =====================================================

    public DepartmentResponse getDepartmentById(Long id, UUID tenantId) {
        Department department = departmentRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        return convertToResponse(department);
    }

    // =====================================================
    // GET ALL DEPARTMENTS (WITH EMPLOYEE COUNTS)
    // =====================================================

    public Page<DepartmentResponse> getAllDepartments(UUID tenantId, Pageable pageable) {
        Page<Department> departments = departmentRepository.findByTenant_Id(tenantId, pageable);
        return departments.map(this::convertToResponse);
    }

    public List<DepartmentResponse> getAllDepartmentsList(UUID tenantId) {
        List<Department> departments = departmentRepository.findByTenant_Id(tenantId);
        return departments.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
// =====================================================
// GET EMPLOYEE COUNT (Legacy method for backward compatibility)
// =====================================================

    public Long getEmployeeCount(Long departmentId, UUID tenantId) {
        return getTotalEmployeeCount(departmentId, tenantId);
    }
    public Page<DepartmentResponse> searchDepartments(UUID tenantId, String query, Pageable pageable) {
        log.info("Searching departments for tenant: {} with query: {}", tenantId, query);
        Page<Department> departments = departmentRepository.searchDepartments(tenantId, query, pageable);
        return departments.map(this::convertToResponse);
    }

    // =====================================================
    // GET DEPARTMENT WITH STATISTICS (ENHANCED)
    // =====================================================

    public DepartmentResponse getDepartmentWithStats(Long id, UUID tenantId) {
        Department department = departmentRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        // Get all employee counts for this department
        Long totalEmployees = employeeRepository.countByDepartmentIdAndTenantId(department.getId(), tenantId);
        Long activeEmployees = employeeRepository.countActiveByDepartmentIdAndTenantId(
                department.getId(), tenantId, EmployeeStatus.ACTIVE);
        Long onProbationEmployees = employeeRepository.countActiveByDepartmentIdAndTenantId(
                department.getId(), tenantId, EmployeeStatus.PROBATION);

        // Calculate percentages
        Double activePercentage = totalEmployees > 0 ? (activeEmployees * 100.0 / totalEmployees) : 0.0;

        return DepartmentResponse.builder()
                .id(department.getId())
                .tenantId(department.getTenantId())
                .name(department.getName())
                .code(department.getCode())
                .description(department.getDescription())
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .onProbationEmployees(onProbationEmployees)
                .activePercentage(Math.round(activePercentage * 100.0) / 100.0)
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }

    // =====================================================
    // GET ALL DEPARTMENTS WITH BULK EMPLOYEE COUNTS (OPTIMIZED)
    // =====================================================

    public List<DepartmentResponse> getAllDepartmentsWithBulkCounts(UUID tenantId) {
        List<Department> departments = departmentRepository.findByTenant_Id(tenantId);

        // Get all employee counts in a single query per department
        // Or use batch query for better performance
        Map<Long, Long> totalCounts = new HashMap<>();
        Map<Long, Long> activeCounts = new HashMap<>();

        for (Department dept : departments) {
            totalCounts.put(dept.getId(), employeeRepository.countByDepartmentIdAndTenantId(dept.getId(), tenantId));
            activeCounts.put(dept.getId(), employeeRepository.countActiveByDepartmentIdAndTenantId(
                    dept.getId(), tenantId, EmployeeStatus.ACTIVE));
        }

        return departments.stream()
                .map(dept -> convertToResponseWithCounts(dept,
                        totalCounts.getOrDefault(dept.getId(), 0L),
                        activeCounts.getOrDefault(dept.getId(), 0L)))
                .collect(Collectors.toList());
    }

    // =====================================================
    // DELETE DEPARTMENT (WITH VALIDATION)
    // =====================================================

    @Transactional
    public void deleteDepartment(Long id, UUID tenantId) {
        log.info("Deleting department: {} for tenant: {}", id, tenantId);

        Department department = departmentRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        // Check if department has employees
        Long employeeCount = employeeRepository.countByDepartmentIdAndTenantId(department.getId(), tenantId);
        if (employeeCount > 0) {
            throw new BusinessException("Cannot delete department with " + employeeCount +
                    " employees. Reassign or remove employees first.");
        }

        departmentRepository.delete(department);
        log.info("Department deleted: {}", id);
    }

    // =====================================================
    // GET EMPLOYEE COUNTS FOR DEPARTMENT
    // =====================================================

    public Long getTotalEmployeeCount(Long departmentId, UUID tenantId) {
        return employeeRepository.countByDepartmentIdAndTenantId(departmentId, tenantId);
    }

    public Long getActiveEmployeeCount(Long departmentId, UUID tenantId) {
        return employeeRepository.countActiveByDepartmentIdAndTenantId(departmentId, tenantId, EmployeeStatus.ACTIVE);
    }

    public Long getOnProbationCount(Long departmentId, UUID tenantId) {
        return employeeRepository.countActiveByDepartmentIdAndTenantId(departmentId, tenantId, EmployeeStatus.PROBATION);
    }

    // =====================================================
    // DEPARTMENT STATISTICS DASHBOARD
    // =====================================================

    public Map<String, Object> getDepartmentDashboard(UUID tenantId) {
        Map<String, Object> dashboard = new HashMap<>();

        List<Department> departments = departmentRepository.findByTenant_Id(tenantId);

        long totalDepartments = departments.size();
        long totalEmployees = 0;
        long totalActiveEmployees = 0;

        Map<String, Long> departmentWiseCount = new HashMap<>();

        for (Department dept : departments) {
            long deptTotal = employeeRepository.countByDepartmentIdAndTenantId(dept.getId(), tenantId);
            long deptActive = employeeRepository.countActiveByDepartmentIdAndTenantId(dept.getId(), tenantId, EmployeeStatus.ACTIVE);

            totalEmployees += deptTotal;
            totalActiveEmployees += deptActive;
            departmentWiseCount.put(dept.getName(), deptTotal);
        }

        dashboard.put("totalDepartments", totalDepartments);
        dashboard.put("totalEmployees", totalEmployees);
        dashboard.put("totalActiveEmployees", totalActiveEmployees);
        dashboard.put("averageEmployeesPerDept", totalDepartments > 0 ? (double) totalEmployees / totalDepartments : 0);
        dashboard.put("departmentWiseCount", departmentWiseCount);

        return dashboard;
    }

    // =====================================================
    // PRIVATE CONVERSION METHODS
    // =====================================================

    private DepartmentResponse convertToResponse(Department department) {
        Long departmentId = department.getId();
        UUID tenantId = department.getTenantId();

        // Get employee counts using repository methods
        Long totalEmployees = employeeRepository.countByDepartmentIdAndTenantId(departmentId, tenantId);
        Long activeEmployees = employeeRepository.countActiveByDepartmentIdAndTenantId(departmentId, tenantId, EmployeeStatus.ACTIVE);
        Long onProbationEmployees = employeeRepository.countActiveByDepartmentIdAndTenantId(departmentId, tenantId, EmployeeStatus.PROBATION);

        // Calculate percentages
        Double activePercentage = totalEmployees > 0 ? (activeEmployees * 100.0 / totalEmployees) : 0.0;
        Double probationPercentage = totalEmployees > 0 ? (onProbationEmployees * 100.0 / totalEmployees) : 0.0;

        return DepartmentResponse.builder()
                .id(department.getId())
                .tenantId(department.getTenantId())
                .name(department.getName())
                .code(department.getCode())
                .description(department.getDescription())
                // Employee counts
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .onProbationEmployees(onProbationEmployees)
                .onLeaveEmployees(0L)  // To be implemented
                .resignedEmployees(0L)  // To be implemented
                // Percentages
                .activePercentage(Math.round(activePercentage * 100.0) / 100.0)
                .probationPercentage(Math.round(probationPercentage * 100.0) / 100.0)
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }

    private DepartmentResponse convertToResponseWithCounts(Department department, Long totalEmployees, Long activeEmployees) {
        Double activePercentage = totalEmployees > 0 ? (activeEmployees * 100.0 / totalEmployees) : 0.0;

        return DepartmentResponse.builder()
                .id(department.getId())
                .tenantId(department.getTenantId())
                .name(department.getName())
                .code(department.getCode())
                .description(department.getDescription())
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .activePercentage(Math.round(activePercentage * 100.0) / 100.0)
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }
}