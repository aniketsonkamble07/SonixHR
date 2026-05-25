package com.sonixhr.service.department;

import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.tenant.Tenant;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService  {

    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;


    @Transactional
    public DepartmentResponse createDepartment(UUID tenantId, DepartmentRequest request) {
        log.info("Creating department for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Department department = Department.builder()
                .tenant(tenant)
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .build();

        Department savedDepartment = departmentRepository.save(department);
        return convertToResponse(savedDepartment);
    }


    @Transactional
    public DepartmentResponse updateDepartment(Long id, UUID tenantId, DepartmentRequest request) {
        log.info("Updating department: {} for tenant: {}", id, tenantId);

        Department department = departmentRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        department.setName(request.getName());
        department.setCode(request.getCode().toUpperCase());
        department.setDescription(request.getDescription());

        Department updatedDepartment = departmentRepository.save(department);
        return convertToResponse(updatedDepartment);
    }


    public DepartmentResponse getDepartmentById(Long id, UUID tenantId) {
        Department department = departmentRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        return convertToResponse(department);
    }


    public Page<DepartmentResponse> getAllDepartments(UUID tenantId, Pageable pageable) {
        return departmentRepository.findByTenant_Id(tenantId, pageable)
                .map(this::convertToResponse);
    }


    public List<DepartmentResponse> getAllDepartmentsList(UUID tenantId) {
        return departmentRepository.findByTenant_Id(tenantId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }


    @Transactional
    public void deleteDepartment(Long id, UUID tenantId) {
        log.info("Deleting department: {} for tenant: {}", id, tenantId);

        Department department = departmentRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

        departmentRepository.delete(department);
    }


    public Long getEmployeeCount(Long departmentId, UUID tenantId) {
        return employeeRepository.countByDepartmentIdAndTenantId(departmentId, tenantId);
    }

    private DepartmentResponse convertToResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .tenantId(department.getTenantId())
                .name(department.getName())
                .code(department.getCode())
                .description(department.getDescription())
                .employeeCount(getEmployeeCount(department.getId(), department.getTenantId()))
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }
}