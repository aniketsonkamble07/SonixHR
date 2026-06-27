package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.enums.IndianState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class DepartmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenHeader;
    private Long tenantId;

    @BeforeEach
    public void setUp() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Dept Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@depttest.com";

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
        this.tenantId = regResponse.getTenantId();

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
    public void testDepartmentCRUDAndEndpoints() throws Exception {
        // 1. Create Department
        DepartmentRequest deptRequest = DepartmentRequest.builder()
                .name("Product Engineering")
                .code("PROD-ENG")
                .description("Product Development Department")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), DepartmentResponse.class);
        assertNotNull(created.getId());
        assertEquals("Product Engineering", created.getName());
        assertEquals("PROD-ENG", created.getCode());

        Long deptId = created.getId();

        // 2. Get Department by ID
        MvcResult getResult = mockMvc.perform(get("/api/tenant/departments/" + deptId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        DepartmentResponse retrieved = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), DepartmentResponse.class);
        assertEquals(deptId, retrieved.getId());
        assertEquals("Product Engineering", retrieved.getName());

        // 3. Get Department with Stats
        mockMvc.perform(get("/api/tenant/departments/" + deptId + "/stats")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 4. Get All Departments (Paginated)
        mockMvc.perform(get("/api/tenant/departments")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 5. Get All Departments List
        mockMvc.perform(get("/api/tenant/departments/list")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 6. Get Department Lookup
        mockMvc.perform(get("/api/tenant/departments/lookup")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 7. Get All Departments with Bulk Stats
        mockMvc.perform(get("/api/tenant/departments/bulk-stats")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 8. Update Department
        DepartmentRequest updateRequest = DepartmentRequest.builder()
                .name("Core Engineering")
                .code("CORE-ENG")
                .description("Core Platform Engineering")
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/tenant/departments/" + deptId)
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();

        DepartmentResponse updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), DepartmentResponse.class);
        assertEquals(deptId, updated.getId());
        assertEquals("Core Engineering", updated.getName());
        assertEquals("CORE-ENG", updated.getCode());

        // 9. Employee Counts
        mockMvc.perform(get("/api/tenant/departments/" + deptId + "/employee-count/total")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tenant/departments/" + deptId + "/employee-count/active")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tenant/departments/" + deptId + "/employee-count/probation")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 10. Dashboard
        mockMvc.perform(get("/api/tenant/departments/dashboard")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        // 11. Search
        mockMvc.perform(get("/api/tenant/departments/search")
                        .header("Authorization", tokenHeader)
                        .param("query", "Core"))
                .andExpect(status().isOk());

        // 12. Delete Department
        mockMvc.perform(delete("/api/tenant/departments/" + deptId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // Verify it was deleted (should return 404/NOT_FOUND)
        mockMvc.perform(get("/api/tenant/departments/" + deptId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDepartmentDuplicateValidation() throws Exception {
        // Create initial department
        DepartmentRequest dept1 = DepartmentRequest.builder()
                .name("HR Development")
                .code("HR-DEV")
                .description("Human Resources Development")
                .build();

        mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dept1)))
                .andExpect(status().isCreated());

        // Try to create another department with the same name -> should fail with name field error
        DepartmentRequest deptDuplicateName = DepartmentRequest.builder()
                .name("HR Development")
                .code("HR-DIFF")
                .description("Duplicate Name Department")
                .build();

        MvcResult duplicateNameResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptDuplicateName)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String duplicateNameBody = duplicateNameResult.getResponse().getContentAsString();
        assertTrue(duplicateNameBody.contains("\"errors\""));
        assertTrue(duplicateNameBody.contains("\"name\""));
        assertTrue(duplicateNameBody.contains("Department name already exists"));

        // Try to create another department with the same code -> should fail with code field error
        DepartmentRequest deptDuplicateCode = DepartmentRequest.builder()
                .name("Different Name")
                .code("HR-DEV")
                .description("Duplicate Code Department")
                .build();

        MvcResult duplicateCodeResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptDuplicateCode)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String duplicateCodeBody = duplicateCodeResult.getResponse().getContentAsString();
        assertTrue(duplicateCodeBody.contains("\"errors\""));
        assertTrue(duplicateCodeBody.contains("\"code\""));
        assertTrue(duplicateCodeBody.contains("Department code already exists"));
    }
}
