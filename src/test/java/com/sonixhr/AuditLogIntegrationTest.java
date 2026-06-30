package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.entity.tenant.TenantAuditLog;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.tenant.TenantAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class AuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantAuditLogRepository auditLogRepository;

    @Test
    public void testAuditLoggingForSensitiveMutations() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Audit Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@audit.com";

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

        // Login to get token
        LoginRequest loginReq = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String adminToken = "Bearer " + loginResponse.getAccessToken();

        // Create Department
        DepartmentRequest deptReq = DepartmentRequest.builder()
                .name("Audit Department " + uniqueSuffix)
                .code("AUD_" + uniqueSuffix)
                .description("Audit Dept")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptReq)))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse departmentResponse = objectMapper.readValue(
                deptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // 1. Create custom role A
        TenantRoleCreateRequest roleReqA = new TenantRoleCreateRequest();
        roleReqA.setName("Audit Role A " + uniqueSuffix);
        roleReqA.setDescription("Audit Role A");
        roleReqA.setPermissionIds(Set.of());
        roleReqA.setIsDefault(false);

        MvcResult roleResultA = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleReqA)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse roleResponseA = objectMapper.readValue(
                roleResultA.getResponse().getContentAsString(), TenantRoleResponse.class);

        // 2. Create custom role B
        TenantRoleCreateRequest roleReqB = new TenantRoleCreateRequest();
        roleReqB.setName("Audit Role B " + uniqueSuffix);
        roleReqB.setDescription("Audit Role B");
        roleReqB.setPermissionIds(Set.of());
        roleReqB.setIsDefault(false);

        MvcResult roleResultB = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleReqB)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse roleResponseB = objectMapper.readValue(
                roleResultB.getResponse().getContentAsString(), TenantRoleResponse.class);

        // 3. Create another Employee in this tenant with Role A
        EmployeeCreateRequest empReq = EmployeeCreateRequest.builder()
                .firstName("Test")
                .lastName("Audited")
                .email("testaudited_" + uniqueSuffix + "@audit.com")
                .phone("+919988776655")
                .position("Auditor")
                .departmentId(departmentResponse.getId())
                .hireDate(LocalDate.now())
                .roleIds(Set.of(roleResponseA.getId()))
                .build();

        MvcResult empResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empReq)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse employeeResponse = objectMapper.readValue(
                empResult.getResponse().getContentAsString(), EmployeeResponse.class);

        // 4. Assign custom role B to the employee
        mockMvc.perform(post("/api/tenant/roles/" + roleResponseB.getId() + "/users/" + employeeResponse.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // 5. Delete the employee
        mockMvc.perform(delete("/api/employees/" + employeeResponse.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        // 6. Verify audit logs exist for ROLE_ASSIGNED and EMPLOYEE_DELETED
        List<TenantAuditLog> logs = auditLogRepository.findAll();
        
        boolean foundRoleAssigned = logs.stream().anyMatch(l -> "ROLE_ASSIGNED".equals(l.getAction()));
        boolean foundEmployeeDeleted = logs.stream().anyMatch(l -> "EMPLOYEE_DELETED".equals(l.getAction()));

        assertTrue(foundRoleAssigned, "Should find ROLE_ASSIGNED in audit logs");
        assertTrue(foundEmployeeDeleted, "Should find EMPLOYEE_DELETED in audit logs");
    }
}
