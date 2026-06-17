package com.sonixhr;

import com.sonixhr.bootstrap.TenantSeeder;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.attendance.ShiftConfigurationRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.employee.EmployeeService;
import com.sonixhr.controller.TenantAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SonixhrApplicationTests {

	@Autowired
	private TenantSeeder tenantSeeder;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private TenantRoleRepository tenantRoleRepository;

	@Autowired
	private ShiftConfigurationRepository shiftConfigurationRepository;

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private EmployeeService employeeService;

	@Autowired
	private TenantAuthController tenantAuthController;

	@Autowired
	private com.sonixhr.controller.employee.EmployeeSelfServiceController employeeSelfServiceController;

	@Autowired
	private com.sonixhr.controller.employee.EmployeeController employeeController;

	@Autowired
	private com.sonixhr.controller.leave.LeaveController leaveController;

	@Autowired
	private com.sonixhr.repository.department.DepartmentRepository departmentRepository;

	@Autowired
	private com.sonixhr.repository.leave.TenantLeaveSettingsRepository tenantLeaveSettingsRepository;

	@Autowired
	private com.sonixhr.service.leave.LeaveConfigurationService leaveConfigService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private org.springframework.transaction.PlatformTransactionManager transactionManager;

	@Test
	void contextLoadsAndSeedsDefaultTenantAndShift() throws Exception {
		// Clean up database for deterministic test within a committed transaction
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			try {
				jdbcTemplate.execute("ALTER TABLE tenants DROP COLUMN IF EXISTS subdomain CASCADE");
			} catch (Exception e) {
				// Ignore if there is an issue altering the table structure
			}
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Run seeder
		tenantSeeder.run(null);

		assertTrue(tenantRepository.count() > 0, "Default tenant should be seeded");
		assertTrue(shiftConfigurationRepository.count() > 0, "Default shift configuration should be seeded");
	}

	@Test
	void assignShiftToEmployeeOnCreationAndLogin() throws Exception {
		// Clean database for deterministic test
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Run seeder to seed default tenant
		tenantSeeder.run(null);

		// Get seeded tenant and default shift
		com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findAll().get(0);
		com.sonixhr.entity.attendance.ShiftConfiguration defaultShift = shiftConfigurationRepository.findAll().get(0);

		// Fetch the Super Admin employee created during seeding
		com.sonixhr.entity.employee.Employee superAdmin = employeeRepository.findByEmailWithRolesAndPermissions("admin@acme.com")
				.orElseThrow(() -> new AssertionError("Super Admin employee not found"));

		// Seeded Super Admin should initially have no shift assigned
		assertNull(superAdmin.getShift(), "Seeded Super Admin shift should be null initially");

		// Simulate login
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						superAdmin, "Admin@123", superAdmin.getAuthorities()
				)
		);

		com.sonixhr.dto.LoginRequest loginRequest = new com.sonixhr.dto.LoginRequest();
		loginRequest.setEmail("admin@acme.com");
		loginRequest.setPassword("Admin@123");
		tenantAuthController.login(loginRequest, "WEB");

		// Fetch employee again and check that shift is assigned
		com.sonixhr.entity.employee.Employee updatedSuperAdmin = employeeRepository.findById(superAdmin.getId()).get();
		assertNotNull(updatedSuperAdmin.getShift(), "Super Admin should have shift assigned on login");
		assertEquals(defaultShift.getId(), updatedSuperAdmin.getShift().getId());

		// Test employee creation assigns default shift
		com.sonixhr.dto.employee.EmployeeCreateRequest createRequest = com.sonixhr.dto.employee.EmployeeCreateRequest.builder()
				.firstName("John")
				.lastName("Doe")
				.email("john.doe@acme.com")
				.phone("9876543210")
				.roleIds(java.util.Collections.singleton(superAdmin.getRoles().iterator().next().getId()))
				.build();

		com.sonixhr.dto.employee.EmployeeResponse response = employeeService.createEmployee(tenant.getId(), createRequest);

		assertNotNull(response.getShift(), "Created employee should have shift assigned in response");
		assertEquals(defaultShift.getShiftName(), response.getShift().getShiftName());

		com.sonixhr.entity.employee.Employee createdEmp = employeeRepository.findById(response.getId()).get();
		assertNotNull(createdEmp.getShift(), "Created employee should have shift assigned in DB");
		assertEquals(defaultShift.getId(), createdEmp.getShift().getId());
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void getPersonalizedOrgChart_shouldReturnCorrectHierarchy() throws Exception {
		// Clean database
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Seed default tenant
		tenantSeeder.run(null);

		com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findAll().get(0);
		com.sonixhr.entity.employee.Employee ceo = employeeRepository.findByEmailWithRolesAndPermissions("admin@acme.com")
				.orElseThrow(() -> new AssertionError("CEO not found"));

		// Fetch managed roles from DB
		java.util.List<com.sonixhr.entity.tenant.TenantRole> roles = tenantRoleRepository.findAll();

		// Create Director under CEO
		com.sonixhr.entity.employee.Employee director = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("DIR001")
				.firstName("Alice")
				.lastName("Director")
				.email("alice.director@acme.com")
				.phone("1111111111")
				.position("Director")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now())
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.manager(ceo)
				.build();
		director.getRoles().addAll(roles);
		director = employeeRepository.save(director);

		// Create Peer 1 under Director
		com.sonixhr.entity.employee.Employee peer1 = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("PEER001")
				.firstName("Bob")
				.lastName("Peer")
				.email("bob.peer@acme.com")
				.phone("2222222222")
				.position("Engineer")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now())
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.manager(director)
				.build();
		peer1.getRoles().addAll(roles);
		peer1 = employeeRepository.save(peer1);

		// Create Target Employee under Director
		com.sonixhr.entity.employee.Employee target = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("TAR001")
				.firstName("John")
				.lastName("Target")
				.email("john.target@acme.com")
				.phone("3333333333")
				.position("Senior Engineer")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now())
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.manager(director)
				.build();
		target.getRoles().addAll(roles);
		target = employeeRepository.save(target);

		// Create Subordinate under Target
		com.sonixhr.entity.employee.Employee sub = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("SUB001")
				.firstName("Eve")
				.lastName("Subordinate")
				.email("eve.sub@acme.com")
				.phone("4444444444")
				.position("Junior Engineer")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now())
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.manager(target)
				.build();
		sub.getRoles().addAll(roles);
		sub = employeeRepository.save(sub);

		// Set authenticated context for John Target
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						target, "password", target.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		// Query personalized organization chart
		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.MyOrgChartResponse> responseEntity =
				employeeSelfServiceController.getMyOrgChart(target);

		com.sonixhr.dto.employee.MyOrgChartResponse chart = responseEntity.getBody();

		assertNotNull(chart);
		assertEquals(target.getId(), chart.getEmployee().getId());
		
		// Manager chain should be Director -> CEO
		assertEquals(2, chart.getManagerChain().size());
		assertEquals(director.getId(), chart.getManagerChain().get(0).getId());
		assertEquals(ceo.getId(), chart.getManagerChain().get(1).getId());

		// Peers should contain Peer 1 (Bob Peer)
		assertEquals(1, chart.getPeers().size());
		assertEquals(peer1.getId(), chart.getPeers().get(0).getId());

		// Direct reports should contain Subordinate (Eve Subordinate)
		assertEquals(1, chart.getDirectReports().size());
		assertEquals(sub.getId(), chart.getDirectReports().get(0).getId());

		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void testEmployeeControllerApis() throws Exception {
		// Clean database
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Seed default tenant
		tenantSeeder.run(null);

		com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findAll().get(0);
		com.sonixhr.entity.employee.Employee superAdmin = employeeRepository.findByEmailWithRolesAndPermissions("admin@acme.com")
				.orElseThrow(() -> new AssertionError("Super Admin employee not found"));

		// Simulate authenticated context for superAdmin
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						superAdmin, "Admin@123", superAdmin.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		// 1. Get current employee (me)
		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.EmployeeResponse> meResponse =
				employeeController.getCurrentEmployee(superAdmin);
		assertNotNull(meResponse.getBody());
		assertEquals(superAdmin.getEmail(), meResponse.getBody().getEmail());

		// 2. Create a new employee
		com.sonixhr.dto.employee.EmployeeCreateRequest createRequest = com.sonixhr.dto.employee.EmployeeCreateRequest.builder()
				.firstName("John")
				.lastName("Doe")
				.email("john.doe@acme.com")
				.phone("9876543210")
				.position("Software Engineer")
				.hireDate(java.time.LocalDate.now())
				.roleIds(java.util.Collections.singleton(superAdmin.getRoles().iterator().next().getId()))
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.EmployeeResponse> createResponse =
				employeeController.createEmployee(createRequest, superAdmin);
		assertNotNull(createResponse.getBody());
		Long newEmployeeId = createResponse.getBody().getId();
		String newEmployeeCode = createResponse.getBody().getEmployeeCode();

		// 3. Get employee by ID
		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.EmployeeResponse> getByIdResponse =
				employeeController.getEmployeeById(newEmployeeId, superAdmin);
		assertNotNull(getByIdResponse.getBody());
		assertEquals("John", getByIdResponse.getBody().getFirstName());

		// 4. Get employee by Code
		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.EmployeeResponse> getByCodeResponse =
				employeeController.getEmployeeByCode(newEmployeeCode, superAdmin);
		assertNotNull(getByCodeResponse.getBody());
		assertEquals("john.doe@acme.com", getByCodeResponse.getBody().getEmail());

		// 5. Get employee by Email
		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.EmployeeResponse> getByEmailResponse =
				employeeController.getEmployeeByEmail("john.doe@acme.com", superAdmin);
		assertNotNull(getByEmailResponse.getBody());
		assertEquals("John", getByEmailResponse.getBody().getFirstName());

		// 6. Get all employees
		org.springframework.http.ResponseEntity<org.springframework.data.domain.Page<com.sonixhr.dto.employee.EmployeeSummaryResponse>> allResponse =
				employeeController.getAllEmployees(superAdmin, org.springframework.data.domain.PageRequest.of(0, 10));
		assertNotNull(allResponse.getBody());
		assertTrue(allResponse.getBody().getTotalElements() >= 2);

		// 7. Update employee
		createRequest.setFirstName("Johnny");
		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.EmployeeResponse> updateResponse =
				employeeController.updateEmployee(newEmployeeId, createRequest, superAdmin);
		assertNotNull(updateResponse.getBody());
		assertEquals("Johnny", updateResponse.getBody().getFirstName());

		// 8. Search employees
		org.springframework.http.ResponseEntity<org.springframework.data.domain.Page<com.sonixhr.dto.employee.EmployeeSummaryResponse>> searchResponse =
				employeeController.searchEmployees("Johnny", superAdmin, org.springframework.data.domain.PageRequest.of(0, 10));
		assertNotNull(searchResponse.getBody());
		assertEquals(1, searchResponse.getBody().getTotalElements());

		// 9. Soft Delete employee
		org.springframework.http.ResponseEntity<Void> deleteResponse =
				employeeController.deleteEmployee(newEmployeeId, superAdmin);
		assertEquals(org.springframework.http.HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void testLeaveApplyAndApprove() throws Exception {
		// Clean database
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Seed default tenant
		tenantSeeder.run(null);

		com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findAll().get(0);
		
		// Set up Tenant Leave Settings (Ensure policiesConfigured is true)
		com.sonixhr.entity.leave.TenantLeaveSettings settings = leaveConfigService.getTenantSettings(tenant.getId());
		settings.setPoliciesConfigured(true);
		tenantLeaveSettingsRepository.save(settings);

		// Get seeded Super Admin role
		com.sonixhr.entity.tenant.TenantRole superAdminRole = tenantRoleRepository.findAll().get(0);

		// Create Department
		com.sonixhr.entity.department.Department department = com.sonixhr.entity.department.Department.builder()
				.tenant(tenant)
				.name("Engineering")
				.code("ENG")
				.isActive(true)
				.build();
		department = departmentRepository.save(department);

		// Create Manager (the approver)
		com.sonixhr.entity.employee.Employee manager = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("MGR001")
				.firstName("Jane")
				.lastName("Manager")
				.email("jane.manager@acme.com")
				.phone("9876543211")
				.position("Engineering Manager")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now().minusYears(1))
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.department(department)
				.roles(new java.util.HashSet<>(java.util.List.of(superAdminRole)))
				.build();
		manager = employeeRepository.save(manager);

		// Create Employee (the requester, reporting to Manager)
		com.sonixhr.entity.employee.Employee employee = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("EMP001")
				.firstName("John")
				.lastName("Requester")
				.email("john.requester@acme.com")
				.phone("9876543212")
				.position("Software Engineer")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now())
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.department(department)
				.manager(manager)
				.roles(new java.util.HashSet<>(java.util.List.of(superAdminRole)))
				.build();
		employee = employeeRepository.save(employee);

		// Authenticate as employee to apply for leave
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						employee, "password", employee.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		// 1. Check leave balance (should succeed and return CASUAL balance info)
		org.springframework.http.ResponseEntity<java.util.Map<String, Object>> balanceResponse =
				leaveController.getLeaveBalance(employee);
		assertNotNull(balanceResponse.getBody());
		assertTrue(balanceResponse.getBody().containsKey("CASUAL"));

		// 2. Request Leave (Future dates: weekday only)
		java.time.LocalDate startDate = java.time.LocalDate.now().plusDays(1);
		while (startDate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || startDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
			startDate = startDate.plusDays(1);
		}
		java.time.LocalDate endDate = startDate.plusDays(1);

		com.sonixhr.dto.leave.LeaveRequestDTO leaveRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(startDate)
				.endDate(endDate)
				.reason("Family event")
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> applyResponse =
				leaveController.requestLeave(leaveRequest, employee);
		assertNotNull(applyResponse.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.PENDING, applyResponse.getBody().getStatus());
		Long leaveId = applyResponse.getBody().getId();

		// 3. Authenticate as manager to approve the leave
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						manager, "password", manager.getAuthorities()
				)
		);

		// 4. Approve leave request
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> approveResponse =
				leaveController.approveLeave(leaveId, manager);
		assertNotNull(approveResponse.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.APPROVED, approveResponse.getBody().getStatus());
		assertEquals(manager.getId(), approveResponse.getBody().getApprovedBy());

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

}

