package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.department.DepartmentRequest;
import com.sonixhr.dto.department.DepartmentResponse;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.employee.EmployeeUpdateRequest;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.employee.EmploymentType;
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

import java.time.LocalDate;
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
public class EmployeeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantPermissionRepository permissionRepository;

    private String tokenHeader;
    private Long departmentId;
    private Long roleId;

    // =====================================================
    // SETUP: Register tenant, login, create dept & role
    // =====================================================

    @BeforeEach
    public void setUp() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Emp Test Co " + uniqueSuffix;
        String adminEmail = "admin_emp_" + uniqueSuffix + "@emptest.com";

        // Register Tenant
        TenantRegistrationRequest regRequest = TenantRegistrationRequest.builder()
                .companyName(companyName)
                .adminEmail(adminEmail)
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("123 Main Street")
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

        objectMapper.readValue(
                regResult.getResponse().getContentAsString(), TenantRegistrationResponse.class);

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

        // Create Department
        DepartmentRequest deptRequest = DepartmentRequest.builder()
                .name("Engineering " + uniqueSuffix)
                .code("ENG-" + uniqueSuffix.substring(0, 4))
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
        this.departmentId = deptResponse.getId();

        // Create Role (using existing permissions)
        List<TenantPermission> permissions = permissionRepository.findAll();
        Set<Long> permIds = permissions.stream()
                .map(TenantPermission::getId)
                .limit(5)
                .collect(Collectors.toSet());

        TenantRoleCreateRequest roleRequest = TenantRoleCreateRequest.builder()
                .name("Dev Role " + uniqueSuffix)
                .description("Developer role for testing")
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
        this.roleId = roleResponse.getId();
    }

    // =====================================================
    // HELPER: Build a unique EmployeeCreateRequest
    // =====================================================

    private EmployeeCreateRequest buildEmployeeRequest(String suffix) {
        return EmployeeCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe." + suffix + "@emptest.com")
                .departmentId(departmentId)
                .position("Software Engineer")
                .hireDate(LocalDate.now())
                .phone("+919876543210")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .workLocation("Office")
                .employmentType(EmploymentType.FULL_TIME)
                .roleIds(Set.of(roleId))
                .build();
    }

    // =====================================================
    // TEST 1: Full CRUD Lifecycle
    // =====================================================

    @Test
    public void testEmployeeCRUDLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        EmployeeCreateRequest createReq = buildEmployeeRequest(suffix);

        // 1. Create Employee
        MvcResult createResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertNotNull(created.getId());
        assertEquals("John", created.getFirstName());
        assertEquals("Doe", created.getLastName());
        assertEquals("john.doe." + suffix + "@emptest.com", created.getEmail());
        assertEquals("Software Engineer", created.getPosition());
        assertNotNull(created.getEmployeeCode(), "Employee code should be auto-generated");
        assertNotNull(created.getDepartment());
        assertEquals(departmentId, created.getDepartment().getId());

        Long employeeId = created.getId();
        String employeeCode = created.getEmployeeCode();

        // 2. Get Employee by ID
        MvcResult getByIdResult = mockMvc.perform(get("/api/employees/" + employeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        EmployeeResponse gotById = objectMapper.readValue(
                getByIdResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertEquals(employeeId, gotById.getId());
        assertEquals("John", gotById.getFirstName());
        assertEquals("Doe", gotById.getLastName());

        // 3. Get Employee by Code
        MvcResult getByCodeResult = mockMvc.perform(get("/api/employees/code/" + employeeCode)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        EmployeeResponse gotByCode = objectMapper.readValue(
                getByCodeResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertEquals(employeeId, gotByCode.getId());
        assertEquals(employeeCode, gotByCode.getEmployeeCode());

        // 4. Get Employee by Email
        MvcResult getByEmailResult = mockMvc.perform(get("/api/employees/email/" + createReq.getEmail())
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        EmployeeResponse gotByEmail = objectMapper.readValue(
                getByEmailResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertEquals(employeeId, gotByEmail.getId());
        assertEquals(createReq.getEmail(), gotByEmail.getEmail());

        // 5. Update Employee
        EmployeeUpdateRequest updateReq = new EmployeeUpdateRequest();
        updateReq.setFirstName("Jane");
        updateReq.setLastName("Smith");
        updateReq.setEmail(createReq.getEmail()); // keep same email
        updateReq.setDepartmentId(departmentId);
        updateReq.setPosition("Senior Software Engineer");
        updateReq.setHireDate(LocalDate.now());
        updateReq.setCity("Mumbai");
        updateReq.setState(IndianState.MAHARASHTRA);
        updateReq.setCountry("India");
        updateReq.setEmploymentType(EmploymentType.FULL_TIME);

        MvcResult updateResult = mockMvc.perform(put("/api/employees/" + employeeId)
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andReturn();

        EmployeeResponse updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertEquals(employeeId, updated.getId());
        assertEquals("Jane", updated.getFirstName());
        assertEquals("Smith", updated.getLastName());
        assertEquals("Senior Software Engineer", updated.getPosition());
        assertEquals("Mumbai", updated.getCity());

        // 6. Soft Delete Employee
        mockMvc.perform(delete("/api/employees/" + employeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNoContent());

        // 7. Verify employee is gone (should return 404)
        mockMvc.perform(get("/api/employees/" + employeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isNotFound());
    }

    // =====================================================
    // TEST 2: Get Current Employee (GET /me)
    // =====================================================

    @Test
    public void testGetCurrentEmployee() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        assertTrue(body.contains("\"id\""));
        assertTrue(body.contains("\"email\""));
    }

    // =====================================================
    // TEST 3: Get All Employees (Paginated)
    // =====================================================

    @Test
    public void testGetAllEmployeesPaginated() throws Exception {
        // Create an employee first
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildEmployeeRequest(suffix))))
                .andExpect(status().isCreated());

        // Get all employees (paginated)
        MvcResult listResult = mockMvc.perform(get("/api/employees")
                        .header("Authorization", tokenHeader)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();

        String body = listResult.getResponse().getContentAsString();
        assertTrue(body.contains("\"content\""));
        assertTrue(body.contains("\"totalElements\""));
    }

    // =====================================================
    // TEST 4: Duplicate Email Validation
    // =====================================================

    @Test
    public void testDuplicateEmailValidation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        EmployeeCreateRequest emp1 = buildEmployeeRequest(suffix);

        // Create first employee
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp1)))
                .andExpect(status().isCreated());

        // Try same email again → should fail
        MvcResult dupResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp1)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String dupBody = dupResult.getResponse().getContentAsString();
        assertTrue(dupBody.contains("\"errors\""), "Response should contain errors map");
        assertTrue(dupBody.contains("\"email\""), "Error should be on email field");
    }

    // =====================================================
    // TEST 5: Missing Required Fields Validation
    // =====================================================

    @Test
    public void testMissingRequiredFieldsValidation() throws Exception {
        // Request missing firstName, lastName, email, departmentId, position, hireDate
        EmployeeCreateRequest invalid = EmployeeCreateRequest.builder().build();

        MvcResult result = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"errors\""), "Should return validation errors");
    }

    // =====================================================
    // TEST 6: Search Employees
    // =====================================================

    @Test
    public void testSearchEmployees() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Create an employee to search for
        EmployeeCreateRequest emp = EmployeeCreateRequest.builder()
                .firstName("SearchFirst" + suffix)
                .lastName("SearchLast")
                .email("search.emp." + suffix + "@emptest.com")
                .departmentId(departmentId)
                .position("QA Engineer")
                .hireDate(LocalDate.now())
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .employmentType(EmploymentType.FULL_TIME)
                .roleIds(Set.of(roleId))
                .build();

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp)))
                .andExpect(status().isCreated());

        // Search by first name
        MvcResult searchResult = mockMvc.perform(get("/api/employees/search")
                        .header("Authorization", tokenHeader)
                        .param("searchTerm", "SearchFirst" + suffix))
                .andExpect(status().isOk())
                .andReturn();

        String body = searchResult.getResponse().getContentAsString();
        assertTrue(body.contains("\"content\""));
        assertTrue(body.contains("SearchFirst" + suffix));
    }

    // =====================================================
    // TEST 7: Get Employees by Department Name
    // =====================================================

    @Test
    public void testGetEmployeesByDepartmentName() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Create dept with a known name
        String deptName = "SpecialDept " + suffix;
        DepartmentRequest specialDept = DepartmentRequest.builder()
                .name(deptName)
                .code("SPD-" + suffix.substring(0, 4))
                .description("Special Dept")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specialDept)))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse specialDeptResp = objectMapper.readValue(
                deptResult.getResponse().getContentAsString(), DepartmentResponse.class);

        // Create an employee in that dept
        EmployeeCreateRequest emp = EmployeeCreateRequest.builder()
                .firstName("DeptEmp")
                .lastName("Test")
                .email("dept.emp." + suffix + "@emptest.com")
                .departmentId(specialDeptResp.getId())
                .position("Analyst")
                .hireDate(LocalDate.now())
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .employmentType(EmploymentType.FULL_TIME)
                .roleIds(Set.of(roleId))
                .build();

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp)))
                .andExpect(status().isCreated());

        // Get employees by dept name
        MvcResult result = mockMvc.perform(get("/api/employees/department/name/" + deptName)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("DeptEmp"), "Should return employee in that department");
    }

    // =====================================================
    // TEST 8: Manager Assignment and Team Hierarchy
    // =====================================================

    @Test
    public void testManagerAssignmentAndTeam() throws Exception {
        String s1 = UUID.randomUUID().toString().substring(0, 8);
        String s2 = UUID.randomUUID().toString().substring(0, 8);

        // Create Manager employee
        EmployeeCreateRequest managerReq = EmployeeCreateRequest.builder()
                .firstName("Manager")
                .lastName("One")
                .email("manager." + s1 + "@emptest.com")
                .departmentId(departmentId)
                .position("Engineering Manager")
                .hireDate(LocalDate.now())
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .employmentType(EmploymentType.FULL_TIME)
                .roleIds(Set.of(roleId))
                .build();

        MvcResult managerResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(managerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse manager = objectMapper.readValue(
                managerResult.getResponse().getContentAsString(), EmployeeResponse.class);
        Long managerId = manager.getId();
        String managerCode = manager.getEmployeeCode();

        // Create subordinate employee
        EmployeeCreateRequest subordinateReq = EmployeeCreateRequest.builder()
                .firstName("Subordinate")
                .lastName("Two")
                .email("subordinate." + s2 + "@emptest.com")
                .departmentId(departmentId)
                .position("Junior Developer")
                .hireDate(LocalDate.now())
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .employmentType(EmploymentType.FULL_TIME)
                .roleIds(Set.of(roleId))
                .build();

        MvcResult subordinateResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subordinateReq)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse subordinate = objectMapper.readValue(
                subordinateResult.getResponse().getContentAsString(), EmployeeResponse.class);
        String subordinateCode = subordinate.getEmployeeCode();
        Long subordinateId = subordinate.getId();

        // Assign manager to subordinate using employee codes
        MvcResult assignResult = mockMvc.perform(put("/api/employees/by-code/" + subordinateCode + "/manager")
                        .header("Authorization", tokenHeader)
                        .param("managerCode", managerCode))
                .andExpect(status().isOk())
                .andReturn();

        EmployeeResponse afterAssign = objectMapper.readValue(
                assignResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertNotNull(afterAssign.getManager(), "Manager should be assigned");
        assertEquals(managerId, afterAssign.getManager().getId());

        // Get team members for manager (paginated)
        MvcResult teamResult = mockMvc.perform(get("/api/employees/managers/" + managerId + "/team")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String teamBody = teamResult.getResponse().getContentAsString();
        assertTrue(teamBody.contains("\"content\""));
        assertTrue(teamBody.contains("Subordinate"), "Team should include subordinate");

        // Get team list (non-paginated)
        MvcResult teamListResult = mockMvc.perform(get("/api/employees/" + managerId + "/team/list")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String teamListBody = teamListResult.getResponse().getContentAsString();
        assertTrue(teamListBody.contains("Subordinate"));

        // Check if manager is actually a manager
        MvcResult isManagerResult = mockMvc.perform(get("/api/employees/" + managerId + "/is-manager")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        Boolean isManager = objectMapper.readValue(
                isManagerResult.getResponse().getContentAsString(), Boolean.class);
        assertTrue(isManager, "Employee with direct reports should be flagged as manager");

        // Get manager chain of subordinate
        MvcResult chainResult = mockMvc.perform(get("/api/employees/" + subordinateId + "/manager-chain")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String chainBody = chainResult.getResponse().getContentAsString();
        // Should contain the manager in the chain
        assertTrue(chainBody.contains("Manager"), "Manager chain should include the assigned manager");
    }

    // =====================================================
    // TEST 9: Employee Status Update
    // =====================================================

    @Test
    public void testEmployeeStatusUpdate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        EmployeeCreateRequest emp = buildEmployeeRequest(suffix);

        MvcResult createResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), EmployeeResponse.class);
        Long employeeId = created.getId();

        // Update status to ACTIVE
        mockMvc.perform(patch("/api/employees/" + employeeId + "/status")
                        .header("Authorization", tokenHeader)
                        .param("status", "ACTIVE")
                        .param("reason", "Probation completed"))
                .andExpect(status().isOk());

        // Verify status change
        MvcResult getResult = mockMvc.perform(get("/api/employees/" + employeeId)
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String body = getResult.getResponse().getContentAsString();
        assertTrue(body.contains("ACTIVE"), "Employee status should be ACTIVE");
    }

    // =====================================================
    // TEST 10: Organization Chart
    // =====================================================

    @Test
    public void testGetOrganizationChart() throws Exception {
        // Create at least one employee
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildEmployeeRequest(suffix))))
                .andExpect(status().isCreated());

        // Get org chart
        mockMvc.perform(get("/api/employees/organization-chart")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());
    }

    // =====================================================
    // TEST 11: Get Employees by Status
    // =====================================================

    @Test
    public void testGetEmployeesByStatus() throws Exception {
        mockMvc.perform(get("/api/employees/status/INVITED")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/employees/status/ACTIVE")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());
    }

    // =====================================================
    // TEST 12: Get Active Employees Dropdown
    // =====================================================

    @Test
    public void testGetActiveEmployeesForDropdown() throws Exception {
        mockMvc.perform(get("/api/employees/dropdown")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());
    }

    // =====================================================
    // TEST 13: Department Statistics
    // =====================================================

    @Test
    public void testGetDepartmentStatistics() throws Exception {
        mockMvc.perform(get("/api/employees/statistics/departments")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk());
    }

    // =====================================================
    // TEST 14: Employees with No Manager
    // =====================================================

    @Test
    public void testGetEmployeesWithNoManager() throws Exception {
        // Create an employee without a manager
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildEmployeeRequest(suffix))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/employees/no-manager")
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        // Should be a JSON array
        assertTrue(body.startsWith("["), "Should return a list");
    }

    // =====================================================
    // TEST 15: Search for Assignment (Dropdown)
    // =====================================================

    @Test
    public void testSearchEmployeesForAssignment() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        EmployeeCreateRequest emp = EmployeeCreateRequest.builder()
                .firstName("AssignSearch" + suffix)
                .lastName("Test")
                .email("assign.search." + suffix + "@emptest.com")
                .departmentId(departmentId)
                .position("Developer")
                .hireDate(LocalDate.now())
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .employmentType(EmploymentType.FULL_TIME)
                .roleIds(Set.of(roleId))
                .build();

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emp)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/employees/search/assignment")
                        .header("Authorization", tokenHeader)
                        .param("query", "AssignSearch" + suffix)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"content\""));
        assertTrue(body.contains("AssignSearch" + suffix));
    }

    // =====================================================
    // TEST 16: Full Flow - Tenant → Dept → Role → Employee
    // =====================================================

    @Test
    public void testFullEmployeeOnboardingFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create fresh department for this flow
        DepartmentRequest newDept = DepartmentRequest.builder()
                .name("Product " + suffix)
                .code("PRD-" + suffix.substring(0, 4))
                .description("Product Department")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/tenant/departments")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newDept)))
                .andExpect(status().isCreated())
                .andReturn();

        DepartmentResponse dept = objectMapper.readValue(
                deptResult.getResponse().getContentAsString(), DepartmentResponse.class);
        assertNotNull(dept.getId());

        // 2. Create employee in that department
        EmployeeCreateRequest empReq = EmployeeCreateRequest.builder()
                .firstName("Product")
                .lastName("Lead" + suffix)
                .email("product.lead." + suffix + "@emptest.com")
                .departmentId(dept.getId())
                .position("Product Lead")
                .hireDate(LocalDate.of(2024, 1, 15))
                .phone("+919876543211")
                .city("Hyderabad")
                .state(IndianState.TELANGANA)
                .country("India")
                .workLocation("Remote")
                .employmentType(EmploymentType.FULL_TIME)
                .roleIds(Set.of(roleId))
                .build();

        MvcResult empResult = mockMvc.perform(post("/api/employees")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empReq)))
                .andExpect(status().isCreated())
                .andReturn();

        EmployeeResponse emp = objectMapper.readValue(
                empResult.getResponse().getContentAsString(), EmployeeResponse.class);

        // Verify all fields
        assertNotNull(emp.getId());
        assertNotNull(emp.getEmployeeCode());
        assertEquals("Product", emp.getFirstName());
        assertEquals("Lead" + suffix, emp.getLastName());
        assertEquals("product.lead." + suffix + "@emptest.com", emp.getEmail());
        assertEquals("Product Lead", emp.getPosition());
        assertEquals("Remote", emp.getWorkLocation());
        assertEquals("Hyderabad", emp.getCity());
        assertNotNull(emp.getDepartment());
        assertEquals(dept.getId(), emp.getDepartment().getId());
        assertEquals(dept.getName(), emp.getDepartment().getName());

        // 3. Verify via GET by ID
        MvcResult verifyResult = mockMvc.perform(get("/api/employees/" + emp.getId())
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        EmployeeResponse verified = objectMapper.readValue(
                verifyResult.getResponse().getContentAsString(), EmployeeResponse.class);
        assertEquals(emp.getId(), verified.getId());
        assertEquals("product.lead." + suffix + "@emptest.com", verified.getEmail());

        // 4. Verify employee appears in dept employees list
        MvcResult deptEmpResult = mockMvc.perform(get("/api/employees/department/name/" + dept.getName())
                        .header("Authorization", tokenHeader))
                .andExpect(status().isOk())
                .andReturn();

        String deptEmpBody = deptEmpResult.getResponse().getContentAsString();
        assertTrue(deptEmpBody.contains("Lead" + suffix), "Employee should appear in department listing");
    }
}
