package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.enums.IndianState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class TokenBlacklistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testTenantUserLogoutBlacklistsToken() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Blacklist Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@blacklisttest.com";

        // 1. Register Tenant
        TenantRegistrationRequest regRequest = TenantRegistrationRequest.builder()
                .companyName(companyName)
                .adminEmail(adminEmail)
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("123 Test Street")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .planCode("trial")
                .billingCycle("MONTHLY")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegistrationResponse regResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(), TenantRegistrationResponse.class);

        // 2. Login
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String tokenHeader = "Bearer " + loginResponse.getAccessToken();

        // 3. Make a request to a protected endpoint -> Expect 200 OK
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 4. Logout (Blacklist token)
        mockMvc.perform(post("/api/tenant/auth/logout")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 5. Try the same request -> Expect 401 Unauthorized because the token is blacklisted
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testPlatformUserLogoutBlacklistsToken() throws Exception {
        // 1. Login as Platform Super Admin
        LoginRequest loginRequest = new LoginRequest("admin@sonixhr.com", "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String tokenHeader = "Bearer " + loginResponse.getAccessToken();

        // 2. Make a request to platform tenants -> Expect 200 OK
        mockMvc.perform(get("/api/platform/tenants")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 3. Logout
        mockMvc.perform(post("/api/platform/auth/logout")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 4. Try request again -> Expect 401 Unauthorized
        mockMvc.perform(get("/api/platform/tenants")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isUnauthorized());
    }

    @Autowired
    private com.sonixhr.security.RateLimiterService rateLimiterService;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    @Test
    public void testRedisConnectionAndOperations() {
        String testKey = "sonixhr:test:redis:key:" + UUID.randomUUID().toString();
        String testValue = "redis-is-active-and-healthy";

        try {
            // Write
            redisTemplate.opsForValue().set(testKey, testValue);

            // Read
            String retrievedValue = redisTemplate.opsForValue().get(testKey);
            org.junit.jupiter.api.Assertions.assertEquals(testValue, retrievedValue, "Value retrieved from Redis must match the value written");

            // Exists check
            Boolean existsBefore = redisTemplate.hasKey(testKey);
            org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(existsBefore), "Redis must confirm the key exists");

            // Delete
            redisTemplate.delete(testKey);

            // Exists check after delete
            Boolean existsAfter = redisTemplate.hasKey(testKey);
            org.junit.jupiter.api.Assertions.assertFalse(Boolean.TRUE.equals(existsAfter), "Redis key must be deleted successfully");

        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail("Redis operations failed. Is Redis server running? Error: " + e.getMessage());
        }
    }

    @BeforeEach
    public void setUp() {
        rateLimiterService.reset("login:ip:127.0.0.1");
        rateLimiterService.reset("refresh:ip:127.0.0.1");
        
        String email = "admin@sonixhr.com";
        String hashedEmail = org.springframework.util.DigestUtils.md5DigestAsHex(email.getBytes());
        rateLimiterService.reset("login:email:" + hashedEmail);
    }

    @Test
    public void testRateLimiterServiceFailsOpenOrSucceeds() {
        try {
            rateLimiterService.checkOrThrow("test-key-" + UUID.randomUUID(), 5, 10);
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail("Rate Limiter should fail open on Redis errors: " + e.getMessage());
        }
    }

    @Test
    public void testTenantUserRefreshTokenWorkflow() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Refresh Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@refreshtest.com";

        // 1. Register Tenant
        TenantRegistrationRequest regRequest = TenantRegistrationRequest.builder()
                .companyName(companyName)
                .adminEmail(adminEmail)
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("123 Test Street")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .planCode("trial")
                .billingCycle("MONTHLY")
                .build();

        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // 2. Login
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String refreshToken = loginResponse.getRefreshToken();
        
        // 3. Refresh Token -> Expect 200 OK
        MvcResult refreshResult = mockMvc.perform(post("/api/tenant/auth/refresh")
                        .param("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse refreshedResponse = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(), LoginResponse.class);
        
        // Verify new access token is valid by hitting a protected endpoint
        String newAccessTokenHeader = "Bearer " + refreshedResponse.getAccessToken();
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", newAccessTokenHeader))
                .andExpect(status().isOk());

        // 4. Try refreshing with an invalid token -> Expect 401 Unauthorized
        mockMvc.perform(post("/api/tenant/auth/refresh")
                        .param("refreshToken", "invalid-refresh-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testPlatformUserRefreshTokenWorkflow() throws Exception {
        // 1. Login as Platform Super Admin
        LoginRequest loginRequest = new LoginRequest("admin@sonixhr.com", "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String refreshToken = loginResponse.getRefreshToken();

        // 2. Refresh Token -> Expect 200 OK
        MvcResult refreshResult = mockMvc.perform(post("/api/platform/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse refreshedResponse = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(), LoginResponse.class);

        // Verify new access token works
        String newAccessTokenHeader = "Bearer " + refreshedResponse.getAccessToken();
        mockMvc.perform(get("/api/platform/tenants")
                        .header("Authorization", newAccessTokenHeader))
                .andExpect(status().isOk());

        // 3. Try refreshing with invalid token -> Expect 401 Unauthorized
        mockMvc.perform(post("/api/platform/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-refresh-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testSessionInvalidatedOnSecondLogin() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Session Inval Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@sessioninval.com";

        // 1. Register Tenant
        TenantRegistrationRequest regRequest = new TenantRegistrationRequest();
        regRequest.setCompanyName(companyName);
        regRequest.setAdminEmail(adminEmail);
        regRequest.setAdminName("Admin User");
        regRequest.setAdminPhone("+12345678902");
        regRequest.setOfficeAddress("123 Test Street");
        regRequest.setCity("Bangalore");
        regRequest.setState(IndianState.KARNATAKA);
        regRequest.setCountry("India");
        regRequest.setPlanCode("trial");
        regRequest.setBillingCycle("MONTHLY");

        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // 2. First Login
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult1 = mockMvc.perform(post("/api/tenant/auth/login")
                        .header("X-Client-Type", "WEB")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse1 = objectMapper.readValue(
                loginResult1.getResponse().getContentAsString(), LoginResponse.class);
        String token1 = loginResponse1.getAccessToken();

        // Verify token1 works
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());

        // 3. Second Login on same client type (WEB)
        MvcResult loginResult2 = mockMvc.perform(post("/api/tenant/auth/login")
                        .header("X-Client-Type", "WEB")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse2 = objectMapper.readValue(
                loginResult2.getResponse().getContentAsString(), LoginResponse.class);
        String token2 = loginResponse2.getAccessToken();

        // 4. Verify token2 works
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk());

        // 5. Verify token1 is now blacklisted/invalidated
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testRefreshTokenEndpointRejectsAccessToken() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Reject Access Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@rejectaccess.com";

        // 1. Register Tenant
        TenantRegistrationRequest regRequest = new TenantRegistrationRequest();
        regRequest.setCompanyName(companyName);
        regRequest.setAdminEmail(adminEmail);
        regRequest.setAdminName("Admin User");
        regRequest.setAdminPhone("+12345678903");
        regRequest.setOfficeAddress("123 Test Street");
        regRequest.setCity("Bangalore");
        regRequest.setState(IndianState.KARNATAKA);
        regRequest.setCountry("India");
        regRequest.setPlanCode("trial");
        regRequest.setBillingCycle("MONTHLY");

        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // 2. Login to get access token
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String accessToken = loginResponse.getAccessToken();

        // 3. Try refreshing using the ACCESS TOKEN instead of a refresh token -> Expect 401 Unauthorized
        mockMvc.perform(post("/api/tenant/auth/refresh")
                        .param("refreshToken", accessToken))
                .andExpect(status().isUnauthorized());
    }
}
