package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

@SpringBootTest
@ActiveProfiles("dev")
public class DynamicRoleServiceTest {

    @Autowired
    private PlatformDynamicRoleService platformDynamicRoleService;

    @Autowired
    private TenantDynamicRoleService tenantDynamicRoleService;

    // Autowire real beans to restore them in tearDown
    @Autowired
    private PlatformUserRepository realPlatformUserRepository;

    @Autowired
    private PlatformRoleRepository realPlatformRoleRepository;

    @Autowired
    private EmployeeRepository realEmployeeRepository;

    @Autowired
    private TenantRoleRepository realTenantRoleRepository;

    // Mock repositories for isolation
    private PlatformUserRepository mockPlatformUserRepository;
    private PlatformRoleRepository mockPlatformRoleRepository;
    private EmployeeRepository mockEmployeeRepository;
    private TenantRoleRepository mockTenantRoleRepository;

    private PlatformUser mockPlatformUser;
    private PlatformRole mockPlatformRole;
    private PlatformPermission mockPlatformPermission;

    private Employee mockEmployee;
    private TenantRole mockTenantRole;
    private TenantPermission mockTenantPermission;

    @BeforeEach
    public void setUp() {
        mockPlatformUserRepository = Mockito.mock(PlatformUserRepository.class);
        mockPlatformRoleRepository = Mockito.mock(PlatformRoleRepository.class);
        mockEmployeeRepository = Mockito.mock(EmployeeRepository.class);
        mockTenantRoleRepository = Mockito.mock(TenantRoleRepository.class);

        // Inject mocks into services
        ReflectionTestUtils.setField(platformDynamicRoleService, "platformUserRepository", mockPlatformUserRepository);
        ReflectionTestUtils.setField(platformDynamicRoleService, "platformRoleRepository", mockPlatformRoleRepository);
        ReflectionTestUtils.setField(tenantDynamicRoleService, "employeeRepository", mockEmployeeRepository);
        ReflectionTestUtils.setField(tenantDynamicRoleService, "tenantRoleRepository", mockTenantRoleRepository);

        // Setup Platform Mock Entity Hierarchy
        mockPlatformUser = Mockito.mock(PlatformUser.class);
        Mockito.when(mockPlatformUser.getEmail()).thenReturn("admin@sonixhr.com");
        Mockito.when(mockPlatformUser.getId()).thenReturn(10L);

        mockPlatformRole = Mockito.mock(PlatformRole.class);
        Mockito.when(mockPlatformRole.getName()).thenReturn("SUPER_ADMIN");
        Mockito.when(mockPlatformRole.isActive()).thenReturn(true);
        Mockito.when(mockPlatformRole.getId()).thenReturn(101L);

        mockPlatformPermission = Mockito.mock(PlatformPermission.class);
        Mockito.when(mockPlatformPermission.getPermission()).thenReturn("USER_WRITE");

        Mockito.when(mockPlatformUser.getRoles()).thenReturn(Set.of(mockPlatformRole));
        Mockito.when(mockPlatformRole.getPermissions()).thenReturn(Set.of(mockPlatformPermission));

        Mockito.when(mockPlatformUserRepository.findByEmailWithRoles("admin@sonixhr.com"))
                .thenReturn(Optional.of(mockPlatformUser));
        Mockito.when(mockPlatformUserRepository.findById(10L))
                .thenReturn(Optional.of(mockPlatformUser));
        Mockito.when(mockPlatformRoleRepository.findByIdWithPermissions(101L))
                .thenReturn(Optional.of(mockPlatformRole));

        // Setup Tenant Mock Entity Hierarchy
        mockEmployee = Mockito.mock(Employee.class);
        Mockito.when(mockEmployee.getEmail()).thenReturn("emp@sonixhr.com");
        Mockito.when(mockEmployee.getId()).thenReturn(20L);
        Mockito.when(mockEmployee.getTenantId()).thenReturn(2L);

        mockTenantRole = Mockito.mock(TenantRole.class);
        Mockito.when(mockTenantRole.getName()).thenReturn("MANAGER");
        Mockito.when(mockTenantRole.isActive()).thenReturn(true);
        Mockito.when(mockTenantRole.getId()).thenReturn(201L);

        mockTenantPermission = Mockito.mock(TenantPermission.class);
        Mockito.when(mockTenantPermission.getPermission()).thenReturn("LEAVE_APPROVE");

        Mockito.when(mockEmployee.getRoles()).thenReturn(Set.of(mockTenantRole));
        Mockito.when(mockTenantRole.getPermissions()).thenReturn(Set.of(mockTenantPermission));

        Mockito.when(mockEmployeeRepository.findByEmailAndTenantIdWithRoles("emp@sonixhr.com", 2L))
                .thenReturn(Optional.of(mockEmployee));
        Mockito.when(mockEmployeeRepository.findByIdAndTenantId(20L, 2L))
                .thenReturn(Optional.of(mockEmployee));
        Mockito.when(mockTenantRoleRepository.findByIdWithPermissions(201L))
                .thenReturn(Optional.of(mockTenantRole));
    }

