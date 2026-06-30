package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

@SpringBootTest
@ActiveProfiles("dev")
public class PermissionEvaluatorTest {

    @Autowired
    private CustomPermissionEvaluator permissionEvaluator;

    @Autowired
    private PermissionService permissionService;

    private Employee mockEmployee;
    private PlatformUser mockPlatformUser;
    private Authentication employeeAuth;
    private Authentication platformAuth;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.clearContext();

        // Setup mock Employee
        mockEmployee = Mockito.mock(Employee.class);
        Mockito.when(mockEmployee.getEmail()).thenReturn("employee@sonixhr.com");
        Mockito.when(mockEmployee.getTenantId()).thenReturn(1L);
        Mockito.when(mockEmployee.getId()).thenReturn(100L);
        employeeAuth = new UsernamePasswordAuthenticationToken(mockEmployee, null, Collections.emptyList());

        // Setup mock PlatformUser
        mockPlatformUser = Mockito.mock(PlatformUser.class);
        Mockito.when(mockPlatformUser.getEmail()).thenReturn("admin@sonixhr.com");
        platformAuth = new UsernamePasswordAuthenticationToken(mockPlatformUser, null, Collections.emptyList());
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =====================================================
    // 7.1 GENERAL PERMISSION CHECKS SCENARIOS (PE-01 to PE-06)
    // =====================================================

    @Test
    public void testGeneralPermissionChecks() {
        // PE-01: No Auth principal -> no permissions
        Assertions.assertFalse(permissionEvaluator.hasPermission(null, "LEAVE_APPLY"));
        Assertions.assertFalse(permissionService.hasPermission("LEAVE_APPLY"));

        // Set Employee Auth context
        SecurityContextHolder.getContext().setAuthentication(employeeAuth);

        // PE-02: Permission present
        Mockito.when(mockEmployee.hasPermission("LEAVE_APPLY")).thenReturn(true);
        Assertions.assertTrue(permissionEvaluator.hasPermission(employeeAuth, "LEAVE_APPLY"));
        Assertions.assertTrue(permissionService.hasPermission("LEAVE_APPLY"));

        // PE-03: Permission missing
        Mockito.when(mockEmployee.hasPermission("LEAVE_APPROVE")).thenReturn(false);
        Assertions.assertFalse(permissionEvaluator.hasPermission(employeeAuth, "LEAVE_APPROVE"));
        Assertions.assertFalse(permissionService.hasPermission("LEAVE_APPROVE"));

        // PE-04: Checked via targetDomainObject hasPermission signature
        Assertions.assertTrue(permissionEvaluator.hasPermission(employeeAuth, new Object(), "LEAVE_APPLY"));

        // PE-05: Checked via serializable targetId signature
        Assertions.assertTrue(permissionEvaluator.hasPermission(employeeAuth, 123L, "Leave", "LEAVE_APPLY"));

        // PE-06: Permission service hasAnyPermission and hasAllPermissions
        Mockito.when(mockEmployee.hasPermission("LEAVE_REJECT")).thenReturn(false);
        Assertions.assertTrue(permissionService.hasAnyPermission("LEAVE_REJECT", "LEAVE_APPLY"));
        Assertions.assertFalse(permissionService.hasAllPermissions("LEAVE_APPLY", "LEAVE_REJECT"));
    }

    // =====================================================
    // 7.2 LEAVE SECURITY SCENARIOS (LS-01 to LS-10)
    // =====================================================

    @Test
    public void testLeaveSecurityScenarios() {
        // Set Employee Auth context
        SecurityContextHolder.getContext().setAuthentication(employeeAuth);

        // LS-01: Employee can apply for leave
        Mockito.when(mockEmployee.hasPermission("LEAVE_APPLY")).thenReturn(true);
        Assertions.assertTrue(permissionService.hasPermission("LEAVE_APPLY"));

        // LS-02: Employee cannot approve leave without permission
        Mockito.when(mockEmployee.hasPermission("LEAVE_APPROVE")).thenReturn(false);
        Assertions.assertFalse(permissionService.hasPermission("LEAVE_APPROVE"));

        // LS-03: Manager can approve leave
        Mockito.when(mockEmployee.hasPermission("LEAVE_APPROVE")).thenReturn(true);
        Assertions.assertTrue(permissionService.hasPermission("LEAVE_APPROVE"));

        // LS-04: Employee cannot reject leave without permission
        Mockito.when(mockEmployee.hasPermission("LEAVE_REJECT")).thenReturn(false);
        Assertions.assertFalse(permissionService.hasPermission("LEAVE_REJECT"));

        // LS-05: Manager can reject leave
        Mockito.when(mockEmployee.hasPermission("LEAVE_REJECT")).thenReturn(true);
        Assertions.assertTrue(permissionService.hasPermission("LEAVE_REJECT"));

        // Switch to Platform User Auth context
        SecurityContextHolder.getContext().setAuthentication(platformAuth);

        // LS-06: Platform User permission check
        Mockito.when(mockPlatformUser.hasPermission("LEAVE_VIEW_ALL")).thenReturn(true);
        Assertions.assertTrue(permissionService.hasPermission("LEAVE_VIEW_ALL"));

        // LS-07: Platform User doesn't have employee permission
        Mockito.when(mockPlatformUser.hasPermission("LEAVE_APPLY")).thenReturn(false);
        Assertions.assertFalse(permissionService.hasPermission("LEAVE_APPLY"));

        // LS-08: Super admin checks
        Mockito.when(mockPlatformUser.isSuperAdmin()).thenReturn(true);
        Assertions.assertTrue(permissionService.isSuperAdmin());

        SecurityContextHolder.getContext().setAuthentication(employeeAuth);
        Mockito.when(mockEmployee.isSuperAdmin()).thenReturn(false);
        Assertions.assertFalse(permissionService.isSuperAdmin());

        // LS-09: Get current employee credentials
        Assertions.assertEquals(100L, permissionService.getCurrentEmployeeId());
        Assertions.assertEquals(1L, permissionService.getCurrentTenantId());

        // LS-10: Get current user type
        Assertions.assertEquals("EMPLOYEE", permissionService.getCurrentUserType());
        SecurityContextHolder.getContext().setAuthentication(platformAuth);
        Assertions.assertEquals("PLATFORM", permissionService.getCurrentUserType());
    }
}
