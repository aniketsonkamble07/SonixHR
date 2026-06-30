package com.sonixhr.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

@SpringBootTest
@ActiveProfiles("dev")
@SuppressWarnings("unchecked")
public class SecurityUtilsTest {

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Authentication mockEmployeeAuth;
    private Authentication mockPlatformAuth;

    @BeforeEach
    public void setUp() {
        securityUtils.clearAllCaches();
        SecurityContextHolder.clearContext();

        // ThreadLocal cache cleanup
        ThreadLocal<Map<String, Object>> requestCache = (ThreadLocal<Map<String, Object>>) ReflectionTestUtils.getField(securityUtils, "requestCache");
        if (requestCache != null) {
            requestCache.get().clear();
        }

        // Stub Employee Authentication
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_EMPLOYEE"),
                new SimpleGrantedAuthority("ROLE_MANAGER")
        );
        UserDetails principal = new User("employee@sonixhr.com", "password", authorities);
        UsernamePasswordAuthenticationToken empAuth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        
        Map<String, Object> details = new HashMap<>();
        details.put("userType", "EMPLOYEE");
        details.put("tenantId", 2L);
        details.put("employeeId", 123L);
        details.put("employeeCode", "EMP-123");
        empAuth.setDetails(details);
        this.mockEmployeeAuth = empAuth;

        // Stub Platform Authentication
        List<GrantedAuthority> platformAuthorities = List.of(
                new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        UserDetails platformPrincipal = new User("admin@sonixhr.com", "password", platformAuthorities);
        UsernamePasswordAuthenticationToken platAuth = new UsernamePasswordAuthenticationToken(platformPrincipal, null, platformAuthorities);
        
        Map<String, Object> platformDetails = new HashMap<>();
        platformDetails.put("userType", "PLATFORM");
        platAuth.setDetails(platformDetails);
        this.mockPlatformAuth = platAuth;
    }

    @AfterEach
    public void tearDown() {
        securityUtils.clearAllCaches();
        SecurityContextHolder.clearContext();
    }

    private void resetCaches() {
        securityUtils.clearAllCaches();
        ThreadLocal<Map<String, Object>> requestCache = (ThreadLocal<Map<String, Object>>) ReflectionTestUtils.getField(securityUtils, "requestCache");
        if (requestCache != null) {
            requestCache.get().clear();
        }
    }

    // =====================================================
    // 4.1 AUTHENTICATION CONTEXT SCENARIOS
    // =====================================================

    @Test
    public void testAuthenticationContext() {
        // AC-02: Get Current Auth Anonymous/No Authentication
        Assertions.assertNull(securityUtils.getCurrentAuthentication());
        Assertions.assertFalse(securityUtils.isAuthenticated());

        // AC-01 & AC-03: Get Current Auth & Check Authenticated
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();
        Assertions.assertNotNull(securityUtils.getCurrentAuthentication());
        Assertions.assertTrue(securityUtils.isAuthenticated());

        // AC-04: Check Authenticated Anonymous
        UsernamePasswordAuthenticationToken anonAuth = new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(anonAuth);
        resetCaches();
        Assertions.assertFalse(securityUtils.isAuthenticated());

        // AC-05: Get Principal
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();
        Assertions.assertEquals(mockEmployeeAuth.getPrincipal(), securityUtils.getCurrentPrincipal());
    }

    // =====================================================
    // 4.2 ROLE CHECKS SCENARIOS
    // =====================================================

