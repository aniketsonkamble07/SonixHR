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
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class TenantFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantPermissionRepository permissionRepository;

    @Test
    public void testEndToEndTenantFlow() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@testcompany.com";

        // Step 1: Register Tenant
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

        String regResponseStr = regResult.getResponse().getContentAsString();
        TenantRegistrationResponse regResponse = objectMapper.readValue(regResponseStr, TenantRegistrationResponse.class);

        assertNotNull(regResponse);
        assertTrue(regResponse.isSuccess());
        assertNotNull(regResponse.getTenantId());
        assertEquals(companyName, regResponse.getCompanyName());
        assertEquals(adminEmail, regResponse.getAdminEmail());

        // Step 2: Login as the registered Admin
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");

        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponseStr = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseStr, LoginResponse.class);

        assertNotNull(loginResponse);
        assertNotNull(loginResponse.getAccessToken());
        assertEquals(adminEmail, loginResponse.getEmail());
        String tokenHeader = "Bearer " + loginResponse.getAccessToken();

        // Step 3: Create Department
        DepartmentRequest deptRequest = DepartmentRequest.builder()
                .name("Engineering")
                .code("ENG")
                .description("Engineering Department")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String deptResponseStr = deptResult.getResponse().getContentAsString();
        DepartmentResponse deptResponse = objectMapper.readValue(deptResponseStr, DepartmentResponse.class);

        assertNotNull(deptResponse);
        assertNotNull(deptResponse.getId());
        assertEquals("Engineering", deptResponse.getName());
        assertEquals("ENG", deptResponse.getCode());

        // Step 4: Create custom Tenant Role
        // Retrieve some available permissions for this tenant
        List<TenantPermission> permissions = permissionRepository.findAll();
        assertFalse(permissions.isEmpty(), "Permissions should be pre-seeded");
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(5)
                .collect(Collectors.toSet());

        TenantRoleCreateRequest roleRequest = TenantRoleCreateRequest.builder()
                .name("Senior Dev " + uniqueSuffix)
                .description("Custom role for testing")
                .isDefault(false)
                .permissionIds(permIds)
                .category("DEVELOPMENT")
                .priority(50)
                .build();

        MvcResult roleResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String roleResponseStr = roleResult.getResponse().getContentAsString();
        TenantRoleResponse roleResponse = objectMapper.readValue(roleResponseStr, TenantRoleResponse.class);

        assertNotNull(roleResponse);
        assertNotNull(roleResponse.getId());
        assertEquals("Senior Dev " + uniqueSuffix, roleResponse.getName());

        // Step 5: Create Employee (associated with created department and role)
        EmployeeCreateRequest empRequest = EmployeeCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Software Engineer")
                .hireDate(LocalDate.now())
                .phone("+919876543210")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .workLocation("Office")
                .employmentType(EmploymentType.FULL_TIME)
                .probationMonths(3)
                .roleIds(Set.of(roleResponse.getId()))
                .build();

        MvcResult empResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String empResponseStr = empResult.getResponse().getContentAsString();
        EmployeeResponse empResponse = objectMapper.readValue(empResponseStr, EmployeeResponse.class);

        assertNotNull(empResponse);
        assertNotNull(empResponse.getId());
        assertEquals("John", empResponse.getFirstName());
        assertEquals("Doe", empResponse.getLastName());
        assertEquals("john.doe_" + uniqueSuffix + "@testcompany.com", empResponse.getEmail());
        assertEquals(deptResponse.getId(), empResponse.getDepartment().getId());
    }

    @Test
    public void testTenantRegistrationValidation() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Unique Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@uniquecompany.com";

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

        // 1. Register successfully the first time
        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // 2. Try to register with the same company name -> should fail with companyName field error in the errors map
        TenantRegistrationRequest duplicateCompanyRequest = TenantRegistrationRequest.builder()
                .companyName(companyName)
                .adminEmail("different_email@uniquecompany.com")
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("123 Test Street")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .planCode("trial")
                .billingCycle("MONTHLY")
                .build();

        MvcResult duplicateCompanyResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateCompanyRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String duplicateCompanyBody = duplicateCompanyResult.getResponse().getContentAsString();
        assertTrue(duplicateCompanyBody.contains("\"errors\""));
        assertTrue(duplicateCompanyBody.contains("\"companyName\""));
        assertTrue(duplicateCompanyBody.contains("Company name already registered"));

        // 3. Try to register with the same admin email -> should fail with adminEmail field error in the errors map
        TenantRegistrationRequest duplicateEmailRequest = TenantRegistrationRequest.builder()
                .companyName("Different Company Name")
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

        MvcResult duplicateEmailResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmailRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String duplicateEmailBody = duplicateEmailResult.getResponse().getContentAsString();
        assertTrue(duplicateEmailBody.contains("\"errors\""));
        assertTrue(duplicateEmailBody.contains("\"adminEmail\""));
        assertTrue(duplicateEmailBody.contains("Email address already registered"));
    }

    @Test
    public void testEmployeeValidation() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Emp Val Company " + uniqueSuffix;
        String adminEmail = "admin_emp_val_" + uniqueSuffix + "@testcompany.com";

        // Step 1: Register Tenant
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

        // Step 2: Login as the registered Admin
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String tokenHeader = "Bearer " + loginResponse.getAccessToken();

        // Step 3: Create Department
        DepartmentRequest deptRequest = DepartmentRequest.builder()
                .name("Engineering")
                .code("ENG")
                .description("Engineering Department")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse deptResponse = objectMapper.readValue(
                deptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // Step 4: Create custom Tenant Role
        List<TenantPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(1)
                .collect(Collectors.toSet());

        TenantRoleCreateRequest roleRequest = TenantRoleCreateRequest.builder()
                .name("Dev " + uniqueSuffix)
                .description("Custom role for testing")
                .isDefault(false)
                .permissionIds(permIds)
                .category("DEVELOPMENT")
                .priority(50)
                .build();

        MvcResult roleResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse roleResponse = objectMapper.readValue(
                roleResult.getResponse().getContentAsString(), TenantRoleResponse.class);

        // 1. Try to create employee with empty roles -> should fail on roleIds field
        EmployeeCreateRequest emptyRolesRequest = EmployeeCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Software Engineer")
                .hireDate(LocalDate.now())
                .phone("+919876543210")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .workLocation("Office")
                .employmentType(EmploymentType.FULL_TIME)
                .probationMonths(3)
                .roleIds(null) // Null roleIds
                .build();

        MvcResult emptyRolesResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRolesRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String emptyRolesBody = emptyRolesResult.getResponse().getContentAsString();
        assertTrue(emptyRolesBody.contains("\"errors\""));
        assertTrue(emptyRolesBody.contains("\"roleIds\""));
        assertTrue(emptyRolesBody.contains("At least one role is required"));

        // 2. Create Employee 1 successfully
        EmployeeCreateRequest emp1Request = EmployeeCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Software Engineer")
                .hireDate(LocalDate.now())
                .phone("+919876543210")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .workLocation("Office")
                .employmentType(EmploymentType.FULL_TIME)
                .probationMonths(3)
                .roleIds(Set.of(roleResponse.getId()))
                .build();

        MvcResult emp1Result = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp1Request)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse emp1Response = objectMapper.readValue(
                emp1Result.getResponse().getContentAsString(), EmployeeResponse.class);

        // 3. Create Employee 2 successfully with different email
        EmployeeCreateRequest emp2Request = EmployeeCreateRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("QA Engineer")
                .roleIds(Set.of(roleResponse.getId()))
                .hireDate(LocalDate.now())
                .build();

        MvcResult emp2Result = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp2Request)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse emp2Response = objectMapper.readValue(
                emp2Result.getResponse().getContentAsString(), EmployeeResponse.class);

        // 4. Try to create another employee with Employee 1's email -> should fail on email field
        EmployeeCreateRequest duplicateEmailRequest = EmployeeCreateRequest.builder()
                .firstName("Dup")
                .lastName("Email")
                .email("john.doe_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Engineer")
                .roleIds(Set.of(roleResponse.getId()))
                .hireDate(LocalDate.now())
                .build();

        MvcResult duplicateResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmailRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String duplicateBody = duplicateResult.getResponse().getContentAsString();
        assertTrue(duplicateBody.contains("\"errors\""));
        assertTrue(duplicateBody.contains("\"email\""));
        assertTrue(duplicateBody.contains("already exists"));

        // 5. Try to assign Employee 1 as their own manager -> should fail on managerId field
        EmployeeCreateRequest selfManagerRequest = EmployeeCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Software Engineer")
                .hireDate(LocalDate.now())
                .roleIds(Set.of(roleResponse.getId()))
                .managerId(emp1Response.getId()) // self
                .build();

        MvcResult selfManagerResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/employees/" + emp1Response.getId())
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfManagerRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String selfManagerBody = selfManagerResult.getResponse().getContentAsString();
        assertTrue(selfManagerBody.contains("\"errors\""));
        assertTrue(selfManagerBody.contains("\"managerId\""));
        assertTrue(selfManagerBody.contains("own manager"));

        // 6. Try to update Employee 1 email to Employee 2's email -> should fail on email field
        EmployeeCreateRequest dupEmailUpdateRequest = EmployeeCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("jane.smith_" + uniqueSuffix + "@testcompany.com") // emp2 email
                .departmentId(deptResponse.getId())
                .position("Software Engineer")
                .hireDate(LocalDate.now())
                .roleIds(Set.of(roleResponse.getId()))
                .build();

        MvcResult dupEmailUpdateResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/employees/" + emp1Response.getId())
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupEmailUpdateRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String dupEmailUpdateBody = dupEmailUpdateResult.getResponse().getContentAsString();
        assertTrue(dupEmailUpdateBody.contains("\"errors\""));
        assertTrue(dupEmailUpdateBody.contains("\"email\""));
        assertTrue(dupEmailUpdateBody.contains("already exists"));

        // 7. Test activation password mismatch in EmployeeActivationController
        com.sonixhr.dto.ActivationRequest actRequest = com.sonixhr.dto.ActivationRequest.builder()
                .token("dummy_token")
                .password("Password@123")
                .confirmPassword("Mismatch@123")
                .build();

        MvcResult actResult = mockMvc.perform(post("/api/employee/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String actBody = actResult.getResponse().getContentAsString();
        assertTrue(actBody.contains("\"errors\""));
        assertTrue(actBody.contains("\"confirmPassword\""));
        assertTrue(actBody.contains("Passwords do not match"));

        // 8. Log in as Employee 1 to get their token
        LoginRequest emp1Login = new LoginRequest("john.doe_" + uniqueSuffix + "@testcompany.com", "Admin@123");
        MvcResult emp1LoginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp1Login)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse emp1LoginResponse = objectMapper.readValue(
                emp1LoginResult.getResponse().getContentAsString(), LoginResponse.class);
        String emp1TokenHeader = "Bearer " + emp1LoginResponse.getAccessToken();

        // 9. Try to update professional info via self-service PUT /api/employee/profile -> should fail with ValidationException on departmentId
        com.sonixhr.dto.employee.EmployeeProfileUpdateRequest selfServiceRequest = com.sonixhr.dto.employee.EmployeeProfileUpdateRequest.builder()
                .phone("+918888888888") // allowed personal info change
                .departmentId(deptResponse.getId()) // unauthorized professional info change
                .build();

        MvcResult selfServiceResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/employee/profile")
                        .header("Authorization", emp1TokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfServiceRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String selfServiceBody = selfServiceResult.getResponse().getContentAsString();
        assertTrue(selfServiceBody.contains("\"errors\""));
        assertTrue(selfServiceBody.contains("\"departmentId\""));
        assertTrue(selfServiceBody.contains("Only HR or Super Admin can update"));
    }
}
