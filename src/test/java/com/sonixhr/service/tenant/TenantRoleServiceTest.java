package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRoleDeleteResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.security.TenantDynamicRoleService;
import com.sonixhr.service.employee.EmployeeService;
import com.sonixhr.service.common.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TenantRoleServiceTest {

    @Mock private TenantRoleRepository roleRepository;
    @Mock private TenantPermissionRepository permissionRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private TenantDynamicRoleService dynamicRoleService;
    @Mock private EmployeeService employeeService;
    @Mock private AuditLogService auditLogService;

    private TenantRoleService roleService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        roleService = new TenantRoleService(
                roleRepository,
                permissionRepository,
                employeeRepository,
                dynamicRoleService,
                employeeService,
                auditLogService
        );
    }

    @Test
    public void testDeleteRole_ZeroEmployees_HardDelete() {
        Long roleId = 1L;
        Long tenantId = 10L;

        TenantRole role = new TenantRole();
        role.setId(roleId);
        role.setTenantId(tenantId);
        role.setDefault(false);

        when(roleRepository.findByIdAndTenantIdWithPermissions(roleId, tenantId)).thenReturn(Optional.of(role));
        when(roleRepository.countByTenantId(tenantId)).thenReturn(2L);
        when(employeeRepository.findByRolesIdAndTenantId(roleId, tenantId)).thenReturn(new ArrayList<>());

        TenantRoleDeleteResponse response = roleService.deleteRole(roleId, tenantId, false);

        assertTrue(response.isDeleted());
        assertFalse(response.isRequiresConfirmation());
        verify(roleRepository, times(1)).delete(role);
    }

    @Test
    public void testDeleteRole_OneEmployee_NoConfirm_RequiresConfirmation() {
        Long roleId = 1L;
        Long tenantId = 10L;

        TenantRole role = new TenantRole();
        role.setId(roleId);
        role.setTenantId(tenantId);
        role.setDefault(false);

        Employee employee = new Employee();
        employee.setId(100L);
        employee.setFirstName("John");
        employee.setLastName("Doe");

        when(roleRepository.findByIdAndTenantIdWithPermissions(roleId, tenantId)).thenReturn(Optional.of(role));
        when(roleRepository.countByTenantId(tenantId)).thenReturn(2L);
        when(employeeRepository.findByRolesIdAndTenantId(roleId, tenantId)).thenReturn(List.of(employee));

        TenantRoleDeleteResponse response = roleService.deleteRole(roleId, tenantId, false);

        assertFalse(response.isDeleted());
        assertTrue(response.isRequiresConfirmation());
        assertEquals("John Doe", response.getEmployeeName());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    public void testDeleteRole_OneEmployee_WithConfirm_HardDeleteAndRemoveFromEmployee() {
        Long roleId = 1L;
        Long tenantId = 10L;

        TenantRole role = new TenantRole();
        role.setId(roleId);
        role.setTenantId(tenantId);
        role.setDefault(false);

        Employee employee = new Employee();
        employee.setId(100L);
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmail("john.doe@test.com");
        employee.setRoles(new HashSet<>(List.of(role)));

        when(roleRepository.findByIdAndTenantIdWithPermissions(roleId, tenantId)).thenReturn(Optional.of(role));
        when(roleRepository.countByTenantId(tenantId)).thenReturn(2L);
        when(employeeRepository.findByRolesIdAndTenantId(roleId, tenantId)).thenReturn(List.of(employee));

        TenantRoleDeleteResponse response = roleService.deleteRole(roleId, tenantId, true);

        assertTrue(response.isDeleted());
        assertFalse(response.isRequiresConfirmation());
        assertFalse(employee.getRoles().contains(role));
        verify(employeeRepository, times(1)).save(employee);
        verify(roleRepository, times(1)).delete(role);
    }

    @Test
    public void testDeleteRole_MultipleEmployees_ThrowsException() {
        Long roleId = 1L;
        Long tenantId = 10L;

        TenantRole role = new TenantRole();
        role.setId(roleId);
        role.setTenantId(tenantId);
        role.setDefault(false);

        Employee emp1 = new Employee();
        Employee emp2 = new Employee();

        when(roleRepository.findByIdAndTenantIdWithPermissions(roleId, tenantId)).thenReturn(Optional.of(role));
        when(roleRepository.countByTenantId(tenantId)).thenReturn(2L);
        when(employeeRepository.findByRolesIdAndTenantId(roleId, tenantId)).thenReturn(List.of(emp1, emp2));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            roleService.deleteRole(roleId, tenantId, false);
        });

        assertTrue(exception.getMessage().contains("assigned to 2 employees"));
        verify(roleRepository, never()).delete(any());
    }
}
