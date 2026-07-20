package com.sonixhr.service.department;

import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.dto.department.DepartmentLookupResponse;
import com.sonixhr.entity.department.Department;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.exceptions.ValidationException;
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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;

    // =====================================================
    // CREATE DEPARTMENT
    // =====================================================

    @Transactional
    @CacheEvict(value = "departmentsLookup", key = "#tenantId")
    public DepartmentResponse createDepartment(Long tenantId, DepartmentRequest request) {
        log.info("Creating department for tenant: {}", tenantId);

        // Validate unique name
        if (departmentRepository.existsByTenant_IdAndNameAndIsDeletedFalse(tenantId, request.getName())) {
            throw new ValidationException("name", "Department name already exists for this tenant");
        }

        // Validate unique code
        if (departmentRepository.existsByTenant_IdAndCodeAndIsDeletedFalse(tenantId, request.getCode())) {
            throw new ValidationException("code", "Department code already exists for this tenant");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Department department = Department.builder()
                .tenant(tenant)
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .isActive(true)
                .isDeleted(false)
                .build();

        Department savedDepartment = departmentRepository.save(department);
        log.info("Department created with id: {}", savedDepartment.getId());

        return convertToResponse(savedDepartment);
    }

    // =====================================================
    // UPDATE DEPARTMENT
    // =====================================================

    @Transactional
    @CacheEvict(value = "departmentsLookup", key = "#tenantId")
    public DepartmentResponse updateDepartment(Long id, Long tenantId, DepartmentRequest request) {
        log.info("Updating department: {} for tenant: {}", id, tenantId);

        Department department = departmentRepository.findByIdAndTenant_IdAndIsDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        // Check name uniqueness (if changed)
        if (!department.getName().equals(request.getName()) &&
                departmentRepository.existsByTenant_IdAndNameAndIsDeletedFalse(tenantId, request.getName())) {
            throw new ValidationException("name", "Department name already exists for this tenant");
        }

        // Check code uniqueness (if changed)
        if (!department.getCode().equals(request.getCode()) &&
                departmentRepository.existsByTenant_IdAndCodeAndIsDeletedFalse(tenantId, request.getCode())) {
            throw new ValidationException("code", "Department code already exists for this tenant");
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

    public DepartmentResponse getDepartmentById(Long id, Long tenantId) {
        Department department = departmentRepository.findByIdAndTenant_IdAndIsDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        return convertToResponse(department);
    }

    // =====================================================
    // GET ALL DEPARTMENTS (WITH EMPLOYEE COUNTS)
    // =====================================================

    public Page<DepartmentResponse> getAllDepartments(Long tenantId, Pageable pageable) {
        Page<Department> departments = departmentRepository.findByTenant_IdAndIsDeletedFalse(tenantId, pageable);
        Map<Long, Map<EmployeeStatus, Long>> countsMap = getDepartmentCountsMap(tenantId);
        return departments.map(dept -> convertToResponseUsingAllCountsMap(dept, countsMap));
    }

    public List<DepartmentResponse> getAllDepartmentsList(Long tenantId) {
        List<Department> departments = departmentRepository.findByTenant_IdAndIsDeletedFalse(tenantId);
        Map<Long, Map<EmployeeStatus, Long>> countsMap = getDepartmentCountsMap(tenantId);
        return departments.stream()
                .map(dept -> convertToResponseUsingAllCountsMap(dept, countsMap))
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET EMPLOYEE COUNT (Legacy method for backward compatibility)
    // =====================================================

    public Long getEmployeeCount(Long departmentId, Long tenantId) {
        return getTotalEmployeeCount(departmentId, tenantId);
    }

    public Page<DepartmentResponse> searchDepartments(Long tenantId, String query, Pageable pageable) {
        log.info("Searching departments for tenant: {} with query: {}", tenantId, query);
        Page<Department> departments = departmentRepository.searchDepartments(tenantId, query, pageable);
        Map<Long, Map<EmployeeStatus, Long>> countsMap = getDepartmentCountsMap(tenantId);
        return departments.map(dept -> convertToResponseUsingAllCountsMap(dept, countsMap));
    }

    // =====================================================
    // GET DEPARTMENT WITH STATISTICS (ENHANCED)
    // =====================================================

    public DepartmentResponse getDepartmentWithStats(Long id, Long tenantId) {
        Department department = departmentRepository.findByIdAndTenant_IdAndIsDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        return convertToResponse(department);
    }

    // =====================================================
    // GET ALL DEPARTMENTS WITH BULK EMPLOYEE COUNTS (OPTIMIZED)
    // =====================================================

    public List<DepartmentResponse> getAllDepartmentsWithBulkCounts(Long tenantId) {
        List<Department> departments = departmentRepository.findByTenant_IdAndIsDeletedFalse(tenantId);
        Map<Long, Map<EmployeeStatus, Long>> countsMap = getDepartmentCountsMap(tenantId);

        return departments.stream()
                .map(dept -> {
                    Map<EmployeeStatus, Long> statusCounts = countsMap.getOrDefault(dept.getId(), Map.of());
                    long totalEmployees = statusCounts.values().stream().mapToLong(Long::longValue).sum();
                    long activeEmployees = statusCounts.getOrDefault(EmployeeStatus.ACTIVE, 0L);
                    return convertToResponseWithCounts(dept, totalEmployees, activeEmployees);
                })
                .collect(Collectors.toList());
    }

    // =====================================================
    // DELETE DEPARTMENT (SOFT DELETE WITH VALIDATION)
    // =====================================================

    @Transactional
    @CacheEvict(value = "departmentsLookup", key = "#tenantId")
    public void deleteDepartment(Long id, Long tenantId) {
        log.info("Soft deleting department: {} for tenant: {}", id, tenantId);

        Department department = departmentRepository.findByIdAndTenant_IdAndIsDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        // Soft delete: deactivate, set flag, and rename code/name to release unique constraints
        department.setIsActive(false);
        department.setIsDeleted(true);
        
        long timestamp = System.currentTimeMillis();
        department.setName(department.getName() + " (Deleted " + timestamp + ")");
        department.setCode(department.getCode() + "-DEL-" + timestamp);

        departmentRepository.save(department);
        log.info("Department soft deleted successfully: {}", id);
    }

    // =====================================================
    // GET EMPLOYEE COUNTS FOR DEPARTMENT
    // =====================================================

    public Long getTotalEmployeeCount(Long departmentId, Long tenantId) {
        return employeeRepository.countByDepartmentIdAndTenantId(departmentId, tenantId);
    }

    public Long getActiveEmployeeCount(Long departmentId, Long tenantId) {
        return employeeRepository.countActiveByDepartmentIdAndTenantId(departmentId, tenantId, EmployeeStatus.ACTIVE);
    }

    public Long getOnProbationCount(Long departmentId, Long tenantId) {
        return employeeRepository.countActiveByDepartmentIdAndTenantId(departmentId, tenantId, EmployeeStatus.PROBATION);
    }

    // =====================================================
    // DEPARTMENT STATISTICS DASHBOARD
    // =====================================================

    public Map<String, Object> getDepartmentDashboard(Long tenantId) {
        Map<String, Object> dashboard = new HashMap<>();

        List<Department> departments = departmentRepository.findByTenant_IdAndIsDeletedFalse(tenantId);

        long totalDepartments = departments.size();
        long totalEmployees = 0;
        long totalActiveEmployees = 0;

        Map<String, Long> departmentWiseCount = new HashMap<>();
        Map<Long, Map<EmployeeStatus, Long>> countsMap = getDepartmentCountsMap(tenantId);

        for (Department dept : departments) {
            Map<EmployeeStatus, Long> statusCounts = countsMap.getOrDefault(dept.getId(), Map.of());
            long deptTotal = statusCounts.values().stream().mapToLong(Long::longValue).sum();
            long deptActive = statusCounts.getOrDefault(EmployeeStatus.ACTIVE, 0L);

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

    private Map<Long, Map<EmployeeStatus, Long>> getDepartmentCountsMap(Long tenantId) {
        List<Object[]> countData = employeeRepository.countEmployeesByDepartmentAndStatus(tenantId);
        Map<Long, Map<EmployeeStatus, Long>> map = new HashMap<>();
        for (Object[] row : countData) {
            Long deptId = (Long) row[0];
            EmployeeStatus status = (EmployeeStatus) row[1];
            Long count = (Long) row[2];
            map.computeIfAbsent(deptId, k -> new HashMap<>()).put(status, count);
        }
        return map;
    }

    private Map<EmployeeStatus, Long> getDepartmentStatusCounts(Long tenantId, Long departmentId) {
        List<Object[]> countData = employeeRepository.countEmployeesByStatusForDepartment(tenantId, departmentId);
        Map<EmployeeStatus, Long> map = new HashMap<>();
        for (Object[] row : countData) {
            EmployeeStatus status = (EmployeeStatus) row[0];
            Long count = (Long) row[1];
            map.put(status, count);
        }
        return map;
    }

    private DepartmentResponse convertToResponse(Department department) {
        Map<EmployeeStatus, Long> statusCounts = getDepartmentStatusCounts(department.getTenantId(), department.getId());
        return convertToResponse(department, statusCounts);
    }

    private DepartmentResponse convertToResponseUsingAllCountsMap(Department department, Map<Long, Map<EmployeeStatus, Long>> countsMap) {
        Map<EmployeeStatus, Long> statusCounts = countsMap.getOrDefault(department.getId(), Map.of());
        return convertToResponse(department, statusCounts);
    }

    private DepartmentResponse convertToResponse(Department department, Map<EmployeeStatus, Long> statusCounts) {
        long totalEmployees = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long activeEmployees = statusCounts.getOrDefault(EmployeeStatus.ACTIVE, 0L);
        long onProbationEmployees = statusCounts.getOrDefault(EmployeeStatus.PROBATION, 0L);

        double activePercentage = totalEmployees > 0 ? (activeEmployees * 100.0 / totalEmployees) : 0.0;
        double probationPercentage = totalEmployees > 0 ? (onProbationEmployees * 100.0 / totalEmployees) : 0.0;

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
                .onLeaveEmployees(statusCounts.getOrDefault(EmployeeStatus.ON_LEAVE, 0L))
                .resignedEmployees(statusCounts.getOrDefault(EmployeeStatus.RESIGNED, 0L))
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

    public DepartmentLookupResponse toLookupResponse(Department department) {
        if (department == null) {
            return null;
        }
        return DepartmentLookupResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .code(department.getCode())
                .build();
    }

    @Cacheable(value = "departmentsLookup", key = "#tenantId", unless = "#result == null")
    public List<DepartmentLookupResponse> getDepartmentLookup(Long tenantId) {
        log.debug("Fetching department lookup for tenant: {}", tenantId);
        List<Department> departments = departmentRepository.findByTenant_IdAndIsDeletedFalse(tenantId);
        return departments.stream()
                .map(this::toLookupResponse)
                .collect(Collectors.toList());
    }
}