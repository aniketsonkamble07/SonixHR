package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.platform.*;
import com.sonixhr.entity.platform.*;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.repository.platform.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class PlatformRoleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformPermissionRepository permissionRepository;

    @Autowired
    private PlatformRoleRepository roleRepository;

    @Autowired
    private PlatformUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenHeader;
    private Long testUserId;

    @BeforeEach
    public void setUp() throws Exception {
        // Login as Super Admin
        LoginRequest loginRequest = new LoginRequest("admin@sonixhr.com", "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        this.tokenHeader = "Bearer " + loginResponse.getAccessToken();
    }

    @Test
    public void testPlatformRoleLifecycleAndEndpoints() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String roleName = "Technical Architect " + uniqueSuffix;
        String updatedRoleName = "Chief Architect " + uniqueSuffix;

        // Fetch some pre-seeded platform permissions
        List<PlatformPermission> permissions = permissionRepository.findAll();
        assertFalse(permissions.isEmpty(), "Permissions should be pre-seeded");
        Set<Long> permIds = permissions.stream()
                .map(PlatformPermission::getId)
                .limit(3)
                .collect(Collectors.toSet());

        // 1. CREATE ROLE
        PlatformRoleCreateRequest roleCreate = PlatformRoleCreateRequest.builder()
                .name(roleName)
                .description("Manages technical decisions and design")
                .permissionIds(permIds)
                .category("TECHNICAL")
                .priority(85)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/platform/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        PlatformRoleResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), PlatformRoleResponse.class);
        assertNotNull(created.getId());
        assertEquals(roleName, created.getName());

        Long roleId = created.getId();

        // 2. GET ROLE BY ID
        MvcResult getResult = mockMvc.perform(get("/api/platform/roles/" + roleId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        PlatformRoleResponse retrieved = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), PlatformRoleResponse.class);
        assertEquals(roleId, retrieved.getId());
        assertEquals(roleName, retrieved.getName());

        // 3. UPDATE ROLE DETAILS
        PlatformRoleCreateRequest roleUpdate = PlatformRoleCreateRequest.builder()
                .name(updatedRoleName)
                .description("Leads platform technical vision")
                .permissionIds(permIds)
                .category("TECHNICAL")
                .priority(95)
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/platform/roles/" + roleId)
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleUpdate)))
                .andExpect(status().isOk())
                .andReturn();

        PlatformRoleResponse updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), PlatformRoleResponse.class);
        assertEquals(updatedRoleName, updated.getName());

        // 4. UPDATE ROLE PERMISSIONS
        Set<Long> newPermIds = permissions.stream()
                .map(PlatformPermission::getId)
                .skip(3)
                .limit(2)
                .collect(Collectors.toSet());

        mockMvc.perform(put("/api/platform/roles/" + roleId + "/permissions")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPermIds)))
                .andExpect(status().isOk());

        // 5. GET ALL ROLES
        mockMvc.perform(get("/api/platform/roles")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 6. GET ROLE LOOKUP
        mockMvc.perform(get("/api/platform/roles/lookup")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 7. CREATE A TEST USER AND ASSIGN ROLE
        String uniqueEmail = "arch_" + UUID.randomUUID().toString().substring(0, 8) + "@sonixhr.com";
        PlatformRole roleEntity = roleRepository.findById(roleId).orElseThrow();
        PlatformUser testUser = PlatformUser.builder()
                .email(uniqueEmail)
                .password(passwordEncoder.encode("Password@123"))
                .fullName("Test Architect User")
                .designation("Staff Engineer")
                .status(UserStatus.ACTIVE)
                .active(true)
                .roles(new HashSet<>(List.of(roleEntity)))
                .rolesVersion(1)
                .build();
        testUser = userRepository.save(testUser);
        this.testUserId = testUser.getId();

        // 8. GET USERS BY ROLE
        MvcResult usersResult = mockMvc.perform(get("/api/platform/roles/" + roleId + "/users")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();
        String usersBody = usersResult.getResponse().getContentAsString();
        assertTrue(usersBody.contains(uniqueEmail));

        // 9. REMOVE ROLE FROM USER
        mockMvc.perform(delete("/api/platform/roles/" + roleId + "/users/" + testUserId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // 10. ASSIGN ROLE TO USER VIA ENDPOINT
        mockMvc.perform(post("/api/platform/roles/" + roleId + "/users/" + testUserId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // Cleanup user assignment for delete test
        mockMvc.perform(delete("/api/platform/roles/" + roleId + "/users/" + testUserId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // 11. DELETE ROLE
        mockMvc.perform(delete("/api/platform/roles/" + roleId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // Verify deleted role returns 404/NOT_FOUND
        mockMvc.perform(get("/api/platform/roles/" + roleId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNotFound());

        // Cleanup user
        userRepository.delete(userRepository.findById(testUserId).orElseThrow());
    }

    @Test
    public void testPlatformRoleDeleteWithReassignment() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String roleNameA = "Platform Role A " + uniqueSuffix;
        String roleNameB = "Platform Role B " + uniqueSuffix;

        List<PlatformPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(PlatformPermission::getId)
                .limit(2)
                .collect(Collectors.toSet());

        // 1. Create Role A
        PlatformRoleCreateRequest roleCreateA = PlatformRoleCreateRequest.builder()
                .name(roleNameA)
                .description("Temporary Platform Role A")
                .permissionIds(permIds)
                .category("TESTING")
                .priority(10)
                .build();

        MvcResult createResultA = mockMvc.perform(post("/api/platform/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreateA)))
                .andExpect(status().isCreated())
                .andReturn();

        PlatformRoleResponse roleA = objectMapper.readValue(
                createResultA.getResponse().getContentAsString(), PlatformRoleResponse.class);
        Long roleIdA = roleA.getId();

        // 2. Create Role B
        PlatformRoleCreateRequest roleCreateB = PlatformRoleCreateRequest.builder()
                .name(roleNameB)
                .description("Temporary Platform Role B")
                .permissionIds(permIds)
                .category("TESTING")
                .priority(20)
                .build();

        MvcResult createResultB = mockMvc.perform(post("/api/platform/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreateB)))
                .andExpect(status().isCreated())
                .andReturn();

        PlatformRoleResponse roleB = objectMapper.readValue(
                createResultB.getResponse().getContentAsString(), PlatformRoleResponse.class);
        Long roleIdB = roleB.getId();

        // 3. Create a platform user and assign Role A
        String uniqueEmail = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@sonixhr.com";
        PlatformRole roleEntityA = roleRepository.findById(roleIdA).orElseThrow();
        PlatformUser testUser = PlatformUser.builder()
                .email(uniqueEmail)
                .password(passwordEncoder.encode("Password@123"))
                .fullName("Test Platform User A")
                .status(UserStatus.ACTIVE)
                .active(true)
                .roles(new HashSet<>(List.of(roleEntityA)))
                .rolesVersion(1)
                .build();
        testUser = userRepository.save(testUser);
        Long userId = testUser.getId();

        // 4. Try to delete Role A -> should return 400 Bad Request because it is assigned to a user
        mockMvc.perform(delete("/api/platform/roles/" + roleIdA)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isBadRequest());

        // 5. Manually reassign the user to Role B
        PlatformRole roleEntityB = roleRepository.findById(roleIdB).orElseThrow();
        testUser.getRoles().clear();
        testUser.getRoles().add(roleEntityB);
        userRepository.save(testUser);

        // 6. Delete Role A -> should succeed now that no users are assigned
        mockMvc.perform(delete("/api/platform/roles/" + roleIdA)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // 7. Verify Role A returns 404 Not Found (inactive)
        mockMvc.perform(get("/api/platform/roles/" + roleIdA)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNotFound());

        // 8. Verify Role B now has the user assigned
        MvcResult usersResult = mockMvc.perform(get("/api/platform/roles/" + roleIdB + "/users")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String body = usersResult.getResponse().getContentAsString();
        assertTrue(body.contains("\"id\":" + userId), "User should be assigned to Role B");

        // Cleanup
        userRepository.delete(userRepository.findById(userId).orElseThrow());
        roleRepository.deleteById(roleIdB);
        roleRepository.deleteById(roleIdA);
    }

    @Test
    public void testPlatformRoleDeletePreview() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String roleNameC = "Platform Role C " + uniqueSuffix;

        List<PlatformPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(PlatformPermission::getId)
                .limit(2)
                .collect(Collectors.toSet());

        // 1. Create custom Role C
        PlatformRoleCreateRequest roleCreateC = PlatformRoleCreateRequest.builder()
                .name(roleNameC)
                .description("Temporary Platform Role C")
                .permissionIds(permIds)
                .category("TESTING")
                .priority(30)
                .build();

        MvcResult createResultC = mockMvc.perform(post("/api/platform/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleCreateC)))
                .andExpect(status().isCreated())
                .andReturn();

        PlatformRoleResponse roleC = objectMapper.readValue(
                createResultC.getResponse().getContentAsString(), PlatformRoleResponse.class);
        Long roleIdC = roleC.getId();

        // 2. Create a platform user and assign Role C
        String uniqueEmail = "user_c_" + UUID.randomUUID().toString().substring(0, 8) + "@sonixhr.com";
        PlatformRole roleEntityC = roleRepository.findById(roleIdC).orElseThrow();
        PlatformUser testUser = PlatformUser.builder()
                .email(uniqueEmail)
                .password(passwordEncoder.encode("Password@123"))
                .fullName("Test Platform User C")
                .status(UserStatus.ACTIVE)
                .active(true)
                .roles(new HashSet<>(List.of(roleEntityC)))
                .rolesVersion(1)
                .build();
        testUser = userRepository.save(testUser);
        Long userId = testUser.getId();

        // 3. Request Role Delete Preview
        MvcResult previewResult = mockMvc.perform(get("/api/platform/roles/" + roleIdC + "/delete-preview")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        PlatformRoleDeletePreviewResponse preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(), PlatformRoleDeletePreviewResponse.class);

        assertEquals(roleIdC, preview.getRoleId());
        assertEquals(roleNameC, preview.getRoleName());
        assertEquals(1, preview.getAffectedUserCount());
        assertFalse(preview.getAffectedUsers().isEmpty());
        assertEquals(userId, preview.getAffectedUsers().get(0).getId());

        // Options should list other active roles in the platform
        assertFalse(preview.getReassignmentOptions().isEmpty());
        boolean containsSelf = preview.getReassignmentOptions().stream()
                .anyMatch(opt -> opt.getId().equals(roleIdC));
        assertFalse(containsSelf, "Reassignment options should not contain the role being deleted");

        assertTrue(preview.isDeletable(), "Custom role with other active options should be deletable");
        assertNull(preview.getValidationMessage());

        // Test delete preview on system role (should return deletable=false, validationMessage rather than throwing exception)
        PlatformRole systemRole = roleRepository.findAll().stream()
                .filter(PlatformRole::isSystemRole)
                .findFirst()
                .orElseThrow();
        Long systemRoleId = systemRole.getId();

        MvcResult systemPreviewResult = mockMvc.perform(get("/api/platform/roles/" + systemRoleId + "/delete-preview")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        PlatformRoleDeletePreviewResponse systemPreview = objectMapper.readValue(
                systemPreviewResult.getResponse().getContentAsString(), PlatformRoleDeletePreviewResponse.class);

        assertFalse(systemPreview.isDeletable(), "System role should not be deletable");
        assertNotNull(systemPreview.getValidationMessage());
        assertTrue(systemPreview.getValidationMessage().contains("Cannot delete system role"));

        // Cleanup
        userRepository.delete(userRepository.findById(userId).orElseThrow());
        roleRepository.deleteById(roleIdC);
    }

    @Test
    public void testPlatformUserDuplicateEmailValidation() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String uniqueEmail = "test_admin_" + uniqueSuffix + "@sonixhr.com";

        // Fetch active platform roles to assign
        List<PlatformRole> activeRoles = roleRepository.findByActiveTrue();
        assertFalse(activeRoles.isEmpty());
        Set<Long> roleIds = Set.of(activeRoles.get(0).getId());

        PlatformUserCreateRequest request = PlatformUserCreateRequest.builder()
                .email(uniqueEmail)
                .fullName("Test Platform Admin")
                .designation("Platform Operator")
                .roleIds(roleIds)
                .build();

        // 1. Create successfully the first time
        MvcResult firstCreateResult = mockMvc.perform(post("/api/platform/users")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        PlatformUserResponse firstCreated = objectMapper.readValue(
                firstCreateResult.getResponse().getContentAsString(), PlatformUserResponse.class);

        // 2. Try to create again with the same email -> should fail with email field error in the errors map
        MvcResult duplicateResult = mockMvc.perform(post("/api/platform/users")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String duplicateBody = duplicateResult.getResponse().getContentAsString();
        assertTrue(duplicateBody.contains("\"errors\""));
        assertTrue(duplicateBody.contains("\"email\""));
        assertTrue(duplicateBody.contains("Email address already registered"));

        // Cleanup
        userRepository.deleteById(firstCreated.getId());
    }
}
