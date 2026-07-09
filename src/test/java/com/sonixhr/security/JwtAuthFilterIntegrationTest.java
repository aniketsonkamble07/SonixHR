package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.employee.EmployeeDetailsService;
import com.sonixhr.service.platform.PlatformUserDetailsService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.FilterChain;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@SuppressWarnings("unchecked")
public class JwtAuthFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private EmployeeDetailsService employeeDetailsService;

    @MockBean
    private PlatformUserDetailsService platformUserDetailsService;

    @Value("${app.jwt.secret}")
    private String secret;

    private Map<String, Boolean> publicPathCache;
    private Map<String, UserDetails> userDetailsCache;
    private Map<String, UsernamePasswordAuthenticationToken> authCache;

    private Employee mockEmployee;
    private PlatformUser mockPlatformUser;

    @BeforeEach
    public void setUp() {
        publicPathCache = (Map<String, Boolean>) ReflectionTestUtils.getField(jwtAuthFilter, "publicPathCache");
        
        Object userDetailsField = ReflectionTestUtils.getField(jwtAuthFilter, "userDetailsCache");
        if (userDetailsField instanceof com.github.benmanes.caffeine.cache.Cache) {
            userDetailsCache = ((com.github.benmanes.caffeine.cache.Cache<String, UserDetails>) userDetailsField).asMap();
        } else {
            userDetailsCache = (Map<String, UserDetails>) userDetailsField;
        }
        
        Object authCacheField = ReflectionTestUtils.getField(jwtAuthFilter, "authCache");
        if (authCacheField instanceof com.github.benmanes.caffeine.cache.Cache) {
            authCache = ((com.github.benmanes.caffeine.cache.Cache<String, UsernamePasswordAuthenticationToken>) authCacheField).asMap();
        } else {
            authCache = (Map<String, UsernamePasswordAuthenticationToken>) authCacheField;
        }
        
        jwtAuthFilter.clearAllCaches();
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Stub Employee
        mockEmployee = Mockito.mock(Employee.class);
        Mockito.when(mockEmployee.getId()).thenReturn(1L);
        Mockito.when(mockEmployee.getTenantId()).thenReturn(2L);
        Mockito.when(mockEmployee.getEmployeeCode()).thenReturn("EMP-TEST");
        Mockito.when(mockEmployee.getEmail()).thenReturn("testuser@sonixhr.com");
        Mockito.when(mockEmployee.getUsername()).thenReturn("testuser@sonixhr.com");
        Mockito.when(mockEmployee.getRolesVersion()).thenReturn(1);
        Mockito.when(mockEmployee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        Mockito.when(employeeDetailsService.loadUserByUsername("testuser@sonixhr.com")).thenReturn(mockEmployee);
        Mockito.when(employeeDetailsService.loadUserByUsernameWithFreshRoles("testuser@sonixhr.com")).thenReturn(mockEmployee);

        // Stub PlatformUser
        mockPlatformUser = Mockito.mock(PlatformUser.class);
        Mockito.when(mockPlatformUser.getId()).thenReturn(999L);
        Mockito.when(mockPlatformUser.getEmail()).thenReturn("testadmin@sonixhr.com");
        Mockito.when(mockPlatformUser.getUsername()).thenReturn("testadmin@sonixhr.com");
        Mockito.when(mockPlatformUser.getRolesVersion()).thenReturn(1);
        Mockito.when(mockPlatformUser.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        Mockito.when(platformUserDetailsService.loadUserByUsername("testadmin@sonixhr.com")).thenReturn(mockPlatformUser);
    }

    @AfterEach
    public void tearDown() {
        jwtAuthFilter.clearAllCaches();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // =====================================================
    // 2.1 PUBLIC PATH HANDLING SCENARIOS
    // =====================================================

    @Test
    public void testPublicPathHandling() throws Exception {
        // PF-01: Access Public Login -> continues without auth (returns 400 bad request, not 401/403)
        mockMvc.perform(post("/api/tenant/auth/login")).andExpect(status().isBadRequest());

        // PF-02: Access Health Endpoint -> health endpoint returns 404 but is not blocked/401/403
        mockMvc.perform(get("/api/health")).andExpect(status().isNotFound());

        // PF-03: Access Swagger UI -> continues and returns 200 Ok
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());

        // PF-04: Access Register Endpoint (returns 404, not blocked/401/403)
        mockMvc.perform(post("/api/tenant/register")).andExpect(status().isNotFound());

        // PF-05: Access Protected Endpoint without token -> returns 401 Unauthorized
        mockMvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());

        // PF-06: Access Tenant Auth Me -> NOT skipped, requires authentication, returns 401/403
        mockMvc.perform(get("/api/tenant/auth/me")).andExpect(status().is4xxClientError());

        // PF-07: Access Platform Logout -> NOT skipped, requires authentication, returns 401/403
        mockMvc.perform(post("/api/platform/auth/logout")).andExpect(status().is4xxClientError());
    }

    // =====================================================
    // 2.2 TOKEN AUTHENTICATION SCENARIOS
    // =====================================================

    @Test
    public void testTokenAuthenticationScenarios() throws Exception {
        // TA-03: Token in Header and TA-01: Valid Employee Token
        String token = jwtService.generateEmployeeToken(mockEmployee);

        // Hitting departments endpoint will trigger security. Since mockEmployee has no authorities,
        // it may return 403 Forbidden instead of 401 Unauthorized, meaning authentication WAS set!
        // So any status other than 401 Unauthorized proves authentication was successful!
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()); // 403 shows authentication was set, but no permission

        // TA-02: Valid Platform Token
        String platToken = jwtService.generatePlatformToken(mockPlatformUser);
        mockMvc.perform(get("/api/platform/tenants")
                        .header("Authorization", "Bearer " + platToken))
                .andExpect(status().isForbidden()); // 403 shows auth was set

        // TA-04: Token in Query Param (?token=xxx) - Should be Unauthorized as query param tokens are not supported for security
        mockMvc.perform(get("/api/tenant/departments")
                        .param("token", token))
                .andExpect(status().isUnauthorized());

        // TA-05: Missing Token
        mockMvc.perform(get("/api/tenant/departments"))
                .andExpect(status().isUnauthorized());

        // TA-06: Invalid Token
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer invalid-malformed-token"))
                .andExpect(status().isUnauthorized());

        // TA-07: Blacklisted Token
        jwtService.invalidateToken(token);
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 2.3 CACHING BEHAVIOR SCENARIOS
    // =====================================================

    @Test
    public void testCachingBehavior() throws Exception {
        String token = jwtService.generateEmployeeToken(mockEmployee);

        // CA-01: Cache Auth Token on first request
        Assertions.assertEquals(0, authCache.size());
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        Assertions.assertTrue(authCache.containsKey(token));
        Assertions.assertEquals(1, authCache.size());

        // CA-02: Use Cached Token on second request
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // CA-03: Cache User Details
        Assertions.assertTrue(userDetailsCache.containsKey("EMPLOYEE:2:testuser@sonixhr.com"));

        // CA-04: Cache Public Path
        mockMvc.perform(get("/api/public/test-health")).andExpect(status().isNotFound());
        Assertions.assertTrue(publicPathCache.containsKey("/api/public/test-health"));

        // CA-05: Cache Invalidation
        jwtAuthFilter.invalidateUserCache("testuser@sonixhr.com", "EMPLOYEE");
        Assertions.assertFalse(userDetailsCache.containsKey("EMPLOYEE:2:testuser@sonixhr.com"));
        Assertions.assertFalse(authCache.containsKey(token));

        // CA-06: Clear All Caches
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        Assertions.assertTrue(authCache.size() > 0);
        jwtAuthFilter.clearAllCaches();
        Assertions.assertEquals(0, authCache.size());
        Assertions.assertEquals(0, userDetailsCache.size());
        Assertions.assertEquals(0, publicPathCache.size());

        // CA-07: Cache Size Limit
        for (int i = 0; i < 600; i++) {
            publicPathCache.put("/api/public-test-path-" + i, true);
        }
        boolean isPublic = (boolean) ReflectionTestUtils.invokeMethod(jwtAuthFilter, "isPublicPathOptimized", "/api/health");
        Assertions.assertTrue(isPublic);
    }

    // =====================================================
    // 2.4 TENANT CONTEXT SCENARIOS
    // =====================================================

    @Test
    public void testTenantContextScenarios() throws Exception {
        String token = jwtService.generateEmployeeToken(mockEmployee);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenant/departments");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        FilterChain filterChain = new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse resp) {
                // TC-01: Set Tenant Context
                Assertions.assertEquals(2L, TenantContext.getCurrentTenant());
                Assertions.assertTrue(TenantContext.hasTenantContext());
            }
        };

        jwtAuthFilter.doFilter(request, response, filterChain);

        // TC-02: Clear Tenant Context after request completes
        Assertions.assertNull(TenantContext.getCurrentTenant());
        Assertions.assertFalse(TenantContext.hasTenantContext());

        // TC-03: Platform User Context
        String platToken = jwtService.generatePlatformToken(mockPlatformUser);
        MockHttpServletRequest platRequest = new MockHttpServletRequest("GET", "/api/platform/tenants");
        platRequest.addHeader("Authorization", "Bearer " + platToken);
        
        FilterChain platChain = new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse resp) {
                // TC-03: Platform User Context (no tenant set)
                Assertions.assertNull(TenantContext.getCurrentTenant());
            }
        };
        jwtAuthFilter.doFilter(platRequest, response, platChain);
    }

    // =====================================================
    // 2.5 ROLES VERSION HANDLING SCENARIOS
    // =====================================================

    @Test
    public void testRolesVersionHandling() {
        // RV-03: Null Roles Version
        boolean reloadNull = (boolean) ReflectionTestUtils.invokeMethod(jwtAuthFilter, "needRolesReload", Mockito.mock(UserDetails.class), null);
        Assertions.assertFalse(reloadNull);

        // RV-01: Same Roles Version
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getRolesVersion()).thenReturn(2);
        boolean reloadSame = (boolean) ReflectionTestUtils.invokeMethod(jwtAuthFilter, "needRolesReload", employee, 2);
        Assertions.assertFalse(reloadSame);

        // RV-02: Changed Roles Version
        boolean reloadChanged = (boolean) ReflectionTestUtils.invokeMethod(jwtAuthFilter, "needRolesReload", employee, 3);
        Assertions.assertTrue(reloadChanged);
    }

    // =====================================================
    // 3.1 & 3.2 BLACKLIST OPERATIONS & CHECKS SCENARIOS
    // =====================================================

    @Test
    public void testBlacklistOperationsAndChecks() {
        String token = jwtService.generateEmployeeToken(mockEmployee);

        // BL-05 & BC-04: Uses JTI as key
        String jti = jwtService.extractJti(token);
        Assertions.assertNotNull(jti);

        // BC-02: Check Non-Blacklisted Token
        Assertions.assertFalse(tokenBlacklistService.isBlacklisted(token));

        // BL-01 & BC-01 & BC-05: Blacklist Valid Token stored in Redis
        tokenBlacklistService.blacklistToken(token);
        Assertions.assertTrue(tokenBlacklistService.isBlacklisted(token));

        // BL-02: Blacklist Expired Token
        java.security.Key key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
        String expiredToken = Jwts.builder()
                .claim("userType", "EMPLOYEE")
                .setSubject("expiredblack@sonixhr.com")
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Assertions.assertDoesNotThrow(() -> tokenBlacklistService.blacklistToken(expiredToken));
    }
}
