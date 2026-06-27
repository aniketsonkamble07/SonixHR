package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.leave.*;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.LeaveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class LeaveManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminTokenHeader;
    private Long tenantId;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        this.uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Leave Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@leavetest.com";

        // Register Tenant
        TenantRegistrationRequest regRequest = TenantRegistrationRequest.builder()
                .companyName(companyName)
                .adminEmail(adminEmail)
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("123 Test Street")
                .city("Bangalore")
                .state(IndianState.KA)
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

        // Login as admin
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        this.adminTokenHeader = "Bearer " + loginResponse.getAccessToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLeaveManagementWorkflow() throws Exception {
        // Step 1: Create a Department for the employee
        DepartmentRequest deptRequest = DepartmentRequest.builder()
                .name("Engineering")
                .code("ENG-" + uniqueSuffix)
                .description("Engineering Dept")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", adminTokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse dept = objectMapper.readValue(
                deptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // Step 2: Get roles list to assign Employee role
        MvcResult rolesListResult = mockMvc.perform(get("/api/tenant/roles")
                        .header("Authorization", adminTokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> rolesList = objectMapper.readValue(
                rolesListResult.getResponse().getContentAsString(), List.class);
        
        Long employeeRoleId = null;
        for (Map<String, Object> r : rolesList) {
            if ("Employee".equalsIgnoreCase((String) r.get("name"))) {
                employeeRoleId = ((Number) r.get("id")).longValue();
                break;
            }
        }
        assertNotNull(employeeRoleId, "Default 'Employee' role must exist after tenant registration");

        // Step 3: Create a regular Employee John Doe
        String employeeEmail = "john.doe_" + uniqueSuffix + "@leavetest.com";
        EmployeeCreateRequest empRequest = EmployeeCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email(employeeEmail)
                .departmentId(dept.getId())
                .position("Software Engineer")
                .hireDate(LocalDate.now().minusMonths(1))
                .phone("+919876543210")
                .city("Bangalore")
                .state(IndianState.KA)
                .country("India")
                .workLocation("Office")
                .employmentType(EmploymentType.FULL_TIME)
                .probationMonths(3)
                .roleIds(Set.of(employeeRoleId))
                .build();

        MvcResult empResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", adminTokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse employee = objectMapper.readValue(
                empResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertNotNull(employee);

        // Step 4: Login as John Doe
        LoginRequest empLoginRequest = new LoginRequest(employeeEmail, "Admin@123");
        MvcResult empLoginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empLoginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse empLoginResponse = objectMapper.readValue(
                empLoginResult.getResponse().getContentAsString(), LoginResponse.class);
        String employeeTokenHeader = "Bearer " + empLoginResponse.getAccessToken();

        // Step 5: Get settings. Initially policiesConfigured is false.
        MvcResult settingsResult = mockMvc.perform(get("/api/employees/leaves/settings")
                        .header("Authorization", adminTokenHeader))
                .andExpect(status().isOk())
                .andReturn();
        
        LeaveSettingsDTO settings = objectMapper.readValue(
                settingsResult.getResponse().getContentAsString(), LeaveSettingsDTO.class);
        assertNotNull(settings);
        assertFalse(settings.getPoliciesConfigured());

        // Step 6: Requesting leave before policy configuration should fail with 400
        LocalDate nextMonday = LocalDate.now();
        while (nextMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
            nextMonday = nextMonday.plusDays(1);
        }
        if (nextMonday.isBefore(LocalDate.now().plusDays(1))) {
            nextMonday = nextMonday.plusDays(7);
        }
        LocalDate nextTuesday = nextMonday.plusDays(1);

        LeaveRequestDTO leaveReq = LeaveRequestDTO.builder()
                .leaveType(LeaveType.CASUAL)
                .startDate(nextMonday)
                .endDate(nextTuesday)
                .reason("Vacation")
                .isHalfDay(false)
                .build();

        mockMvc.perform(post("/api/employee/leaves")
                        .header("Authorization", employeeTokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaveReq)))
                .andExpect(status().isBadRequest()); // Should fail because policiesConfigured is false

        // Step 7: Configure policies (set policiesConfigured = true, enable CASUAL, set 12 days)
        Map<String, LeavePolicyDTO> policies = settings.getLeavePolicies();
        if (policies == null) {
            policies = new HashMap<>();
        }
        policies.put("CASUAL", LeavePolicyDTO.builder()
                .allowed(true)
                .daysPerYear(12)
                .carryForward(false)
                .maxCarryForwardDays(0)
                .minimumServiceMonths(0)
                .genderEligibility("ALL")
                .probationPeriodAllowed(true)
                .prorated(false)
                .build());
        
        settings.setLeavePolicies(policies);
        settings.setPoliciesConfigured(true);

        MvcResult updateSettingsResult = mockMvc.perform(put("/api/employees/leaves/settings")
                        .header("Authorization", adminTokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isOk())
                .andReturn();

        LeaveSettingsDTO updatedSettings = objectMapper.readValue(
                updateSettingsResult.getResponse().getContentAsString(), LeaveSettingsDTO.class);
        assertTrue(updatedSettings.getPoliciesConfigured());

        // Step 8: Request leave again (now it should succeed)
        MvcResult leaveResult = mockMvc.perform(post("/api/employee/leaves")
                        .header("Authorization", employeeTokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaveReq)))
                .andExpect(status().isCreated())
                .andReturn();

        LeaveResponseDTO leaveResp = objectMapper.readValue(
                leaveResult.getResponse().getContentAsString(), LeaveResponseDTO.class);
        assertNotNull(leaveResp);
        assertEquals(LeaveStatus.PENDING, leaveResp.getStatus());

        // Step 9: Check Leave Balance
        MvcResult balanceResult = mockMvc.perform(get("/api/employee/leaves/balance")
                        .header("Authorization", employeeTokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> balanceMap = objectMapper.readValue(
                balanceResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> casualBalance = (Map<String, Object>) balanceMap.get("CASUAL");
        assertEquals(12.0, ((Number) casualBalance.get("total")).doubleValue());
        assertEquals(0.0, ((Number) casualBalance.get("used")).doubleValue());

        // Step 10: Approve the leave request (Admin/Manager role approves it)
        MvcResult approveResult = mockMvc.perform(put("/api/employees/leaves/" + leaveResp.getId() + "/approve")
                        .header("Authorization", adminTokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        LeaveResponseDTO approvedLeave = objectMapper.readValue(
                approveResult.getResponse().getContentAsString(), LeaveResponseDTO.class);
        assertEquals(LeaveStatus.APPROVED, approvedLeave.getStatus());

        // Step 11: Recheck balance, now used should be 2.0
        MvcResult balanceResult2 = mockMvc.perform(get("/api/employee/leaves/balance")
                        .header("Authorization", employeeTokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> balanceMap2 = objectMapper.readValue(
                balanceResult2.getResponse().getContentAsString(), Map.class);
        Map<String, Object> casualBalance2 = (Map<String, Object>) balanceMap2.get("CASUAL");
        assertEquals(2.0, ((Number) casualBalance2.get("used")).doubleValue());
        assertEquals(10.0, ((Number) casualBalance2.get("remaining")).doubleValue());

        // Step 12: Cancel/delete the leave request (removes attendance)
        MvcResult cancelResult = mockMvc.perform(put("/api/employee/leaves/" + leaveResp.getId() + "/cancel")
                        .header("Authorization", employeeTokenHeader)
                        .param("cancellationReason", "Plans changed"))
                .andExpect(status().isOk())
                .andReturn();

        LeaveResponseDTO cancelledLeave = objectMapper.readValue(
                cancelResult.getResponse().getContentAsString(), LeaveResponseDTO.class);
        assertEquals(LeaveStatus.CANCELLED, cancelledLeave.getStatus());

        // Step 13: Recheck balance again, should be back to 0 used
        MvcResult balanceResult3 = mockMvc.perform(get("/api/employee/leaves/balance")
                        .header("Authorization", employeeTokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> balanceMap3 = objectMapper.readValue(
                balanceResult3.getResponse().getContentAsString(), Map.class);
        Map<String, Object> casualBalance3 = (Map<String, Object>) balanceMap3.get("CASUAL");
        assertEquals(0.0, ((Number) casualBalance3.get("used")).doubleValue());
    }
}