    @Test
    public void testRoleChecks() {
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();

        // RC-02: Has Role - Missing
        Assertions.assertFalse(securityUtils.hasRole("ADMIN"));

        // RC-04: Has Role - Without Prefix
        Assertions.assertTrue(securityUtils.hasRole("EMPLOYEE"));

        // RC-03: Has Role - With Prefix
        Assertions.assertTrue(securityUtils.hasRole("ROLE_EMPLOYEE"));

        // RC-05: Has Any Role - One Match
        Assertions.assertTrue(securityUtils.hasAnyRole("ADMIN", "MANAGER"));

        // RC-06: Has Any Role - No Match
        Assertions.assertFalse(securityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN"));

        // RC-07: Has All Roles - All Present
        Assertions.assertTrue(securityUtils.hasAllRoles("EMPLOYEE", "MANAGER"));

        // RC-08: Has All Roles - Missing One
        Assertions.assertFalse(securityUtils.hasAllRoles("EMPLOYEE", "ADMIN"));

        // Platform User Role Checks
        SecurityContextHolder.getContext().setAuthentication(mockPlatformAuth);
        resetCaches();

        // RC-09: Is Super Admin
        Assertions.assertTrue(securityUtils.isSuperAdmin());

        // RC-10: Is Admin
        Assertions.assertTrue(securityUtils.isAdmin());

        // RC-14: Has Any Admin Role
        Assertions.assertTrue(securityUtils.hasAnyAdminRole());

        // Reset to Employee Auth
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();

        // RC-12: Is Manager
        Assertions.assertTrue(securityUtils.isManager());

        // RC-13: Is Employee
        Assertions.assertTrue(securityUtils.isEmployee());

        // RC-11: Is HR (not assigned)
        Assertions.assertFalse(securityUtils.isHR());
    }

    // =====================================================
    // 4.3 USER TYPE CHECKS SCENARIOS
    // =====================================================

    @Test
    public void testUserTypeChecks() {
        // UT-04: Get User Type - Null
        Assertions.assertNull(securityUtils.getCurrentUserType());

        // UT-02 & UT-06: Get User Type Employee & Is Employee User
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();
        Assertions.assertEquals("EMPLOYEE", securityUtils.getCurrentUserType());
        Assertions.assertTrue(securityUtils.isEmployeeUser());
        Assertions.assertFalse(securityUtils.isPlatformUser());

        // UT-01 & UT-05: Get User Type Platform & Is Platform User
        SecurityContextHolder.getContext().setAuthentication(mockPlatformAuth);
        resetCaches();
        Assertions.assertEquals("PLATFORM", securityUtils.getCurrentUserType());
        Assertions.assertTrue(securityUtils.isPlatformUser());
        Assertions.assertFalse(securityUtils.isEmployeeUser());

        // UT-03 & UT-07: Get User Type Tenant & Is Tenant User
        UsernamePasswordAuthenticationToken tenantAuth = new UsernamePasswordAuthenticationToken(mockEmployeeAuth.getPrincipal(), null, mockEmployeeAuth.getAuthorities());
        Map<String, Object> tenantDetails = new HashMap<>();
        tenantDetails.put("userType", "TENANT");
        tenantAuth.setDetails(tenantDetails);
        SecurityContextHolder.getContext().setAuthentication(tenantAuth);
        resetCaches();

        Assertions.assertEquals("TENANT", securityUtils.getCurrentUserType());
        Assertions.assertTrue(securityUtils.isTenantUser());
    }

    // =====================================================
    // 4.4 TENANT CONTEXT EXTRACTION SCENARIOS
    // =====================================================

    @Test
    public void testTenantContextExtraction() {
        // TC-04: Get Tenant ID - Null
        Assertions.assertNull(securityUtils.getCurrentTenantId());

        // TC-01: Get Tenant ID - Long
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();
        Assertions.assertEquals(2L, securityUtils.getCurrentTenantId());

        // TC-02: Get Tenant ID - Integer
        Map<String, Object> integerDetails = new HashMap<>((Map<String, Object>) mockEmployeeAuth.getDetails());
        integerDetails.put("tenantId", 2);
        ((UsernamePasswordAuthenticationToken) mockEmployeeAuth).setDetails(integerDetails);
        resetCaches();
        Assertions.assertEquals(2L, securityUtils.getCurrentTenantId());

        // TC-03: Get Tenant ID - String
        Map<String, Object> stringDetails = new HashMap<>((Map<String, Object>) mockEmployeeAuth.getDetails());
        stringDetails.put("tenantId", "2");
        ((UsernamePasswordAuthenticationToken) mockEmployeeAuth).setDetails(stringDetails);
        resetCaches();
        Assertions.assertEquals(2L, securityUtils.getCurrentTenantId());

        // TC-05: Get Employee ID
        Assertions.assertEquals(123L, securityUtils.getCurrentEmployeeId());

        // TC-06: Get Employee Code
        Assertions.assertEquals("EMP-123", securityUtils.getCurrentEmployeeCode());
    }

    // =====================================================
    // 4.5 CACHING SCENARIOS
    // =====================================================

    @Test
    public void testCachingScenarios() {
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();

        // CS-01: Request Cache -> check request cache holds value
        String emailFirst = securityUtils.getCurrentUserEmail();
        ThreadLocal<Map<String, Object>> requestCache = (ThreadLocal<Map<String, Object>>) ReflectionTestUtils.getField(securityUtils, "requestCache");
        Assertions.assertNotNull(requestCache);
        Assertions.assertEquals(emailFirst, requestCache.get().get("email"));

        // CS-02: Local Cache -> verify local cache hit
        String emailCached = securityUtils.getCurrentUserEmailCached();
        Assertions.assertEquals(emailFirst, emailCached);

        Map<String, Object> localCache = (Map<String, Object>) ReflectionTestUtils.getField(securityUtils, "localCache");
        Assertions.assertTrue(localCache.containsKey("EMPLOYEE:2:employee@sonixhr.com"));

        // CS-06: Permission Cache
        Assertions.assertFalse(securityUtils.hasPermission("LEAVE_APPROVE"));
        Map<String, Boolean> permissionCheckCache = (Map<String, Boolean>) ReflectionTestUtils.getField(securityUtils, "permissionCheckCache");
        Assertions.assertNotNull(permissionCheckCache);

        // CS-04: Cache Invalidation - User
        securityUtils.invalidateUserCache("employee@sonixhr.com");
        resetCaches();
        Assertions.assertFalse(localCache.containsKey("EMPLOYEE:2:employee@sonixhr.com"));

        // CS-05: Cache Invalidation - All
        securityUtils.getCurrentUserEmailCached();
        Assertions.assertTrue(localCache.containsKey("EMPLOYEE:2:employee@sonixhr.com"));
        securityUtils.clearAllCaches();
        resetCaches();
        Assertions.assertFalse(localCache.containsKey("EMPLOYEE:2:employee@sonixhr.com"));
    }

    @Test
    public void testCacheDisabledScenarios() {
        SecurityContextHolder.getContext().setAuthentication(mockEmployeeAuth);
        resetCaches();

        // Set cacheEnabled to false
        ReflectionTestUtils.setField(securityUtils, "cacheEnabled", false);
        try {
            // Check that it gets the email correctly
            String email = securityUtils.getCurrentUserEmailCached();
            Assertions.assertEquals("employee@sonixhr.com", email);

            // localCache should NOT contain the key because cacheEnabled is false
            Map<String, Object> localCache = (Map<String, Object>) ReflectionTestUtils.getField(securityUtils, "localCache");
            Assertions.assertFalse(localCache.containsKey("EMPLOYEE:2:employee@sonixhr.com"));

            // Check role cache and permission cache are not caching
            securityUtils.hasRole("ROLE_EMPLOYEE");
            Map<String, Boolean> roleCheckCache = (Map<String, Boolean>) ReflectionTestUtils.getField(securityUtils, "roleCheckCache");
            Assertions.assertTrue(roleCheckCache.isEmpty());

            securityUtils.hasPermission("LEAVE_APPROVE");
            Map<String, Boolean> permissionCheckCache = (Map<String, Boolean>) ReflectionTestUtils.getField(securityUtils, "permissionCheckCache");
            Assertions.assertTrue(permissionCheckCache.isEmpty());
        } finally {
            // Restore cacheEnabled
            ReflectionTestUtils.setField(securityUtils, "cacheEnabled", true);
        }
    }
}