    @AfterEach
    public void tearDown() {
        // Restore real beans in services to keep the context clean
        ReflectionTestUtils.setField(platformDynamicRoleService, "platformUserRepository", realPlatformUserRepository);
        ReflectionTestUtils.setField(platformDynamicRoleService, "platformRoleRepository", realPlatformRoleRepository);
        ReflectionTestUtils.setField(tenantDynamicRoleService, "employeeRepository", realEmployeeRepository);
        ReflectionTestUtils.setField(tenantDynamicRoleService, "tenantRoleRepository", realTenantRoleRepository);
    }

    // =====================================================
    // 8.1 PLATFORM DYNAMIC ROLE SERVICE SCENARIOS
    // =====================================================

    @Test
    public void testPlatformDynamicRoleService() {
        // PR-01: Load user authorities
        Collection<? extends GrantedAuthority> authorities = platformDynamicRoleService.loadUserAuthorities("admin@sonixhr.com");
        Assertions.assertNotNull(authorities);
        Assertions.assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")));
        Assertions.assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("USER_WRITE")));

        // PR-02: Get role permissions
        Set<String> perms = platformDynamicRoleService.getRolePermissions(101L);
        Assertions.assertTrue(perms.contains("USER_WRITE"));

        // PR-03: Has permission
        Assertions.assertTrue(platformDynamicRoleService.hasPermission("admin@sonixhr.com", "USER_WRITE"));

        // PR-04: Has role
        Assertions.assertTrue(platformDynamicRoleService.hasRole("admin@sonixhr.com", "SUPER_ADMIN"));
        Assertions.assertTrue(platformDynamicRoleService.hasRole("admin@sonixhr.com", "ROLE_SUPER_ADMIN"));

        // PR-05: Get user role names
        List<String> roles = platformDynamicRoleService.getUserRoleNames("admin@sonixhr.com");
        Assertions.assertEquals(1, roles.size());
        Assertions.assertEquals("SUPER_ADMIN", roles.get(0));

        // PR-06: Invalidate user authority cache
        Assertions.assertDoesNotThrow(() -> platformDynamicRoleService.invalidateUserAuthorityCache("admin@sonixhr.com"));

        // PR-07: Refresh platform user roles
        Assertions.assertDoesNotThrow(() -> platformDynamicRoleService.refreshUserRoles(10L));
        Mockito.verify(mockPlatformUser, Mockito.times(1)).incrementRolesVersion();

        // PR-08: Get user authority summary
        Map<String, Object> summary = platformDynamicRoleService.getUserAuthoritySummary("admin@sonixhr.com");
        Assertions.assertEquals("admin@sonixhr.com", summary.get("email"));
        Assertions.assertEquals(2, summary.get("totalAuthorities"));

        // PR-09: Preload authorities
        Assertions.assertDoesNotThrow(() -> platformDynamicRoleService.preloadAuthorities(List.of("admin@sonixhr.com")));
    }

    // =====================================================
    // 8.2 TENANT DYNAMIC ROLE SERVICE SCENARIOS
    // =====================================================

    @Test
    public void testTenantDynamicRoleService() {
        // TR-01: Load employee authorities
        Collection<? extends GrantedAuthority> authorities = tenantDynamicRoleService.loadEmployeeAuthorities("emp@sonixhr.com", 2L);
        Assertions.assertNotNull(authorities);
        Assertions.assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")));
        Assertions.assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("LEAVE_APPROVE")));
        Assertions.assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("TENANT_2")));

        // TR-02: Get role permissions
        Set<String> perms = tenantDynamicRoleService.getRolePermissions(201L);
        Assertions.assertTrue(perms.contains("LEAVE_APPROVE"));

        // TR-03: Has permission
        Assertions.assertTrue(tenantDynamicRoleService.hasPermission("emp@sonixhr.com", 2L, "LEAVE_APPROVE"));

        // TR-04: Has role
        Assertions.assertTrue(tenantDynamicRoleService.hasRole("emp@sonixhr.com", 2L, "MANAGER"));

        // TR-05: Get employee role names
        List<String> roles = tenantDynamicRoleService.getEmployeeRoleNames("emp@sonixhr.com", 2L);
        Assertions.assertEquals(1, roles.size());
        Assertions.assertEquals("MANAGER", roles.get(0));

        // TR-06: Invalidate employee authority cache
        Assertions.assertDoesNotThrow(() -> tenantDynamicRoleService.invalidateEmployeeAuthorityCache("emp@sonixhr.com", 2L));

        // TR-07: Invalidate tenant cache
        Assertions.assertDoesNotThrow(() -> tenantDynamicRoleService.invalidateTenantCache(2L));

        // TR-08: Refresh employee roles
        Assertions.assertDoesNotThrow(() -> tenantDynamicRoleService.refreshEmployeeRoles(20L, 2L));
        Mockito.verify(mockEmployee, Mockito.times(1)).incrementRolesVersion();

        // TR-09: Get employee authority summary
        Map<String, Object> summary = tenantDynamicRoleService.getEmployeeAuthoritySummary("emp@sonixhr.com", 2L);
        Assertions.assertEquals("emp@sonixhr.com", summary.get("email"));
        Assertions.assertEquals(2L, summary.get("tenantId"));

        // TR-10: Preload authorities
        Assertions.assertDoesNotThrow(() -> tenantDynamicRoleService.preloadAuthorities(List.of("emp@sonixhr.com"), 2L));
    }
}
