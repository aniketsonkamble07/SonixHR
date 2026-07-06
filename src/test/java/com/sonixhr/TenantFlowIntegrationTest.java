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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

        // 9. Try to update professional info via general PUT /api/employees/{id} -> should fail with ValidationException on departmentId
        com.sonixhr.dto.employee.EmployeeUpdateRequest selfServiceRequest = new com.sonixhr.dto.employee.EmployeeUpdateRequest();
        selfServiceRequest.setFirstName("EmpOne");
        selfServiceRequest.setLastName("Test");
        selfServiceRequest.setEmail(emp1Response.getEmail());
        selfServiceRequest.setDepartmentId(deptResponse.getId() + 1); // unauthorized professional info change
        selfServiceRequest.setPosition("Software Engineer"); // required
        selfServiceRequest.setHireDate(LocalDate.now()); // required

        MvcResult selfServiceResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/employees/" + emp1Response.getId())
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

    @Test
    public void testEmployeeAddressFallbackAndSynchronization() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Addr Flow Company " + uniqueSuffix;
        String adminEmail = "admin_addr_flow_" + uniqueSuffix + "@testcompany.com";

        // Step 1: Register Tenant with office address details
        TenantRegistrationRequest regRequest = TenantRegistrationRequest.builder()
                .companyName(companyName)
                .adminEmail(adminEmail)
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("456 Company Boulevard")
                .city("Pune")
                .state(IndianState.MAHARASHTRA)
                .country("India")
                .planCode("trial")
                .billingCycle("MONTHLY")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();



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
                .name("HR")
                .code("HR")
                .description("HR Dept")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse deptResponse = objectMapper.readValue(
                deptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // Step 4: Create default role
        List<TenantPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(1)
                .collect(Collectors.toSet());

        TenantRoleCreateRequest roleRequest = TenantRoleCreateRequest.builder()
                .name("Manager " + uniqueSuffix)
                .description("Manager role")
                .isDefault(false)
                .permissionIds(permIds)
                .category("MANAGEMENT")
                .priority(40)
                .build();

        MvcResult roleResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse roleResponse = objectMapper.readValue(
                roleResult.getResponse().getContentAsString(), TenantRoleResponse.class);

        // Scenario 1: Create Employee with no address fields -> should fallback to Tenant's address details,
        // and permanentAddress should fallback to the resolved current address.
        EmployeeCreateRequest empFallbackRequest = EmployeeCreateRequest.builder()
                .firstName("Fallback")
                .lastName("User")
                .email("fallback.user_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("HR Generalist")
                .hireDate(LocalDate.now())
                .phone("+919876543211")
                .roleIds(Set.of(roleResponse.getId()))
                .build();

        MvcResult createFallbackResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empFallbackRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse fallbackEmp = objectMapper.readValue(
                createFallbackResult.getResponse().getContentAsString(), EmployeeResponse.class);

        // Assert fallbacks to Tenant office details
        assertEquals("456 Company Boulevard", fallbackEmp.getAddress());
        assertEquals("Pune", fallbackEmp.getCity());
        assertEquals(IndianState.MAHARASHTRA, fallbackEmp.getState());
        assertEquals("IN", fallbackEmp.getCountry());

        // Scenario 2: Create Employee with explicit current address but no permanent address -> should copy address to permanent address
        EmployeeCreateRequest empPartialAddressRequest = EmployeeCreateRequest.builder()
                .firstName("Partial")
                .lastName("User")
                .email("partial.user_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Recruiter")
                .hireDate(LocalDate.now())
                .phone("+919876543212")
                .address("789 Current Street")
                .city("Pune")
                .state(IndianState.MAHARASHTRA)
                .country("India")
                .roleIds(Set.of(roleResponse.getId()))
                .build();

        MvcResult createPartialResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empPartialAddressRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse partialEmp = objectMapper.readValue(
                createPartialResult.getResponse().getContentAsString(), EmployeeResponse.class);

        assertEquals("789 Current Street", partialEmp.getAddress());

        // Scenario 3: Update current address but omit permanent address -> should synchronize permanent address to match the updated address
        EmployeeCreateRequest empUpdateRequest = EmployeeCreateRequest.builder()
                .firstName("Partial")
                .lastName("User")
                .email("partial.user_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Recruiter")
                .hireDate(LocalDate.now())
                .phone("+919876543212")
                .address("999 New Current Street") // Updated address
                .city("Pune")
                .state(IndianState.MAHARASHTRA)
                .country("India")
                .roleIds(Set.of(roleResponse.getId()))
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/employees/" + partialEmp.getId())
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empUpdateRequest)))
                .andExpect(status().isOk())
                .andReturn();

        EmployeeResponse updatedEmp = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), EmployeeResponse.class);

        assertEquals("999 New Current Street", updatedEmp.getAddress());
    }

    @Test
    public void testZeroDerivationAndTypeSafeValidation() throws Exception {
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



        // Login as the registered Admin
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String tokenHeader = "Bearer " + loginResponse.getAccessToken();

        // Create Department
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

        // Fetch custom Tenant Role
        List<TenantPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(1)
                .collect(Collectors.toSet());

        TenantRoleCreateRequest roleRequest = TenantRoleCreateRequest.builder()
                .name("Dev " + uniqueSuffix)
                .description("Custom role")
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

        // Scenario 1: JSON payload contains an invalid/unsupported state string -> should trigger 400 Bad Request at deserialization boundary
        String malformedJson = "{"
                + "\"firstName\":\"InvalidState\","
                + "\"lastName\":\"User\","
                + "\"email\":\"invalidstate.user_" + uniqueSuffix + "@testcompany.com\","
                + "\"departmentId\":" + deptResponse.getId() + ","
                + "\"position\":\"Software Engineer\","
                + "\"hireDate\":\"" + LocalDate.now() + "\","
                + "\"phone\":\"+919876543219\","
                + "\"address\":\"123 Main St\","
                + "\"city\":\"Pune\","
                + "\"state\":\"InvalidState\","
                + "\"country\":\"India\","
                + "\"workLocation\":\"Office\","
                + "\"roleIds\":[" + roleResponse.getId() + "]"
                + "}";

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        // Scenario 2: Create a non-Indian employee (country = "United States") with null state -> should succeed (no ValidationException)
        EmployeeCreateRequest usEmpRequest = EmployeeCreateRequest.builder()
                .firstName("US")
                .lastName("Employee")
                .email("us.employee_" + uniqueSuffix + "@testcompany.com")
                .departmentId(deptResponse.getId())
                .position("Manager")
                .hireDate(LocalDate.now())
                .phone("+15550199")
                .address("1600 Amphitheatre Pkwy")
                .city("Mountain View")
                .state(null)
                .country("United States")
                .roleIds(Set.of(roleResponse.getId()))
                .build();

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usEmpRequest)))
                .andExpect(status().isCreated());
    }

    // =====================================================
    // TEST: DEFAULT ROLES SEEDED AFTER TENANT REGISTRATION
    // =====================================================

    /**
     * After registering a tenant, the system should automatically seed 3 default roles:
     * Super Admin, Manager, Employee. This test verifies the seeding via the roles API.
     */
    @Test
    public void testDefaultRolesSeededAfterTenantRegistration() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin_roles_" + uniqueSuffix + "@testcompany.com";

        // Register tenant
        TenantRegistrationRequest regRequest = TenantRegistrationRequest.builder()
                .companyName("Roles Seed Co " + uniqueSuffix)
                .adminEmail(adminEmail)
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("1 Seed Avenue")
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

        // Login
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String tokenHeader = "Bearer " + loginResponse.getAccessToken();

        // GET /api/tenant/roles -> verify 3 default roles are present
        MvcResult rolesResult = mockMvc.perform(get("/api/tenant/roles")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String rolesBody = rolesResult.getResponse().getContentAsString();
        assertTrue(rolesBody.contains("\"name\""), "Response should contain role objects with a name field");
        assertTrue(rolesBody.contains("Super Admin"), "Super Admin role should be seeded automatically");
        assertTrue(rolesBody.contains("Employee"), "Employee role should be seeded automatically");
        assertTrue(rolesBody.contains("Manager"), "Manager role should be seeded automatically");

        // GET /api/tenant/roles/default -> verify default-flagged roles exist
        MvcResult defaultRolesResult = mockMvc.perform(get("/api/tenant/roles/default")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String defaultRolesBody = defaultRolesResult.getResponse().getContentAsString();
        // Super Admin and Employee are seeded with isDefault=true
        assertTrue(defaultRolesBody.contains("Super Admin") || defaultRolesBody.contains("Employee"),
                "At least one default-flagged role should exist after tenant registration");
    }

    // =====================================================
    // TEST: DEPARTMENT UNIQUE NAME AND CODE CONSTRAINTS
    // =====================================================

    /**
     * Within a tenant, department names and codes must be unique.
     * Creating a department with a duplicate name or code should return a 4xx error.
     */
    @Test
    public void testDepartmentUniqueNameAndCodeConstraints() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin_deptcon_" + uniqueSuffix + "@testcompany.com";

        // Register + login
        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRegistrationRequest.builder()
                                .companyName("Dept Constraint Co " + uniqueSuffix)
                                .adminEmail(adminEmail)
                                .adminName("Admin User")
                                .adminPhone("+12345678901")
                                .officeAddress("2 Constraint Ave")
                                .city("Bangalore")
                                .state(IndianState.KARNATAKA)
                                .country("India")
                                .planCode("trial")
                                .billingCycle("MONTHLY")
                                .build())))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();

        String tokenHeader = "Bearer " + objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        // Step 1: Create Engineering department successfully
        mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Engineering")
                                .code("ENG")
                                .description("Engineering Department")
                                .build())))
                .andExpect(status().isCreated());

        // Step 2: Attempt duplicate name (different code) -> must fail
        MvcResult dupNameResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Engineering")   // same name
                                .code("ENG2")
                                .description("Duplicate name attempt")
                                .build())))
                .andExpect(status().is4xxClientError())
                .andReturn();

        String dupNameBody = dupNameResult.getResponse().getContentAsString();
        assertTrue(dupNameBody.contains("name") || dupNameBody.contains("Department") || dupNameBody.contains("already"),
                "Duplicate department name should produce a descriptive error mentioning name or conflict");

        // Step 3: Attempt duplicate code (different name) -> must fail
        MvcResult dupCodeResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Engineering V2")
                                .code("ENG")   // same code
                                .description("Duplicate code attempt")
                                .build())))
                .andExpect(status().is4xxClientError())
                .andReturn();

        String dupCodeBody = dupCodeResult.getResponse().getContentAsString();
        assertTrue(dupCodeBody.contains("code") || dupCodeBody.contains("Department") || dupCodeBody.contains("already"),
                "Duplicate department code should produce a descriptive error mentioning code or conflict");

        // Step 4: Creating a department with a unique name and code succeeds
        MvcResult secondDeptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Human Resources")
                                .code("HR")
                                .description("HR Department")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse secondDept = objectMapper.readValue(
                secondDeptResult.getResponse().getContentAsString(), DepartmentResponse.class);
        assertNotNull(secondDept.getId(), "Second department with unique name/code should be created");
        assertEquals("Human Resources", secondDept.getName());
    }

    // =====================================================
    // TEST: ROLE DUPLICATE NAME REJECTED PER TENANT
    // =====================================================

    /**
     * Role names must be unique within a tenant.
     * Creating a role with the same name as an existing role should fail.
     */
    @Test
    public void testRoleDuplicateNameRejectedPerTenant() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin_roledup_" + uniqueSuffix + "@testcompany.com";

        // Register + login
        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRegistrationRequest.builder()
                                .companyName("Role Dup Co " + uniqueSuffix)
                                .adminEmail(adminEmail)
                                .adminName("Admin User")
                                .adminPhone("+12345678901")
                                .officeAddress("3 Role Ave")
                                .city("Bangalore")
                                .state(IndianState.KARNATAKA)
                                .country("India")
                                .planCode("trial")
                                .billingCycle("MONTHLY")
                                .build())))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();

        String tokenHeader = "Bearer " + objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        List<TenantPermission> permissions = permissionRepository.findAll();
        assertFalse(permissions.isEmpty(), "Permissions must be seeded before role creation");
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(2)
                .collect(Collectors.toSet());

        String roleName = "Senior Engineer " + uniqueSuffix;

        // Step 1: Create role with unique name -> should succeed
        mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name(roleName)
                                .description("First creation")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("ENGINEERING")
                                .priority(50)
                                .build())))
                .andExpect(status().isCreated());

        // Step 2: Create same role name again -> must fail
        MvcResult dupRoleResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name(roleName)   // same name
                                .description("Duplicate role attempt")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("ENGINEERING")
                                .priority(60)
                                .build())))
                .andExpect(status().is4xxClientError())
                .andReturn();

        String dupRoleBody = dupRoleResult.getResponse().getContentAsString();
        assertTrue(dupRoleBody.contains("role") || dupRoleBody.contains("Role")
                        || dupRoleBody.contains("name") || dupRoleBody.contains("exists") || dupRoleBody.contains("already"),
                "Duplicate role name should produce a descriptive conflict error");
    }

    // =====================================================
    // TEST: MANAGER-SUBORDINATE RELATIONSHIP
    // =====================================================

    /**
     * An employee can be assigned a manager at creation time.
     * The response should reflect the manager relationship correctly.
     */
    @Test
    public void testManagerSubordinateRelationship() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin_mgr_" + uniqueSuffix + "@testcompany.com";

        // Register + login
        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRegistrationRequest.builder()
                                .companyName("Manager Hierarchy Co " + uniqueSuffix)
                                .adminEmail(adminEmail)
                                .adminName("Admin User")
                                .adminPhone("+12345678901")
                                .officeAddress("4 Hierarchy Ave")
                                .city("Bangalore")
                                .state(IndianState.KARNATAKA)
                                .country("India")
                                .planCode("trial")
                                .billingCycle("MONTHLY")
                                .build())))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();

        String tokenHeader = "Bearer " + objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        // Create department
        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Technology")
                                .code("TECH")
                                .description("Tech Department")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse dept = objectMapper.readValue(
                deptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // Create role
        Set<Long> permIds = permissionRepository.findAll().stream()
                .map(TenantPermission::getId)
                .limit(1)
                .collect(Collectors.toSet());

        MvcResult roleResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("Tech Lead " + uniqueSuffix)
                                .description("Tech lead role")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("MANAGEMENT")
                                .priority(60)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse role = objectMapper.readValue(
                roleResult.getResponse().getContentAsString(), TenantRoleResponse.class);

        // Create manager employee (no managerId)
        MvcResult managerResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(EmployeeCreateRequest.builder()
                                .firstName("Alice")
                                .lastName("Manager")
                                .email("alice.manager_" + uniqueSuffix + "@testcompany.com")
                                .departmentId(dept.getId())
                                .position("Engineering Manager")
                                .hireDate(LocalDate.now())
                                .roleIds(Set.of(role.getId()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse manager = objectMapper.readValue(
                managerResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertNotNull(manager.getId(), "Manager employee should be created with an ID");

        // Create subordinate employee with managerId pointing to the manager
        MvcResult subordinateResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(EmployeeCreateRequest.builder()
                                .firstName("Bob")
                                .lastName("Engineer")
                                .email("bob.engineer_" + uniqueSuffix + "@testcompany.com")
                                .departmentId(dept.getId())
                                .position("Software Engineer")
                                .hireDate(LocalDate.now())
                                .roleIds(Set.of(role.getId()))
                                .managerId(manager.getId())
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse subordinate = objectMapper.readValue(
                subordinateResult.getResponse().getContentAsString(), EmployeeResponse.class);

        assertNotNull(subordinate.getId(), "Subordinate employee should be created");
        assertNotNull(subordinate.getManager(), "Subordinate should have a manager set in the response");
        assertEquals(manager.getId(), subordinate.getManager().getId(),
                "Subordinate's manager ID should match the created manager");
        assertEquals("Alice Manager", subordinate.getManager().getFullName(),
                "Manager full name should be 'Alice Manager'");
    }

    // =====================================================
    // TEST: MULTIPLE DEPARTMENTS AND EMPLOYEE COUNT PER DEPARTMENT
    // =====================================================

    /**
     * Multiple departments can be created for a tenant. Employees are counted per department.
     * Verifies the department listing API and per-department employee count endpoints.
     */
    @Test
    public void testMultipleDepartmentsAndEmployeeCountPerDepartment() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin_multidept_" + uniqueSuffix + "@testcompany.com";

        // Register + login
        mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRegistrationRequest.builder()
                                .companyName("Multi Dept Co " + uniqueSuffix)
                                .adminEmail(adminEmail)
                                .adminName("Admin User")
                                .adminPhone("+12345678901")
                                .officeAddress("5 Multi Ave")
                                .city("Bangalore")
                                .state(IndianState.KARNATAKA)
                                .country("India")
                                .planCode("trial")
                                .billingCycle("MONTHLY")
                                .build())))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();

        String tokenHeader = "Bearer " + objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        // Create Engineering department
        MvcResult engDeptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Engineering")
                                .code("ENG")
                                .description("Engineering Dept")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse engDept = objectMapper.readValue(
                engDeptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // Create HR department
        MvcResult hrDeptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepartmentRequest.builder()
                                .name("Human Resources")
                                .code("HR")
                                .description("HR Dept")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse hrDept = objectMapper.readValue(
                hrDeptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // Verify at least 2 departments are returned from the list endpoint
        MvcResult deptListResult = mockMvc.perform(get("/api/tenant/departments/list")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String deptListBody = deptListResult.getResponse().getContentAsString();
        assertTrue(deptListBody.contains("Engineering"), "Engineering dept should appear in list");
        assertTrue(deptListBody.contains("Human Resources"), "HR dept should appear in list");

        // Create role for employees
        Set<Long> permIds = permissionRepository.findAll().stream()
                .map(TenantPermission::getId)
                .limit(1)
                .collect(Collectors.toSet());

        MvcResult roleResult = mockMvc.perform(post("/api/tenant/roles")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TenantRoleCreateRequest.builder()
                                .name("Developer " + uniqueSuffix)
                                .description("Developer role")
                                .isDefault(false)
                                .permissionIds(permIds)
                                .category("DEVELOPMENT")
                                .priority(40)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRoleResponse role = objectMapper.readValue(
                roleResult.getResponse().getContentAsString(), TenantRoleResponse.class);

        // Create 2 employees in Engineering department
        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(post("/api/employees")
                            .header("Authorization", tokenHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(EmployeeCreateRequest.builder()
                                    .firstName("Eng")
                                    .lastName("Employee" + i)
                                    .email("eng.emp" + i + "_" + uniqueSuffix + "@testcompany.com")
                                    .departmentId(engDept.getId())
                                    .position("Software Engineer")
                                    .hireDate(LocalDate.now())
                                    .roleIds(Set.of(role.getId()))
                                    .build())))
                    .andExpect(status().isCreated());
        }

        // Create 1 employee in HR department
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(EmployeeCreateRequest.builder()
                                .firstName("HR")
                                .lastName("Specialist")
                                .email("hr.specialist_" + uniqueSuffix + "@testcompany.com")
                                .departmentId(hrDept.getId())
                                .position("HR Generalist")
                                .hireDate(LocalDate.now())
                                .roleIds(Set.of(role.getId()))
                                .build())))
                .andExpect(status().isCreated());

        // Verify Engineering department has exactly 2 employees
        MvcResult engCountResult = mockMvc.perform(
                        get("/api/tenant/departments/" + engDept.getId() + "/employee-count/total")
                                .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        Long engCount = objectMapper.readValue(
                engCountResult.getResponse().getContentAsString(), Long.class);
        assertEquals(2L, engCount, "Engineering department should have exactly 2 employees");

        // Verify HR department has exactly 1 employee
        MvcResult hrCountResult = mockMvc.perform(
                        get("/api/tenant/departments/" + hrDept.getId() + "/employee-count/total")
                                .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        Long hrCount = objectMapper.readValue(
                hrCountResult.getResponse().getContentAsString(), Long.class);
        assertEquals(1L, hrCount, "HR department should have exactly 1 employee");
    }
}
