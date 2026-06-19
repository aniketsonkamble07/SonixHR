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
@SuppressWarnings("null")
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
	private com.sonixhr.controller.leave.EmployeeLeaveController employeeLeaveController;

	@Autowired
	private com.sonixhr.controller.leave.LeaveManagementController leaveManagementController;

	@Autowired
	private com.sonixhr.controller.attendance.ManualAttendanceController manualAttendanceController;

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
				employeeLeaveController.getLeaveBalance(employee);
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
				employeeLeaveController.requestLeave(leaveRequest, employee);
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
				leaveManagementController.approveLeave(leaveId, manager);
		assertNotNull(approveResponse.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.APPROVED, approveResponse.getBody().getStatus());
		assertEquals(manager.getId(), approveResponse.getBody().getApprovedBy());

		// 5. Test employee leave setting override
		org.springframework.http.ResponseEntity<com.sonixhr.dto.employee.EmployeeResponse> overrideResponse =
				leaveManagementController.updateEmployeeLeaveSettings(
						employee.getId(),
						com.sonixhr.enums.leave.WeekendConfig.CUSTOM,
						"SATURDAY",
						manager
				);
		assertNotNull(overrideResponse.getBody());

		// 6. Test getTenantLeaveSettings & updateTenantLeaveSettings
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveSettingsDTO> settingsRes =
				leaveManagementController.getTenantLeaveSettings(manager);
		assertNotNull(settingsRes.getBody());

		com.sonixhr.dto.leave.LeaveSettingsDTO settingsDTO = new com.sonixhr.dto.leave.LeaveSettingsDTO();
		settingsDTO.setCasualLeavePerYear(15);
		settingsDTO.setSickLeavePerYear(12);
		settingsDTO.setEarnedLeavePerYear(20);
		settingsDTO.setLeaveApprovalRequired(true);
		settingsDTO.setAutoApproveForManager(true);
		settingsDTO.setCountWeekendsAsLeave(false);
		settingsDTO.setCountHolidaysAsLeave(false);
		settingsDTO.setWeekendConfig(com.sonixhr.enums.leave.WeekendConfig.SATURDAY_SUNDAY);

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveSettingsDTO> updatedSettingsRes =
				leaveManagementController.updateTenantLeaveSettings(settingsDTO, manager);
		assertNotNull(updatedSettingsRes.getBody());
		assertEquals(Integer.valueOf(15), updatedSettingsRes.getBody().getCasualLeavePerYear());

		// 7. Test getTeamLeaveRequests
		org.springframework.http.ResponseEntity<org.springframework.data.domain.Page<com.sonixhr.dto.leave.LeaveResponseDTO>> teamRequests =
				leaveManagementController.getTeamLeaveRequests(null, manager, org.springframework.data.domain.PageRequest.of(0, 10));
		assertNotNull(teamRequests.getBody());
		assertTrue(teamRequests.getBody().getTotalElements() > 0);

		// 8. Authenticate back as employee to test getMyLeaves, cancelLeave
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						employee, "password", employee.getAuthorities()
				)
		);

		org.springframework.http.ResponseEntity<java.util.List<com.sonixhr.dto.leave.LeaveResponseDTO>> myLeaves =
				employeeLeaveController.getMyLeaves(employee);
		assertNotNull(myLeaves.getBody());
		assertTrue(myLeaves.getBody().size() > 0);

		// Apply for a second leave to test cancellation
		com.sonixhr.dto.leave.LeaveRequestDTO secondRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.SICK)
				.startDate(startDate.plusDays(10))
				.endDate(startDate.plusDays(11))
				.reason("Sick leave")
				.build();
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> secondApply =
				employeeLeaveController.requestLeave(secondRequest, employee);
		assertNotNull(secondApply.getBody());
		Long secondLeaveId = secondApply.getBody().getId();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> cancelRes =
				employeeLeaveController.cancelLeave(secondLeaveId, "Feeling better", employee);
		assertNotNull(cancelRes.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.CANCELLED, cancelRes.getBody().getStatus());

		// Apply for a third leave to test rejection
		com.sonixhr.dto.leave.LeaveRequestDTO thirdRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.EMERGENCY)
				.startDate(startDate.plusDays(20))
				.endDate(startDate.plusDays(21))
				.reason("Personal emergency")
				.build();
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> thirdApply =
				employeeLeaveController.requestLeave(thirdRequest, employee);
		assertNotNull(thirdApply.getBody());
		Long thirdLeaveId = thirdApply.getBody().getId();

		// Authenticate as manager to reject
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						manager, "password", manager.getAuthorities()
				)
		);
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> rejectRes =
				leaveManagementController.rejectLeave(thirdLeaveId, "Insufficient documentation", manager);
		assertNotNull(rejectRes.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.REJECTED, rejectRes.getBody().getStatus());

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void testExtendedLeavePoliciesAndValidation() throws Exception {
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
				.name("QA")
				.code("QA")
				.isActive(true)
				.build();
		department = departmentRepository.save(department);

		// Create Manager
		com.sonixhr.entity.employee.Employee manager = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("MGR002")
				.firstName("Alice")
				.lastName("Manager")
				.email("alice.mgr@acme.com")
				.phone("9876543231")
				.position("QA Manager")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now().minusYears(1))
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.department(department)
				.roles(new java.util.HashSet<>(java.util.List.of(superAdminRole)))
				.build();
		manager = employeeRepository.save(manager);

		// Create Employee (Requester in PROBATION status, reporting to manager)
		com.sonixhr.entity.employee.Employee employee = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("EMP002")
				.firstName("Bob")
				.lastName("Tester")
				.email("bob.tester@acme.com")
				.phone("9876543232")
				.position("QA Engineer")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now())
				.status(com.sonixhr.enums.employee.EmployeeStatus.PROBATION)
				.isActive(true)
				.passwordHash("hashed")
				.department(department)
				.manager(manager)
				.roles(new java.util.HashSet<>(java.util.List.of(superAdminRole)))
				.build();
		employee = employeeRepository.save(employee);

		// 1. Get policies map via manager (admin role)
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						manager, "password", manager.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		org.springframework.http.ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> policiesRes =
				leaveManagementController.getLeavePolicies(manager);
		assertNotNull(policiesRes.getBody());
		assertTrue(policiesRes.getBody().containsKey("CASUAL"));

		// 2. Update CASUAL leave policy:
		//    - allowed = true
		//    - daysPerYear = 12
		//    - probationPeriodAllowed = false
		//    - prorated = true
		com.sonixhr.dto.leave.LeavePolicyDTO casualPolicyUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(12)
				.probationPeriodAllowed(false)
				.prorated(true)
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> updatedPoliciesRes =
				leaveManagementController.updateLeavePolicy("CASUAL", casualPolicyUpdate, manager);
		assertNotNull(updatedPoliciesRes.getBody());
		
		// 3. Authenticate as Bob (still in probation)
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						employee, "password", employee.getAuthorities()
				)
		);

		// Apply should fail due to probation check
		java.time.LocalDate startDate = java.time.LocalDate.now().plusDays(1);
		while (startDate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || startDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
			startDate = startDate.plusDays(1);
		}
		com.sonixhr.dto.leave.LeaveRequestDTO leaveRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(startDate)
				.endDate(startDate)
				.reason("Holiday")
				.build();

		try {
			employeeLeaveController.requestLeave(leaveRequest, employee);
			org.junit.jupiter.api.Assertions.fail("Should have failed validation for probation period");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("probation"));
		}

		// 4. Change Bob status to ACTIVE (remove probation barrier)
		employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE);
		employee = employeeRepository.save(employee);

		// Re-authenticate Bob
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						employee, "password", employee.getAuthorities()
				)
		);

		// 5. Re-authenticate Bob
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						employee, "password", employee.getAuthorities()
				)
		);

		// Get leave balance - CASUAL remaining should be prorated: 12 * (currentMonth / 12)
		org.springframework.http.ResponseEntity<java.util.Map<String, Object>> balanceResponse =
				employeeLeaveController.getLeaveBalance(employee);
		assertNotNull(balanceResponse.getBody());
		
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> casualBalance = (java.util.Map<String, Object>) balanceResponse.getBody().get("CASUAL");
		assertNotNull(casualBalance);
		
		double expectedProrated = 12.0 * ((double) java.time.LocalDate.now().getMonthValue() / 12.0);
		assertEquals(expectedProrated, (Double) casualBalance.get("total"), 0.001);

		// 6. Request leave exceeding expected prorated balance - should fail
		com.sonixhr.dto.leave.LeaveRequestDTO excessiveRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(startDate)
				.endDate(startDate.plusDays(10)) // Too many days
				.reason("Excessive leave")
				.build();

		try {
			employeeLeaveController.requestLeave(excessiveRequest, employee);
			org.junit.jupiter.api.Assertions.fail("Should have failed validation for insufficient prorated balance");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("Insufficient"));
		}

		// Request valid leave (1 day) - should succeed
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> requestRes =
				employeeLeaveController.requestLeave(leaveRequest, employee);
		assertNotNull(requestRes.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.PENDING, requestRes.getBody().getStatus());

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void testLeaveUpdateRequest() throws Exception {
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

		// Create Employee
		com.sonixhr.entity.employee.Employee employee = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("EMP003")
				.firstName("Jack")
				.lastName("Updater")
				.email("jack.updater@acme.com")
				.phone("9876543239")
				.position("Software Engineer")
				.employmentType(com.sonixhr.enums.employee.EmploymentType.FULL_TIME)
				.hireDate(java.time.LocalDate.now())
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.passwordHash("hashed")
				.roles(new java.util.HashSet<>(java.util.List.of(superAdminRole)))
				.build();
		employee = employeeRepository.save(employee);

		// Authenticate as Jack
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						employee, "password", employee.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		// 1. Request a leave (e.g. CASUAL leave for next week)
		java.time.LocalDate startDate = java.time.LocalDate.now().plusDays(1);
		while (startDate.getDayOfWeek() != java.time.DayOfWeek.THURSDAY) {
			startDate = startDate.plusDays(1);
		}
		java.time.LocalDate endDate = startDate.plusDays(1);

		com.sonixhr.dto.leave.LeaveRequestDTO leaveRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(startDate)
				.endDate(endDate)
				.reason("Initial personal reason")
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> applyResponse =
				employeeLeaveController.requestLeave(leaveRequest, employee);
		assertNotNull(applyResponse.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.PENDING, applyResponse.getBody().getStatus());
		Long leaveId = applyResponse.getBody().getId();

		// 2. Update the pending leave request (change reason and extend by one day)
		com.sonixhr.dto.leave.LeaveRequestDTO updateRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(startDate)
				.endDate(endDate.plusDays(1)) // Extend end date
				.reason("Updated personal reason")
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> updateResponse =
				employeeLeaveController.updateLeave(leaveId, updateRequest, employee);
		assertNotNull(updateResponse.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.PENDING, updateResponse.getBody().getStatus());
		assertEquals("Updated personal reason", updateResponse.getBody().getReason());
		// Total days should be updated (Saturday/Sunday/Monday -> 3 working days if weekends counted, or 2 working days if weekends excluded)
		double expectedDays = 2.0;
		if (Boolean.TRUE.equals(settings.getCountWeekendsAsLeave())) {
			expectedDays = 3.0;
		}
		assertEquals(expectedDays, updateResponse.getBody().getTotalDays());

		// 3. Trying to update to overlapping dates of another request should fail
		// Let's create a second leave request first (non-overlapping)
		com.sonixhr.dto.leave.LeaveRequestDTO secondRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(startDate.plusDays(10))
				.endDate(startDate.plusDays(11))
				.reason("Second leave")
				.build();
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> secondResponse =
				employeeLeaveController.requestLeave(secondRequest, employee);
		assertNotNull(secondResponse.getBody());
		Long secondLeaveId = secondResponse.getBody().getId();

		// Try to update the first leave request to overlap with the second one
		com.sonixhr.dto.leave.LeaveRequestDTO overlappingUpdateRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(startDate.plusDays(9)) // overlap with second request which is at plusDays(10)
				.endDate(startDate.plusDays(11))
				.reason("Should fail due to overlap")
				.build();

		try {
			employeeLeaveController.updateLeave(leaveId, overlappingUpdateRequest, employee);
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException due to overlap");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("pending or approved leave"));
		}

		// 4. Try to update a cancelled leave request - should fail
		employeeLeaveController.cancelLeave(secondLeaveId, "Cancel second", employee);
		try {
			employeeLeaveController.updateLeave(secondLeaveId, updateRequest, employee);
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException for updating non-pending leave");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("pending"));
		}

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void testExplicitLeaveTypeUpdateApis() throws Exception {
		// Clean database
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Seed default tenant
		tenantSeeder.run(null);

		com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findAll().get(0);
		com.sonixhr.entity.employee.Employee manager = employeeRepository.findByEmailWithRolesAndPermissions("admin@acme.com")
				.orElseThrow(() -> new AssertionError("Super Admin employee not found"));

		// Simulate authenticated context for manager
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						manager, "Admin@123", manager.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		// Test CASUAL Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO casualUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(14)
				.carryForward(true)
				.maxCarryForwardDays(5)
				.genderEligibility("ALL")
				.build();
		
		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> casualRes =
				leaveManagementController.updateCasualPolicy(casualUpdate, manager);
		assertNotNull(casualRes.getBody());
		assertEquals(14, casualRes.getBody().getDaysPerYear());

		// Test SICK Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO sickUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(10)
				.carryForward(false)
				.genderEligibility("ALL")
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> sickRes =
				leaveManagementController.updateSickPolicy(sickUpdate, manager);
		assertNotNull(sickRes.getBody());
		assertEquals(10, sickRes.getBody().getDaysPerYear());

		// Test EARNED Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO earnedUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(18)
				.carryForward(true)
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> earnedRes =
				leaveManagementController.updateEarnedPolicy(earnedUpdate, manager);
		assertNotNull(earnedRes.getBody());
		assertEquals(18, earnedRes.getBody().getDaysPerYear());

		// Test EMERGENCY Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO emergencyUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(4)
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> emergencyRes =
				leaveManagementController.updateEmergencyPolicy(emergencyUpdate, manager);
		assertNotNull(emergencyRes.getBody());
		assertEquals(4, emergencyRes.getBody().getDaysPerYear());

		// Test MATERNITY Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO maternityUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(90)
				.genderEligibility("FEMALE")
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> maternityRes =
				leaveManagementController.updateMaternityPolicy(maternityUpdate, manager);
		assertNotNull(maternityRes.getBody());
		assertEquals(90, maternityRes.getBody().getDaysPerYear());

		// Test PATERNITY Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO paternityUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(6)
				.genderEligibility("MALE")
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> paternityRes =
				leaveManagementController.updatePaternityPolicy(paternityUpdate, manager);
		assertNotNull(paternityRes.getBody());
		assertEquals(6, paternityRes.getBody().getDaysPerYear());

		// Test UNPAID Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO unpaidUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(5)
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> unpaidRes =
				leaveManagementController.updateUnpaidPolicy(unpaidUpdate, manager);
		assertNotNull(unpaidRes.getBody());
		assertEquals(5, unpaidRes.getBody().getDaysPerYear());

		// Test COMPENSATORY Policy update
		com.sonixhr.dto.leave.LeavePolicyDTO compensatoryUpdate = com.sonixhr.dto.leave.LeavePolicyDTO.builder()
				.allowed(true)
				.daysPerYear(8)
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> compensatoryRes =
				leaveManagementController.updateCompensatoryPolicy(compensatoryUpdate, manager);
		assertNotNull(compensatoryRes.getBody());
		assertEquals(8, compensatoryRes.getBody().getDaysPerYear());

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void testAttendanceStatusValidation() throws Exception {
		// Clean database
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Seed default tenant
		tenantSeeder.run(null);

		com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findAll().get(0);
		com.sonixhr.entity.employee.Employee manager = employeeRepository.findByEmailWithRolesAndPermissions("admin@acme.com")
				.orElseThrow(() -> new AssertionError("Manager employee not found"));

		// Create a target employee
		com.sonixhr.entity.employee.Employee targetEmployee = com.sonixhr.entity.employee.Employee.builder()
				.tenant(tenant)
				.employeeCode("EMP009")
				.email("target@acme.com")
				.passwordHash("hashed")
				.firstName("Target")
				.lastName("User")
				.hireDate(java.time.LocalDate.now().minusDays(10))
				.status(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE)
				.isActive(true)
				.manager(manager)
				.roles(new java.util.HashSet<>(tenantRoleRepository.findAll()))
				.build();
		
		targetEmployee = employeeRepository.save(targetEmployee);

		// Simulate authenticated context for manager
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						manager, "Admin@123", manager.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		// 1. Mark attendance for ACTIVE employee should succeed
		com.sonixhr.dto.attendance.ManualAttendanceMarkRequest request = new com.sonixhr.dto.attendance.ManualAttendanceMarkRequest();
		request.setEmployeeId(targetEmployee.getId());
		request.setAttendanceDate(java.time.LocalDate.now());
		request.setStatus(com.sonixhr.enums.attendance.AttendanceStatus.PRESENT);
		request.setReason("Regular Day");

		org.springframework.http.ResponseEntity<com.sonixhr.dto.attendance.ManualAttendanceRecordResponse> response =
				manualAttendanceController.markAttendance(request, manager);
		assertNotNull(response.getBody());
		assertEquals(com.sonixhr.enums.attendance.AttendanceStatus.PRESENT, response.getBody().getStatus());

		// 2. Set employee status to RESIGNED with future lastWorkingDate (notice period / pending resignation) - should succeed
		targetEmployee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.RESIGNED);
		targetEmployee.setLastWorkingDate(java.time.LocalDate.now().plusDays(2));
		targetEmployee = employeeRepository.save(targetEmployee);

		org.springframework.http.ResponseEntity<com.sonixhr.dto.attendance.ManualAttendanceRecordResponse> resignedResponse =
				manualAttendanceController.markAttendance(request, manager);
		assertNotNull(resignedResponse.getBody());
		assertEquals(com.sonixhr.enums.attendance.AttendanceStatus.PRESENT, resignedResponse.getBody().getStatus());

		// Now set lastWorkingDate to in the past - should fail
		targetEmployee.setLastWorkingDate(java.time.LocalDate.now().minusDays(1));
		employeeRepository.save(targetEmployee);

		try {
			manualAttendanceController.markAttendance(request, manager);
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException for resigned employee after notice period");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("Cannot mark attendance for an employee with status") || ex.getMessage().contains("last working date"));
		}

		// 3. Set employee status to TERMINATED and mark attendance should fail
		targetEmployee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.TERMINATED);
		targetEmployee.setLastWorkingDate(null);
		employeeRepository.save(targetEmployee);

		try {
			manualAttendanceController.markAttendance(request, manager);
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException for terminated employee");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("Cannot mark attendance for an employee with status"));
		}

		// 4. Set employee status to SUSPENDED and mark attendance should fail
		targetEmployee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.SUSPENDED);
		employeeRepository.save(targetEmployee);

		try {
			manualAttendanceController.markAttendance(request, manager);
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException for suspended employee");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("Cannot mark attendance for an employee with status"));
		}

		// 5. Set employee status to INVITED and mark attendance should fail
		targetEmployee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.INVITED);
		employeeRepository.save(targetEmployee);

		try {
			manualAttendanceController.markAttendance(request, manager);
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException for invited employee");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("Cannot mark attendance for an employee with status"));
		}

		// 6. Set employee status to ON_LEAVE and mark attendance status to PRESENT should fail
		targetEmployee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ON_LEAVE);
		employeeRepository.save(targetEmployee);

		try {
			manualAttendanceController.markAttendance(request, manager); // request status is PRESENT
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException for marking ON_LEAVE employee as PRESENT");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("because their employee status is ON LEAVE"));
		}

		// 7. Set employee status to ON_LEAVE and mark attendance status to ON_LEAVE should succeed
		com.sonixhr.dto.attendance.ManualAttendanceMarkRequest onLeaveRequest = new com.sonixhr.dto.attendance.ManualAttendanceMarkRequest();
		onLeaveRequest.setEmployeeId(targetEmployee.getId());
		onLeaveRequest.setAttendanceDate(java.time.LocalDate.now());
		onLeaveRequest.setStatus(com.sonixhr.enums.attendance.AttendanceStatus.ON_LEAVE);
		onLeaveRequest.setReason("On Annual Leave");

		org.springframework.http.ResponseEntity<com.sonixhr.dto.attendance.ManualAttendanceRecordResponse> onLeaveResponse =
				manualAttendanceController.markAttendance(onLeaveRequest, manager);
		assertNotNull(onLeaveResponse.getBody());
		assertEquals(com.sonixhr.enums.attendance.AttendanceStatus.ON_LEAVE, onLeaveResponse.getBody().getStatus());

		// 8. Add overtime for ON_LEAVE employee should fail
		com.sonixhr.dto.attendance.ManualOvertimeRequest overtimeRequest = new com.sonixhr.dto.attendance.ManualOvertimeRequest();
		overtimeRequest.setEmployeeId(targetEmployee.getId());
		overtimeRequest.setDate(java.time.LocalDate.now());
		overtimeRequest.setOvertimeHours(2.0);
		overtimeRequest.setReason("Late support");

		try {
			manualAttendanceController.addOvertime(overtimeRequest, manager);
			org.junit.jupiter.api.Assertions.fail("Should have thrown BusinessException for adding overtime to ON_LEAVE employee");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("Cannot add overtime for an employee who is currently ON LEAVE"));
		}

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	@org.springframework.transaction.annotation.Transactional
	void testHalfDayLeaveRequest() throws Exception {
		// Clean database
		new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.execute("TRUNCATE TABLE employees, tenant_subscriptions, shift_configurations, tenant_roles, tenants CASCADE");
		});

		// Seed default tenant
		tenantSeeder.run(null);

		com.sonixhr.entity.tenant.Tenant tenant = tenantRepository.findAll().get(0);

		// Set up Tenant Leave Settings (Ensure policiesConfigured is true and approval is not required)
		com.sonixhr.entity.leave.TenantLeaveSettings settings = leaveConfigService.getTenantSettings(tenant.getId());
		settings.setPoliciesConfigured(true);
		settings.setLeaveApprovalRequired(false);
		tenantLeaveSettingsRepository.save(settings);

		com.sonixhr.entity.employee.Employee employee = employeeRepository.findByEmailWithRolesAndPermissions("admin@acme.com")
				.orElseThrow(() -> new AssertionError("Super Admin employee not found"));

		// Simulate authenticated context for employee
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						employee, "password", employee.getAuthorities()
				)
		);
		com.sonixhr.security.TenantContext.setCurrentTenant(tenant.getId());

		// 1. Apply for a half-day leave spanning single day (should succeed)
		java.time.LocalDate leaveDate = java.time.LocalDate.now().plusDays(1);
		while (leaveDate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || leaveDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
			leaveDate = leaveDate.plusDays(1);
		}

		com.sonixhr.dto.leave.LeaveRequestDTO halfDayRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(leaveDate)
				.endDate(leaveDate)
				.isHalfDay(true)
				.reason("Doctor appointment half day")
				.build();

		org.springframework.http.ResponseEntity<com.sonixhr.dto.leave.LeaveResponseDTO> response =
				employeeLeaveController.requestLeave(halfDayRequest, employee);
		assertNotNull(response.getBody());
		assertEquals(com.sonixhr.enums.leave.LeaveStatus.APPROVED, response.getBody().getStatus()); // Auto-approved because requester is manager/admin
		assertEquals(0.5, response.getBody().getTotalDays());
		assertTrue(response.getBody().getIsHalfDay());

		// Assert attendance status is HALF_DAY in database
		String attendanceStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM attendance_records WHERE employee_id = ? AND attendance_date = ?",
				String.class, employee.getId(), java.sql.Date.valueOf(leaveDate)
		);
		assertEquals("HALF_DAY", attendanceStatus);

		// 2. Apply for a half-day leave spanning multiple days (should fail)
		com.sonixhr.dto.leave.LeaveRequestDTO invalidHalfDayRequest = com.sonixhr.dto.leave.LeaveRequestDTO.builder()
				.leaveType(com.sonixhr.enums.leave.LeaveType.CASUAL)
				.startDate(leaveDate)
				.endDate(leaveDate.plusDays(1))
				.isHalfDay(true)
				.reason("Invalid multiple days")
				.build();

		try {
			employeeLeaveController.requestLeave(invalidHalfDayRequest, employee);
			org.junit.jupiter.api.Assertions.fail("Should have failed for multiple days half-day request");
		} catch (com.sonixhr.exceptions.BusinessException ex) {
			assertTrue(ex.getMessage().contains("Half-day leaves can only be requested for a single day"));
		}

		// Clean context
		com.sonixhr.security.TenantContext.clear();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}
}

