package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.dto.tenant.TenantRoleSummaryResponse;
import com.sonixhr.dto.tenant.TenantRoleLookupResponse;
import com.sonixhr.dto.tenant.TenantRoleDeletePreviewResponse;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.enums.IndianState;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class TenantRoleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantPermissionRepository permissionRepository;

    private String tokenHeader;
    private Long adminEmployeeId;

    @BeforeEach
    public void setUp() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Role Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@roletest.com";

        // Register Tenant
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
        this.adminEmployeeId = regResponse.getSuperAdminEmployeeId();

        // Login
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        this.tokenHeader = "Bearer " + loginResponse.getAccessToken();
    }

    @Test
    public void testRoleLifecycleAndEndpoints() throws Exception {
        // Fetch pre-seeded permissions
        List<TenantPermission> permissions = permissionRepository.findAll();
        assertFalse(permissions.isEmpty(), "Permissions should be pre-seeded");
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(3)
                .collect(Collectors.toSet());

        // 1. CREATE ROLE
        TenantRoleCreateRequest roleCreate = TenantRoleCreateRequest.builder()
                .name("Technical Lead")
                .description("Leads technical architecture and team development")
                .isDefault(false)
                .permissionIds(permIds)
                .category("DEVELOPMENT")
                .priority(70)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TenantRoleResponse.class);
        assertNotNull(created.getId());
        assertEquals("Technical Lead", created.getName());

        Long roleId = created.getId();

        // 2. GET ROLE BY ID
        MvcResult getResult = mockMvc.perform(get("/api/tenant/roles/" + roleId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        TenantRoleResponse retrieved = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), TenantRoleResponse.class);
        assertEquals(roleId, retrieved.getId());
        assertEquals("Technical Lead", retrieved.getName());

        // 3. UPDATE ROLE DETAILS
        TenantRoleCreateRequest roleUpdate = TenantRoleCreateRequest.builder()
                .name("Principal Engineer")
                .description("Leads technical vision and enterprise architecture")
                .isDefault(false)
                .category("DEVELOPMENT")
                .priority(90)
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/tenant/roles/" + roleId)
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleUpdate)))
                .andExpect(status().isOk())
                .andReturn();

        TenantRoleResponse updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), TenantRoleResponse.class);
        assertEquals("Principal Engineer", updated.getName());

        // 4. UPDATE ROLE PERMISSIONS
        Set<Long> newPermIds = permissions.stream()
                .map(TenantPermission::getId)
                .skip(3)
                .limit(2)
                .collect(Collectors.toSet());

        mockMvc.perform(put("/api/tenant/roles/" + roleId + "/permissions")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPermIds)))
                .andExpect(status().isOk());

        // 5. GET ALL ROLES
        mockMvc.perform(get("/api/tenant/roles")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 6. GET ROLE LOOKUP
        mockMvc.perform(get("/api/tenant/roles/lookup")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 7. GET DEFAULT ROLES
        mockMvc.perform(get("/api/tenant/roles/default")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 8. GET USERS BY ROLE
        mockMvc.perform(get("/api/tenant/roles/" + roleId + "/users")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 9. ASSIGN ROLE TO USER
        // Let's assign our created role to the superAdmin employee
        mockMvc.perform(post("/api/tenant/roles/" + roleId + "/users/" + adminEmployeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 10. REMOVE ROLE FROM USER
        mockMvc.perform(delete("/api/tenant/roles/" + roleId + "/users/" + adminEmployeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // 11. SET DEFAULT ROLE
        mockMvc.perform(post("/api/tenant/roles/" + roleId + "/default")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 11b. Set another role back to default so we can delete our custom role
        MvcResult allRolesResult = mockMvc.perform(get("/api/tenant/roles")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();
        List<TenantRoleResponse> rolesList = objectMapper.readValue(
                allRolesResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TenantRoleResponse.class));
        Long otherRoleId = rolesList.stream()
                .filter(r -> !r.getId().equals(roleId))
                .map(TenantRoleResponse::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected other roles to exist"));

        mockMvc.perform(post("/api/tenant/roles/" + otherRoleId + "/default")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 12. DELETE ROLE
        mockMvc.perform(delete("/api/tenant/roles/" + roleId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // Verify deleted role returns 404/NOT_FOUND
        mockMvc.perform(get("/api/tenant/roles/" + roleId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNotFound());

        // Verify deleted role is not in the list of active roles
        MvcResult listResultAfterDelete = mockMvc.perform(get("/api/tenant/roles")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();
        List<TenantRoleSummaryResponse> rolesListAfterDelete = objectMapper.readValue(
                listResultAfterDelete.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TenantRoleSummaryResponse.class));
        boolean containsDeletedRole = rolesListAfterDelete.stream()
                .anyMatch(r -> r.getId().equals(roleId));
        assertFalse(containsDeletedRole, "Deleted role should not be present in the active roles list");

        // Verify deleted role is not in the active lookup list
        MvcResult lookupResultAfterDelete = mockMvc.perform(get("/api/tenant/roles/lookup")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();
        List<TenantRoleLookupResponse> lookupListAfterDelete = objectMapper.readValue(
                lookupResultAfterDelete.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TenantRoleLookupResponse.class));
        boolean containsDeletedRoleInLookup = lookupListAfterDelete.stream()
                .anyMatch(r -> r.getId().equals(roleId));
        assertFalse(containsDeletedRoleInLookup, "Deleted role should not be present in the active roles lookup list");
    }

    @Test
    public void testRoleDeleteWithReassignment() throws Exception {
        List<TenantPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(2)
                .collect(Collectors.toSet());

        // 1. Create Role A
        TenantRoleCreateRequest roleCreateA = TenantRoleCreateRequest.builder()
                .name("Role A")
                .description("Temporary Role A")
                .isDefault(false)
                .permissionIds(permIds)
                .category("TESTING")
                .priority(10)
                .build();

        MvcResult createResultA = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreateA)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse roleA = objectMapper.readValue(
                createResultA.getResponse().getContentAsString(), TenantRoleResponse.class);
        Long roleIdA = roleA.getId();

        // 2. Create Role B
        TenantRoleCreateRequest roleCreateB = TenantRoleCreateRequest.builder()
                .name("Role B")
                .description("Temporary Role B")
                .isDefault(false)
                .permissionIds(permIds)
                .category("TESTING")
                .priority(20)
                .build();

        MvcResult createResultB = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreateB)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse roleB = objectMapper.readValue(
                createResultB.getResponse().getContentAsString(), TenantRoleResponse.class);
        Long roleIdB = roleB.getId();

        // 3. Assign Role A to adminEmployeeId
        mockMvc.perform(post("/api/tenant/roles/" + roleIdA + "/users/" + adminEmployeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 4. Try to delete Role A without reassignment -> should return 400 Bad Request
        mockMvc.perform(delete("/api/tenant/roles/" + roleIdA)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isBadRequest());

        // 5. Delete Role A with reassignment to Role B -> should succeed (204 No Content)
        mockMvc.perform(delete("/api/tenant/roles/" + roleIdA)
                        .param("reassignToRoleId", roleIdB.toString())
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // 6. Verify Role A returns 404 Not Found
        mockMvc.perform(get("/api/tenant/roles/" + roleIdA)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNotFound());

        // 7. Verify Role B now has the user assigned
        MvcResult usersResult = mockMvc.perform(get("/api/tenant/roles/" + roleIdB + "/users")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String body = usersResult.getResponse().getContentAsString();
        assertTrue(body.contains("\"id\":" + adminEmployeeId), "User should have been reassigned to Role B");
    }

    @Test
    public void testRoleDeletePreview() throws Exception {
        List<TenantPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(2)
                .collect(Collectors.toSet());

        // 1. Create custom Role C
        TenantRoleCreateRequest roleCreateC = TenantRoleCreateRequest.builder()
                .name("Role C")
                .description("Temporary Role C")
                .isDefault(false)
                .permissionIds(permIds)
                .category("TESTING")
                .priority(30)
                .build();

        MvcResult createResultC = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreateC)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse roleC = objectMapper.readValue(
                createResultC.getResponse().getContentAsString(), TenantRoleResponse.class);
        Long roleIdC = roleC.getId();

        // 2. Assign Role C to adminEmployeeId
        mockMvc.perform(post("/api/tenant/roles/" + roleIdC + "/users/" + adminEmployeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 3. Request Role Delete Preview
        MvcResult previewResult = mockMvc.perform(get("/api/tenant/roles/" + roleIdC + "/delete-preview")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        TenantRoleDeletePreviewResponse preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(), TenantRoleDeletePreviewResponse.class);

        assertEquals(roleIdC, preview.getRoleId());
        assertEquals("Role C", preview.getRoleName());
        assertEquals(1, preview.getAffectedEmployeeCount());
        assertFalse(preview.getAffectedEmployees().isEmpty());
        assertEquals(adminEmployeeId, preview.getAffectedEmployees().get(0).getId());
        
        // Options should list other active roles in the tenant
        assertFalse(preview.getReassignmentOptions().isEmpty());
        boolean containsSelf = preview.getReassignmentOptions().stream()
                .anyMatch(opt -> opt.getId().equals(roleIdC));
        assertFalse(containsSelf, "Reassignment options should not contain the role being deleted");

        assertTrue(preview.isDeletable(), "Custom role with other active options should be deletable");
        assertNull(preview.getValidationMessage());

        // Test delete preview on default role (should return deletable=false, validationMessage rather than throwing exception)
        MvcResult defaultRolesResult = mockMvc.perform(get("/api/tenant/roles/default")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();
        List<TenantRoleResponse> defaultRoles = objectMapper.readValue(
                defaultRolesResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TenantRoleResponse.class));
        assertFalse(defaultRoles.isEmpty());
        Long defaultRoleId = defaultRoles.get(0).getId();

        MvcResult defaultPreviewResult = mockMvc.perform(get("/api/tenant/roles/" + defaultRoleId + "/delete-preview")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        TenantRoleDeletePreviewResponse defaultPreview = objectMapper.readValue(
                defaultPreviewResult.getResponse().getContentAsString(), TenantRoleDeletePreviewResponse.class);

        assertFalse(defaultPreview.isDeletable(), "Default role should not be deletable");
        assertNotNull(defaultPreview.getValidationMessage());
        assertTrue(defaultPreview.getValidationMessage().contains("Cannot delete the default role"));
    }

    @Test
    public void testRoleDuplicateValidation() throws Exception {
        // Create initial role
        TenantRoleCreateRequest roleCreate = TenantRoleCreateRequest.builder()
                .name("Software Engineer")
                .description("Builds core applications")
                .isDefault(false)
                .permissionIds(Set.of())
                .category("DEVELOPMENT")
                .priority(50)
                .build();

        mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreate)))
                .andExpect(status().isCreated());

        // Try to create another role with the same name -> should fail with name field error in the errors map
        TenantRoleCreateRequest roleDuplicate = TenantRoleCreateRequest.builder()
                .name("Software Engineer")
                .description("Duplicate Role Name")
                .isDefault(false)
                .permissionIds(Set.of())
                .category("DEVELOPMENT")
                .priority(50)
                .build();

        MvcResult duplicateResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleDuplicate)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String duplicateBody = duplicateResult.getResponse().getContentAsString();
        assertTrue(duplicateBody.contains("\"errors\""));
        assertTrue(duplicateBody.contains("\"name\""));
        assertTrue(duplicateBody.contains("Role name already exists"));
    }
}
