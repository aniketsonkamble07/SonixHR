package com.sonixhr;

import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.exceptions.ValidationException;
import com.sonixhr.repository.department.DepartmentRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.department.DepartmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for service-layer business logic.
 * No Spring context — dependencies are mocked with Mockito.
 * Cache and transaction annotations are not proxied here; only raw method logic is tested.
 */
@ExtendWith(MockitoExtension.class)
public class TenantApiUnitTest {

    @Mock private DepartmentRepository departmentRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private EmployeeRepository employeeRepository;

    @InjectMocks private DepartmentService departmentService;

    // =====================================================================
    // DepartmentService — createDepartment() validation
    // =====================================================================

    /**
     * When a department with the same name already exists for the tenant,
     * createDepartment() must throw ValidationException with field "name"
     * and must NOT call save().
     */
    @Test
    void createDepartment_throwsValidationException_whenNameAlreadyExists() {
        when(departmentRepository.existsByTenant_IdAndName(1L, "Engineering")).thenReturn(true);

        DepartmentRequest req = DepartmentRequest.builder()
                .name("Engineering")
                .code("ENG")
                .description("Engineering Department")
                .build();

        ValidationException ex = assertThrows(ValidationException.class,
                () -> departmentService.createDepartment(1L, req));

        assertTrue(ex.getErrors().containsKey("name"),
                "ValidationException should carry field='name' error");
        assertTrue(ex.getErrors().get("name").contains("already exists"),
                "Error message should mention 'already exists'");
        verify(departmentRepository, never()).save(any());
    }

    /**
     * When the department code is already taken (name is unique),
     * createDepartment() must throw ValidationException with field "code"
     * and must NOT call save().
     */
    @Test
    void createDepartment_throwsValidationException_whenCodeAlreadyExists() {
        when(departmentRepository.existsByTenant_IdAndName(1L, "Operations")).thenReturn(false);
        when(departmentRepository.existsByTenant_IdAndCode(1L, "OPS")).thenReturn(true);

        DepartmentRequest req = DepartmentRequest.builder()
                .name("Operations")
                .code("OPS")
                .description("Operations Department")
                .build();

        ValidationException ex = assertThrows(ValidationException.class,
                () -> departmentService.createDepartment(1L, req));

        assertTrue(ex.getErrors().containsKey("code"),
                "ValidationException should carry field='code' error");
        assertTrue(ex.getErrors().get("code").contains("already exists"),
                "Error message should mention 'already exists'");
        verify(departmentRepository, never()).save(any());
    }

    /**
     * When both name and code are unique but the tenant does not exist in DB,
     * createDepartment() must throw ResourceNotFoundException (not save a dangling record).
     */
    @Test
    void createDepartment_throwsResourceNotFoundException_whenTenantNotFound() {
        when(departmentRepository.existsByTenant_IdAndName(anyLong(), anyString())).thenReturn(false);
        when(departmentRepository.existsByTenant_IdAndCode(anyLong(), anyString())).thenReturn(false);
        when(tenantRepository.findById(42L)).thenReturn(Optional.empty());

        DepartmentRequest req = DepartmentRequest.builder()
                .name("Finance")
                .code("FIN")
                .description("Finance Department")
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> departmentService.createDepartment(42L, req));

        verify(departmentRepository, never()).save(any());
    }

    // =====================================================================
    // DepartmentService — getDepartmentById() not-found path
    // =====================================================================

    /**
     * When the department does not exist for the given tenant,
     * getDepartmentById() must throw ResourceNotFoundException.
     */
    @Test
    void getDepartmentById_throwsResourceNotFoundException_whenNotFound() {
        when(departmentRepository.findByIdAndTenant_Id(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> departmentService.getDepartmentById(999L, 1L));
    }

    /**
     * getDepartmentById() with a wrong tenant but valid department ID
     * must also throw ResourceNotFoundException (tenant isolation).
     */
    @Test
    void getDepartmentById_throwsResourceNotFoundException_forWrongTenant() {
        when(departmentRepository.findByIdAndTenant_Id(10L, 999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> departmentService.getDepartmentById(10L, 999L),
                "Fetching a department belonging to another tenant must be blocked");
    }

    // =====================================================================
    // Validation object assertions
    // =====================================================================

    /**
     * The ValidationException must carry a human-readable message for the field.
     */
    @Test
    void validationException_containsCorrectFieldAndMessage() {
        ValidationException ex = new ValidationException("email", "Email is already in use");
        assertEquals("email", ex.getErrors().keySet().iterator().next());
        assertEquals("Email is already in use", ex.getErrors().get("email"));
        assertTrue(ex.getMessage().contains("email"));
    }

    /**
     * The ValidationException constructed with a Map must expose all provided fields.
     */
    @Test
    void validationException_withMultipleFields_exposesAll() {
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        errors.put("name", "Name is required");
        errors.put("code", "Code is required");

        ValidationException ex = new ValidationException(errors);
        assertEquals(2, ex.getErrors().size());
        assertEquals("Name is required", ex.getErrors().get("name"));
        assertEquals("Code is required", ex.getErrors().get("code"));
    }
}
