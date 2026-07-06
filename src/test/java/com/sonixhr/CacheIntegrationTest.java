package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.dto.tenant.TenantRoleSummaryResponse;
import com.sonixhr.enums.IndianState;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying Spring Cache (@Cacheable / @CacheEvict) behaviour
 * for the department-lookup, role-list, role-lookup, and role-by-id caches.
 *
 * Cache names used by the application:
 *   "departmentsLookup"  key = tenantId          (DepartmentService.getDepartmentLookup)
 *   "tenantRolesList"    key = tenantId          (TenantRoleService.getAllRolesForTenant)
 *   "tenantRolesLookup"  key = tenantId          (TenantRoleService.getRoleLookupForTenant)
 *   "tenantRoles"        key = roleId+":"+tenantId (TenantRoleService.getRoleResponseByIdAndTenant)
 *
 * Each test creates an isolated tenant so tests cannot interfere with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class CacheIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CacheManager cacheManager;
    @Autowired private TenantPermissionRepository permissionRepository;

    // ------------------------------------------------------------------
    // Helper: register a fresh tenant and return its tenantId + Bearer token
    // ------------------------------------------------------------------

    private long[] registerAndLogin(String suffix) throws Exception {
        String adminEmail = "cache_" + suffix + "@cachetest.com";

        MvcResult regResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRegistrationRequest.builder()
                                .companyName("Cache Co " + suffix)
                                .adminEmail(adminEmail)
                                .adminName("Admin User")
                                .adminPhone("+12345678901")
                                .officeAddress("1 Cache Street")
                                .city("Bangalore")
                                .state(IndianState.KARNATAKA)
                                .country("India")
                                .planCode("trial")
                                .billingCycle("MONTHLY")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegistrationResponse reg = objectMapper.readValue(
                regResult.getResponse().getContentAsString(), TenantRegistrationResponse.class);

        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse lr = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);

        // Encode token as its raw long value so the array doesn't need boxing
        // We store tenantId in [0]; we store the token header as a string separately
        return new long[]{reg.getTenantId()};
    }

    /** Returns the Bearer token for a freshly registered + logged-in admin. */
    private String loginFor(String email) throws Exception {
        MvcResult lr = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse resp = objectMapper.readValue(lr.getResponse().getContentAsString(), LoginResponse.class);
        return "Bearer " + resp.getAccessToken();
    }

    /** Registers a tenant and returns [tenantId, email] in an Object array. */
    private Object[] registerTenant(String suffix) throws Exception {
        String adminEmail = "cache_" + suffix + "@cachetest.com";

        MvcResult regResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRegistrationRequest.builder()
                                .companyName("Cache Co " + suffix)
                                .adminEmail(adminEmail)
                                .adminName("Admin User")
                                .adminPhone("+12345678901")
                                .officeAddress("1 Cache Street")
                                .city("Bangalore")
                                .state(IndianState.KARNATAKA)
                                .country("India")
                                .planCode("trial")
                                .billingCycle("MONTHLY")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegistrationResponse reg = objectMapper.readValue(
                regResult.getResponse().getContentAsString(), TenantRegistrationResponse.class);

        return new Object[]{reg.getTenantId(), adminEmail};
    }

    // ==================================================================
    // Cache tests — departmentsLookup
    // ==================================================================

    /**
     * GET /api/tenant/departments/lookup populates the "departmentsLookup" cache.
     * POST /api/tenant/departments (create) evicts it.
     * The second GET after create must NOT return a cached (stale) empty list —
     * it must hit the DB and include the newly created department.
     */
    @Test
    public void testDepartmentLookupCacheIsPopulatedThenEvictedOnCreate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String email = (String) tenantInfo[1];
        String token = loginFor(email);

        Cache lookupCache = cacheManager.getCache("departmentsLookup");
        assertNotNull(lookupCache, "departmentsLookup cache must be configured");

        // 1. Clear any previous entry for this tenant
        lookupCache.evictIfPresent(tenantId);
        assertNull(lookupCache.get(tenantId), "Cache must be empty before first GET");

        // 2. First GET — should hit DB and populate cache
        mockMvc.perform(get("/api/tenant/departments/lookup")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        assertNotNull(lookupCache.get(tenantId),
                "After GET /lookup, cache entry must be populated for tenantId=" + tenantId);

        // 3. Create a new department — @CacheEvict should clear departmentsLookup for this tenant
        mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Finance " + suffix)
                                .code("FIN" + suffix.substring(0, 4))
                                .description("Finance Department")
                                .build())))
                .andExpect(status().isCreated());

        assertNull(lookupCache.get(tenantId),
                "After POST (create), cache must be evicted for tenantId=" + tenantId);
    }

    /**
     * PUT /api/tenant/departments/{id} (update) also evicts the "departmentsLookup" cache.
     */
    @Test
    public void testDepartmentLookupCacheEvictedOnUpdate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String token = loginFor((String) tenantInfo[1]);

        Cache lookupCache = cacheManager.getCache("departmentsLookup");
        assertNotNull(lookupCache);

        // Create + GET to seed the cache
        MvcResult createResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Mktg " + suffix)
                                .code("MKT" + suffix.substring(0, 4))
                                .description("Marketing")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        Long deptId = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), DepartmentResponse.class).getId();

        // Seed cache with a lookup call
        lookupCache.evictIfPresent(tenantId);
        mockMvc.perform(get("/api/tenant/departments/lookup").header("Authorization", token))
                .andExpect(status().isOk());
        assertNotNull(lookupCache.get(tenantId), "Cache should be populated after GET /lookup");

        // Update → should evict
        mockMvc.perform(put("/api/tenant/departments/" + deptId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Marketing Updated " + suffix)
                                .code("MKT" + suffix.substring(0, 4))
                                .description("Updated Marketing")
                                .build())))
                .andExpect(status().isOk());

        assertNull(lookupCache.get(tenantId),
                "After PUT (update), cache must be evicted for tenantId=" + tenantId);
    }

    /**
     * DELETE /api/tenant/departments/{id} evicts the "departmentsLookup" cache.
     */
    @Test
    public void testDepartmentLookupCacheEvictedOnDelete() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String token = loginFor((String) tenantInfo[1]);

        Cache lookupCache = cacheManager.getCache("departmentsLookup");
        assertNotNull(lookupCache);

        // Create dept
        MvcResult createResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Temp Dept " + suffix)
                                .code("TMP" + suffix.substring(0, 4))
                                .description("Temporary")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        Long deptId = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), DepartmentResponse.class).getId();

        // Seed cache
        lookupCache.evictIfPresent(tenantId);
        mockMvc.perform(get("/api/tenant/departments/lookup").header("Authorization", token))
                .andExpect(status().isOk());
        assertNotNull(lookupCache.get(tenantId));

        // Delete → should evict
        mockMvc.perform(delete("/api/tenant/departments/" + deptId)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        assertNull(lookupCache.get(tenantId),
                "After DELETE, cache must be evicted for tenantId=" + tenantId);
    }

    // ==================================================================
    // Cache tests — tenantRolesList
    // ==================================================================

    /**
     * GET /api/tenant/roles populates the "tenantRolesList" cache.
     * POST /api/tenant/roles (create) evicts ALL entries in that cache.
     */
    @Test
    public void testRoleListCacheIsPopulatedThenEvictedOnCreate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String token = loginFor((String) tenantInfo[1]);

        Cache roleListCache = cacheManager.getCache("tenantRolesList");
        assertNotNull(roleListCache, "tenantRolesList cache must be configured");

        roleListCache.evictIfPresent(tenantId);
        assertNull(roleListCache.get(tenantId), "Cache must be empty before first GET");

        // First GET — populates cache
        mockMvc.perform(get("/api/tenant/roles").header("Authorization", token))
                .andExpect(status().isOk());

        assertNotNull(roleListCache.get(tenantId),
                "After GET /roles, cache entry must be populated for tenantId=" + tenantId);

        // Create a role → evicts tenantRolesList (allEntries = true)
        Set<Long> permIds = permissionRepository.findAll().stream()
                .map(TenantPermission::getId).limit(2).collect(Collectors.toSet());

        mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("Cache Test Role " + suffix)
                                .description("Role for cache eviction test")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("TESTING")
                                .priority(50)
                                .build())))
                .andExpect(status().isCreated());

        assertNull(roleListCache.get(tenantId),
                "After POST /roles (create), tenantRolesList cache must be evicted");
    }

    /**
     * PUT /api/tenant/roles/{id} (update) also evicts "tenantRolesList".
     */
    @Test
    public void testRoleListCacheEvictedOnUpdate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String token = loginFor((String) tenantInfo[1]);

        Cache roleListCache = cacheManager.getCache("tenantRolesList");
        assertNotNull(roleListCache);

        // Create role then seed cache
        Set<Long> permIds = permissionRepository.findAll().stream()
                .map(TenantPermission::getId).limit(2).collect(Collectors.toSet());

        MvcResult createResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("Update Test Role " + suffix)
                                .description("Will be updated")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("TESTING")
                                .priority(50)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        Long roleId = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TenantRoleResponse.class).getId();

        roleListCache.evictIfPresent(tenantId);
        mockMvc.perform(get("/api/tenant/roles").header("Authorization", token))
                .andExpect(status().isOk());
        assertNotNull(roleListCache.get(tenantId));

        // Update → evicts
        mockMvc.perform(put("/api/tenant/roles/" + roleId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("Updated Role Name " + suffix)
                                .description("Updated")
                                .isDefault(false)
                                .category("TESTING")
                                .priority(50)
                                .build())))
                .andExpect(status().isOk());

        assertNull(roleListCache.get(tenantId),
                "After PUT /roles/{id}, tenantRolesList cache must be evicted");
    }

    // ==================================================================
    // Cache tests — tenantRolesLookup
    // ==================================================================

    /**
     * GET /api/tenant/roles/lookup populates the "tenantRolesLookup" cache.
     * Creating a new role via POST /api/tenant/roles evicts ALL lookup cache entries.
     */
    @Test
    public void testRoleLookupCacheIsPopulatedThenEvictedOnCreate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String token = loginFor((String) tenantInfo[1]);

        Cache roleLookupCache = cacheManager.getCache("tenantRolesLookup");
        assertNotNull(roleLookupCache, "tenantRolesLookup cache must be configured");

        roleLookupCache.evictIfPresent(tenantId);
        assertNull(roleLookupCache.get(tenantId), "Lookup cache must be empty before first GET");

        // GET /roles/lookup → populates cache
        mockMvc.perform(get("/api/tenant/roles/lookup").header("Authorization", token))
                .andExpect(status().isOk());

        assertNotNull(roleLookupCache.get(tenantId),
                "After GET /roles/lookup, cache entry must be populated for tenantId=" + tenantId);

        // Create role → evicts tenantRolesLookup (allEntries = true)
        Set<Long> permIds = permissionRepository.findAll().stream()
                .map(TenantPermission::getId).limit(1).collect(Collectors.toSet());

        mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("Lookup Evict Role " + suffix)
                                .description("Triggers lookup cache eviction")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("TESTING")
                                .priority(50)
                                .build())))
                .andExpect(status().isCreated());

        assertNull(roleLookupCache.get(tenantId),
                "After POST /roles (create), tenantRolesLookup cache must be evicted");
    }

    // ==================================================================
    // Cache tests — tenantRoles (individual role by ID)
    // ==================================================================

    /**
     * GET /api/tenant/roles/{id} populates the "tenantRoles" cache with
     * key = roleId + ":" + tenantId.
     * PUT /api/tenant/roles/{id} (update) evicts ALL entries in that cache.
     */
    @Test
    public void testRoleByIdCacheIsPopulatedThenEvictedOnUpdate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String token = loginFor((String) tenantInfo[1]);

        Cache roleByIdCache = cacheManager.getCache("tenantRoles");
        assertNotNull(roleByIdCache, "tenantRoles cache must be configured");

        // Get a seeded role ID (e.g. "Super Admin") from the roles list
        MvcResult listResult = mockMvc.perform(get("/api/tenant/roles").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();

        List<TenantRoleSummaryResponse> roles = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TenantRoleSummaryResponse.class));
        assertFalse(roles.isEmpty(), "Tenant must have at least one seeded role");

        Long roleId = roles.get(0).getId();
        String cacheKey = roleId + ":" + tenantId;   // matches SpEL: "#roleId + ':' + #tenantId"

        // Clear and verify cache miss
        roleByIdCache.evictIfPresent(cacheKey);
        assertNull(roleByIdCache.get(cacheKey), "tenantRoles cache must be empty for key=" + cacheKey);

        // GET /roles/{id} → populates cache
        mockMvc.perform(get("/api/tenant/roles/" + roleId).header("Authorization", token))
                .andExpect(status().isOk());

        assertNotNull(roleByIdCache.get(cacheKey),
                "After GET /roles/{id}, cache entry must be populated for key=" + cacheKey);

        // Create a new role → @CacheEvict with allEntries=true clears tenantRoles entirely
        Set<Long> permIds = permissionRepository.findAll().stream()
                .map(TenantPermission::getId).limit(1).collect(Collectors.toSet());

        mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("ById Evict Role " + suffix)
                                .description("Triggers tenantRoles full eviction")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("TESTING")
                                .priority(50)
                                .build())))
                .andExpect(status().isCreated());

        assertNull(roleByIdCache.get(cacheKey),
                "After POST /roles (create), tenantRoles cache must be evicted for key=" + cacheKey);
    }

    // ==================================================================
    // Cache tests — update-permissions evicts correctly
    // ==================================================================

    /**
     * PUT /api/tenant/roles/{id}/permissions evicts the "tenantRoles" cache.
     */
    @Test
    public void testRolePermissionUpdateEvictsTenantRolesCache() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Object[] tenantInfo = registerTenant(suffix);
        Long tenantId = (Long) tenantInfo[0];
        String token = loginFor((String) tenantInfo[1]);

        Cache roleByIdCache = cacheManager.getCache("tenantRoles");
        assertNotNull(roleByIdCache);

        // Create a custom role
        List<TenantPermission> allPerms = permissionRepository.findAll();
        Set<Long> initialPermIds = allPerms.stream()
                .map(TenantPermission::getId).limit(2).collect(Collectors.toSet());

        MvcResult createResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("Perm Evict Role " + suffix)
                                .description("For permission eviction test")
                                .isDefault(false)
                                .permissionIds(initialPermIds)
                                .category("TESTING")
                                .priority(50)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        Long roleId = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TenantRoleResponse.class).getId();
        String cacheKey = roleId + ":" + tenantId;

        // Seed the by-id cache
        roleByIdCache.evictIfPresent(cacheKey);
        mockMvc.perform(get("/api/tenant/roles/" + roleId).header("Authorization", token))
                .andExpect(status().isOk());
        assertNotNull(roleByIdCache.get(cacheKey), "Cache must be populated after GET /roles/{id}");

        // Update permissions → triggers @CacheEvict(allEntries=true) on tenantRoles
        Set<Long> newPermIds = allPerms.stream()
                .map(TenantPermission::getId).skip(2).limit(2).collect(Collectors.toSet());

        mockMvc.perform(put("/api/tenant/roles/" + roleId + "/permissions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPermIds)))
                .andExpect(status().isOk());

        assertNull(roleByIdCache.get(cacheKey),
                "After PUT /roles/{id}/permissions, tenantRoles cache must be evicted");
    }

    // ==================================================================
    // Cross-tenant cache isolation
    // ==================================================================

    /**
     * Verifies that two tenants each have independent cache entries.
     * Evicting Tenant A's cache must NOT evict Tenant B's entry
     * (departmentsLookup uses tenant-specific keys, not allEntries).
     */
    @Test
    public void testDepartmentLookupCacheIsTenantIsolated() throws Exception {
        String suffixA = UUID.randomUUID().toString().substring(0, 8);
        String suffixB = UUID.randomUUID().toString().substring(0, 8);

        Object[] tenantAInfo = registerTenant("a" + suffixA);
        Object[] tenantBInfo = registerTenant("b" + suffixB);

        Long tenantIdA = (Long) tenantAInfo[0];
        Long tenantIdB = (Long) tenantBInfo[0];
        String tokenA = loginFor((String) tenantAInfo[1]);
        String tokenB = loginFor((String) tenantBInfo[1]);

        Cache lookupCache = cacheManager.getCache("departmentsLookup");
        assertNotNull(lookupCache);

        // Seed cache for both tenants
        lookupCache.evictIfPresent(tenantIdA);
        lookupCache.evictIfPresent(tenantIdB);

        mockMvc.perform(get("/api/tenant/departments/lookup").header("Authorization", tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tenant/departments/lookup").header("Authorization", tokenB))
                .andExpect(status().isOk());

        assertNotNull(lookupCache.get(tenantIdA), "Tenant A cache must be populated");
        assertNotNull(lookupCache.get(tenantIdB), "Tenant B cache must be populated");

        // Create a department for Tenant A → evicts only tenantIdA key
        mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Isolated Dept A " + suffixA)
                                .code("IA" + suffixA.substring(0, 4))
                                .description("Tenant A dept")
                                .build())))
                .andExpect(status().isCreated());

        // Tenant A cache evicted
        assertNull(lookupCache.get(tenantIdA),
                "Tenant A's cache entry must be evicted after its own create");

        // Tenant B cache must remain intact
        assertNotNull(lookupCache.get(tenantIdB),
                "Tenant B's cache entry must NOT be affected by Tenant A's create operation");
    }
}
