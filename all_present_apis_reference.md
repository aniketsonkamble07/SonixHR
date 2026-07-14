# Exhaustive SonixHR API & Payload Reference

- **Production Base URL**: `https://sonixhr.onrender.com`
- **Local Base URL**: `http://localhost:8081`

This document lists all API endpoints across all controllers, including mapped request/response DTO structures.

## AdminTenantController

### POST `/api/admin/tenants/{id}/restore`
- **Handler Method**: `restoreTenant`
- **Description**: No description provided
- **Request Body Type**: `TenantRestoreRequest`
#### Request JSON Example:
```json
{
  "planId": 0,
  "notes": ""
}
```
- **Response Type**: `TenantSubscriptionResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "planType": "",
  "planName": "",
  "planStatus": "PlanStatus",
  "maxEmployees": 0,
  "maxStorageMb": 0,
  "startedAt": "2026-06-17",
  "endsAt": "2026-06-17",
  "amount": 0,
  "currency": "",
  "isActive": false,
  "createdAt": "2026-06-17",
  "billingPeriodStart": "2026-06-17",
  "billingPeriodEnd": "2026-06-17",
  "gracePeriodEnd": "2026-06-17",
  "cancellationReason": "",
  "cancelledAtEndOfPeriod": false
}
```

---

### GET `/api/admin/tenants/{id}/restore-history`
- **Handler Method**: `getRestoreHistory`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `RestoreHistoryResponseDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "oldValue": "",
  "newValue": "",
  "createdAt": "2026-06-17",
  "performedByEmail": "",
  "notes": "",
  "planName": ""
}
]
```

---

## CalendarController

### GET `/api/calendar/employee/{employeeId}`
- **Handler Method**: `getEmployeeCalendar`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `CalendarMonthDTO`
#### Response JSON Example:
```json
{
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "year": 0,
  "month": 0,
  "monthName": "",
  "monthDisplayName": "",
  "days": [
  {
    "date": "2026-06-17",
    "dayOfMonth": 0,
    "dayOfWeek": "",
    "dayOfWeekValue": 0,
    "type": "CalendarDayType",
    "status": "CalendarAttendanceStatus",
    "displayName": "",
    "color": "",
    "description": "",
    "overtimeHours": 0,
    "leaveType": "",
    "isWeekend": false,
    "isPast": false,
    "isToday": false
  }
  ],
  "summary": { "key": "value" }
}
```

---

### GET `/api/calendar/my`
- **Handler Method**: `getMyCalendar`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `CalendarMonthDTO`
#### Response JSON Example:
```json
{
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "year": 0,
  "month": 0,
  "monthName": "",
  "monthDisplayName": "",
  "days": [
  {
    "date": "2026-06-17",
    "dayOfMonth": 0,
    "dayOfWeek": "",
    "dayOfWeekValue": 0,
    "type": "CalendarDayType",
    "status": "CalendarAttendanceStatus",
    "displayName": "",
    "color": "",
    "description": "",
    "overtimeHours": 0,
    "leaveType": "",
    "isWeekend": false,
    "isPast": false,
    "isToday": false
  }
  ],
  "summary": { "key": "value" }
}
```

---

## DataExportController

### GET `/`
- **Handler Method**: `exportEmployees`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `byte[]`
#### Response JSON Example:
```json
0
```

---

### GET `/api/export/payroll`
- **Handler Method**: `exportPayroll`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `byte[]`
#### Response JSON Example:
```json
0
```

---

## DebugController

### GET `/api/debug/all-users`
- **Handler Method**: `getAllPlatformUsers`
- **Description**: Debug endpoint to check all platform users
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ResponseEntity<?>`

---

### GET `/api/debug/auth-check`
- **Handler Method**: `checkAuth`
- **Description**: Legacy endpoint (kept for backward compatibility)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/debug/check-database`
- **Handler Method**: `checkDatabase`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ResponseEntity<?>`

---

### GET `/api/debug/employee-auth`
- **Handler Method**: `checkEmployeeAuth`
- **Description**: Debug endpoint for Employee (tenant user)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/debug/platform-auth`
- **Handler Method**: `checkPlatformAuth`
- **Description**: Debug endpoint for Platform User (admin)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/debug/run-payrun`
- **Handler Method**: `runPayrunDebug`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ResponseEntity<?>`

---

### GET `/api/debug/test-auth-flow`
- **Handler Method**: `testAuthFlow`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ResponseEntity<?>`

---

### GET `/api/debug/test-password`
- **Handler Method**: `testPassword`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ResponseEntity<?>`

---

## DepartmentController

### GET `/api/tenant/departments`
- **Handler Method**: `getAllDepartments`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<DepartmentResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### POST `/api/tenant/departments`
- **Handler Method**: `createDepartment`
- **Description**: No description provided
- **Request Body Type**: `DepartmentRequest`
#### Request JSON Example:
```json
{
  "name": "",
  "code": "",
  "description": ""
}
```
- **Response Type**: `DepartmentResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### GET `/api/tenant/departments/bulk-stats`
- **Handler Method**: `getAllDepartmentsWithBulkCounts`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `DepartmentResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/tenant/departments/dashboard`
- **Handler Method**: `getDepartmentDashboard`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/tenant/departments/list`
- **Handler Method**: `getAllDepartmentsList`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `DepartmentResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/tenant/departments/lookup`
- **Handler Method**: `getDepartmentLookup`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `DepartmentLookupResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "name": "",
  "code": ""
}
]
```

---

### GET `/api/tenant/departments/search`
- **Handler Method**: `searchDepartments`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<DepartmentResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### DELETE `/api/tenant/departments/{id}`
- **Handler Method**: `deleteDepartment`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/tenant/departments/{id}`
- **Handler Method**: `getDepartmentById`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `DepartmentResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### PUT `/api/tenant/departments/{id}`
- **Handler Method**: `updateDepartment`
- **Description**: No description provided
- **Request Body Type**: `DepartmentRequest`
#### Request JSON Example:
```json
{
  "name": "",
  "code": "",
  "description": ""
}
```
- **Response Type**: `DepartmentResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### GET `/api/tenant/departments/{id}/employee-count/active`
- **Handler Method**: `getActiveEmployeeCount`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Long`
#### Response JSON Example:
```json
0
```

---

### GET `/api/tenant/departments/{id}/employee-count/probation`
- **Handler Method**: `getOnProbationCount`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Long`
#### Response JSON Example:
```json
0
```

---

### GET `/api/tenant/departments/{id}/employee-count/total`
- **Handler Method**: `getTotalEmployeeCount`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Long`
#### Response JSON Example:
```json
0
```

---

### GET `/api/tenant/departments/{id}/stats`
- **Handler Method**: `getDepartmentWithStats`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `DepartmentResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "name": "",
  "code": "",
  "description": "",
  "totalEmployees": 0,
  "activeEmployees": 0,
  "onProbationEmployees": 0,
  "onLeaveEmployees": 0,
  "resignedEmployees": 0,
  "activePercentage": 0,
  "probationPercentage": 0,
  "managerId": 0,
  "managerName": "",
  "managerEmail": "",
  "annualBudget": 0,
  "actualSpent": 0,
  "remainingBudget": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

## EmployeeActivationController

### POST `/api/employee/auth/activate`
- **Handler Method**: `activateEmployee`
- **Description**: No description provided
- **Request Body Type**: `ActivationRequest`
#### Request JSON Example:
```json
{
  "token": "",
  "password": "",
  "confirmPassword": ""
}
```
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

## EmployeeController

### GET `/api/employees`
- **Handler Method**: `getAllEmployees`
- **Description**: Get all employees
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<EmployeeSummaryResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### POST `/api/employees`
- **Handler Method**: `createEmployee`
- **Description**: Create a new employee
- **Request Body Type**: `EmployeeCreateRequest`
#### Request JSON Example:
```json
{
  "firstName": "",
  "lastName": "",
  "email": "",
  "departmentId": 0,
  "position": "",
  "hireDate": "2026-06-17",
  "phone": "",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "managerId": 0,
  "managerCode": "",
  "employmentType": "EmploymentType",
  "roleIds": 0,
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "salary": 0,
  "salaryType": "SalaryType",
  "currency": "",
  "taxRegime": "",
  "shiftId": 0,
  "bankDetails": {
    "accountHolderName": "",
    "accountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false
  }
}
```
- **Response Type**: `EmployeeCreateResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "departmentName": "",
  "departmentCode": "",
  "position": "",
  "status": "EmployeeStatus",
  "hireDate": "2026-06-17",
  "message": ""
}
```

---

### GET `/api/employees/code/{employeeCode}`
- **Handler Method**: `getEmployeeByCode`
- **Description**: Get employee by code
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

### GET `/api/employees/department/name/{departmentName}`
- **Handler Method**: `getEmployeesByDepartmentName`
- **Description**: Get employees by department name
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### GET `/api/employees/dropdown`
- **Handler Method**: `getActiveEmployeesForDropdown`
- **Description**: Get active employees for dropdown
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeDropdownDTO[]`
#### Response JSON Example:
```json
[
{
  "employeeCode": "",
  "fullName": ""
}
]
```

---

### GET `/api/employees/email/{email}`
- **Handler Method**: `getEmployeeByEmail`
- **Description**: Get employee by email
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

### GET `/api/employees/managers/{managerId}/team`
- **Handler Method**: `getTeamMembers`
- **Description**: Get team members
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<EmployeeSummaryResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### GET `/api/employees/me`
- **Handler Method**: `getCurrentEmployee`
- **Description**: Get current employee
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

### GET `/api/employees/my-team`
- **Handler Method**: `getMyTeam`
- **Description**: Get my team
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<EmployeeSummaryResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### GET `/api/employees/organization-chart`
- **Handler Method**: `getOrganizationChart`
- **Description**: Get organization chart
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### GET `/api/employees/search`
- **Handler Method**: `searchEmployees`
- **Description**: Search employees
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<EmployeeSummaryResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### GET `/api/employees/statistics/departments`
- **Handler Method**: `getDepartmentStatistics`
- **Description**: Get department statistics
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `DepartmentStat[]`
#### Response JSON Example:
```json
[
{
  "department": "",
  "count": 0
}
]
```

---

### GET `/api/employees/status/{status}`
- **Handler Method**: `getEmployeesByStatus`
- **Description**: Get employees by status
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<EmployeeSummaryResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### GET `/api/employees/upcoming/anniversaries`
- **Handler Method**: `getUpcomingAnniversaries`
- **Description**: Get upcoming work anniversaries
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### GET `/api/employees/upcoming/birthdays`
- **Handler Method**: `getUpcomingBirthdays`
- **Description**: Get upcoming birthdays
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### DELETE `/api/employees/{id}`
- **Handler Method**: `deleteEmployee`
- **Description**: Delete employee
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/employees/{id}`
- **Handler Method**: `getEmployeeById`
- **Description**: Get employee by ID
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

### PUT `/api/employees/{id}`
- **Handler Method**: `updateEmployee`
- **Description**: Update employee
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

### POST `/api/employees/{id}/confirm`
- **Handler Method**: `confirmEmployee`
- **Description**: Confirm employee
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### PATCH `/api/employees/{id}/status`
- **Handler Method**: `updateEmployeeStatus`
- **Description**: Update employee status
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/employees/{managerId}/subordinates`
- **Handler Method**: `manager`
- **Description**: Get all subordinates
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `description = "Retrieves all employees under a`
#### Response JSON Example:
```json
"description = "Retrieves all employees under a"
```

---

## EmployeeLeaveController

### GET `/api/employee/leaves`
- **Handler Method**: `getMyLeaves`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `LeaveResponseDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "leaveType": "LeaveType",
  "leaveTypeDisplay": "",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalDays": 0,
  "reason": "",
  "status": "LeaveStatus",
  "statusDisplay": "",
  "rejectionReason": "",
  "approvedBy": 0,
  "approvedByName": "",
  "approvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "isHalfDay": false
}
]
```

---

### POST `/api/employee/leaves`
- **Handler Method**: `requestLeave`
- **Description**: No description provided
- **Request Body Type**: `LeaveRequestDTO`
#### Request JSON Example:
```json
{
  "leaveType": "LeaveType",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "reason": "",
  "isHalfDay": false
}
```
- **Response Type**: `LeaveResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "leaveType": "LeaveType",
  "leaveTypeDisplay": "",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalDays": 0,
  "reason": "",
  "status": "LeaveStatus",
  "statusDisplay": "",
  "rejectionReason": "",
  "approvedBy": 0,
  "approvedByName": "",
  "approvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "isHalfDay": false
}
```

---

### GET `/api/employee/leaves/balance`
- **Handler Method**: `getLeaveBalance`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### PUT `/api/employee/leaves/{id}`
- **Handler Method**: `updateLeave`
- **Description**: No description provided
- **Request Body Type**: `LeaveRequestDTO`
#### Request JSON Example:
```json
{
  "leaveType": "LeaveType",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "reason": "",
  "isHalfDay": false
}
```
- **Response Type**: `LeaveResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "leaveType": "LeaveType",
  "leaveTypeDisplay": "",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalDays": 0,
  "reason": "",
  "status": "LeaveStatus",
  "statusDisplay": "",
  "rejectionReason": "",
  "approvedBy": 0,
  "approvedByName": "",
  "approvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "isHalfDay": false
}
```

---

### PUT `/api/employee/leaves/{id}/cancel`
- **Handler Method**: `cancelLeave`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `LeaveResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "leaveType": "LeaveType",
  "leaveTypeDisplay": "",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalDays": 0,
  "reason": "",
  "status": "LeaveStatus",
  "statusDisplay": "",
  "rejectionReason": "",
  "approvedBy": 0,
  "approvedByName": "",
  "approvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "isHalfDay": false
}
```

---

## EmployeeSelfServiceController

### GET `/api/employee/organization-chart`
- **Handler Method**: `getMyOrgChart`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `MyOrgChartResponse`
#### Response JSON Example:
```json
{
  "employee": {
    "id": 0,
    "employeeCode": "",
    "firstName": "",
    "lastName": "",
    "fullName": "",
    "email": "",
    "phone": "",
    "position": "",
    "departmentName": "",
    "status": "EmployeeStatus",
    "isActive": false,
    "profilePictureUrl": "",
    "hireDate": "2026-06-17",
    "managerName": "",
    "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
  },
  "managerChain": [
  {
    "id": 0,
    "employeeCode": "",
    "firstName": "",
    "lastName": "",
    "fullName": "",
    "email": "",
    "phone": "",
    "position": "",
    "departmentName": "",
    "status": "EmployeeStatus",
    "isActive": false,
    "profilePictureUrl": "",
    "hireDate": "2026-06-17",
    "managerName": "",
    "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
  }
  ],
  "peers": [
  {
    "id": 0,
    "employeeCode": "",
    "firstName": "",
    "lastName": "",
    "fullName": "",
    "email": "",
    "phone": "",
    "position": "",
    "departmentName": "",
    "status": "EmployeeStatus",
    "isActive": false,
    "profilePictureUrl": "",
    "hireDate": "2026-06-17",
    "managerName": "",
    "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
  }
  ],
  "directReports": [
  {
    "id": 0,
    "employeeCode": "",
    "firstName": "",
    "lastName": "",
    "fullName": "",
    "email": "",
    "phone": "",
    "position": "",
    "departmentName": "",
    "status": "EmployeeStatus",
    "isActive": false,
    "profilePictureUrl": "",
    "hireDate": "2026-06-17",
    "managerName": "",
    "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
  }
  ]
}
```

---

### GET `/api/employee/profile`
- **Handler Method**: `getMyProfile`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

### PUT `/api/employee/profile`
- **Handler Method**: `updateMyProfile`
- **Description**: No description provided
- **Request Body Type**: `EmployeeProfileUpdateRequest`
#### Request JSON Example:
```json
{
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": ""
}
```
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

## EmployeeTaskController

### POST `/api/employees/tasks`
- **Handler Method**: `createTask`
- **Description**: No description provided
- **Request Body Type**: `TaskCreateRequestDTO`
#### Request JSON Example:
```json
{
  "title": "",
  "description": "",
  "assignedToId": 0,
  "dueDate": "2026-06-17"
}
```
- **Response Type**: `TaskResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "title": "",
  "description": "",
  "assignedToId": 0,
  "assignedToName": "",
  "assignedById": 0,
  "assignedByName": "",
  "dueDate": "2026-06-17",
  "status": "TaskStatus",
  "acknowledgedAt": "2026-06-17",
  "acceptedAt": "2026-06-17",
  "completedAt": "2026-06-17",
  "declineReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### GET `/api/employees/tasks/all`
- **Handler Method**: `getAllTasks`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<TaskResponseDTO>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "title": "",
  "description": "",
  "assignedToId": 0,
  "assignedToName": "",
  "assignedById": 0,
  "assignedByName": "",
  "dueDate": "2026-06-17",
  "status": "TaskStatus",
  "acknowledgedAt": "2026-06-17",
  "acceptedAt": "2026-06-17",
  "completedAt": "2026-06-17",
  "declineReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/employees/tasks/assignees`
- **Handler Method**: `getAssignableEmployees`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TaskAssigneeDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "fullName": "",
  "employeeCode": "",
  "departmentName": "",
  "designation": ""
}
]
```

---

### GET `/api/employees/tasks/my`
- **Handler Method**: `getMyTasks`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<TaskResponseDTO>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "title": "",
  "description": "",
  "assignedToId": 0,
  "assignedToName": "",
  "assignedById": 0,
  "assignedByName": "",
  "dueDate": "2026-06-17",
  "status": "TaskStatus",
  "acknowledgedAt": "2026-06-17",
  "acceptedAt": "2026-06-17",
  "completedAt": "2026-06-17",
  "declineReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### PUT `/api/employees/tasks/{id}/accept`
- **Handler Method**: `acceptTask`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TaskResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "title": "",
  "description": "",
  "assignedToId": 0,
  "assignedToName": "",
  "assignedById": 0,
  "assignedByName": "",
  "dueDate": "2026-06-17",
  "status": "TaskStatus",
  "acknowledgedAt": "2026-06-17",
  "acceptedAt": "2026-06-17",
  "completedAt": "2026-06-17",
  "declineReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### PUT `/api/employees/tasks/{id}/acknowledge`
- **Handler Method**: `acknowledgeTask`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TaskResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "title": "",
  "description": "",
  "assignedToId": 0,
  "assignedToName": "",
  "assignedById": 0,
  "assignedByName": "",
  "dueDate": "2026-06-17",
  "status": "TaskStatus",
  "acknowledgedAt": "2026-06-17",
  "acceptedAt": "2026-06-17",
  "completedAt": "2026-06-17",
  "declineReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### PUT `/api/employees/tasks/{id}/decline`
- **Handler Method**: `declineTask`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TaskResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "title": "",
  "description": "",
  "assignedToId": 0,
  "assignedToName": "",
  "assignedById": 0,
  "assignedByName": "",
  "dueDate": "2026-06-17",
  "status": "TaskStatus",
  "acknowledgedAt": "2026-06-17",
  "acceptedAt": "2026-06-17",
  "completedAt": "2026-06-17",
  "declineReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### PUT `/api/employees/tasks/{id}/status`
- **Handler Method**: `updateTaskStatus`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TaskResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "title": "",
  "description": "",
  "assignedToId": 0,
  "assignedToName": "",
  "assignedById": 0,
  "assignedByName": "",
  "dueDate": "2026-06-17",
  "status": "TaskStatus",
  "acknowledgedAt": "2026-06-17",
  "acceptedAt": "2026-06-17",
  "completedAt": "2026-06-17",
  "declineReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

## FnfController

### POST `/api/payroll/fnf/process`
- **Handler Method**: `processFnf`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `FnfSettlement`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenant": {
    "id": 0,
    "tenantCode": "",
    "companyName": "",
    "status": "UserStatus",
    "isActive": false,
    "adminEmail": "",
    "adminName": "",
    "adminPhone": "",
    "officeAddress": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "subscriptionPlan": {
      "id": 0,
      "name": "",
      "code": "",
      "description": "",
      "price": 0,
      "validityMonths": 0,
      "currency": "",
      "maxUsers": 0,
      "maxEmployees": 0,
      "features": "",
      "isCustom": false,
      "isActive": false,
      "isPublic": false,
      "displayOrder": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "isActive": "",
      "deletedAt": "",
      "isActive": "",
      "false": "return",
      "ZERO": "",
      "price": "",
      "validityMonths": "",
      "isActive": "",
      "isPublic": "",
      "currency": "",
      "displayOrder": "",
      "isCustom": "",
      "price": "",
      "isActive": ""
    },
    "maxEmployees": 0,
    "planStatus": "PlanStatus",
    "endsAt": "2026-06-17",
    "suspendedAt": "2026-06-17",
    "suspensionReason": "",
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "deletedAt": "2026-06-17",
    "expiredAt": "2026-06-17",
    "archivedAt": "2026-06-17",
    "archiveWarningNotifiedAt": "2026-06-17",
    "finalReminderSentAt": "2026-06-17",
    "expirationNotifiedAt": "2026-06-17",
    "dataStatus": "TenantDataStatus",
    "deletedByAdminId": 0,
    "createdBy": 0,
    "updatedBy": 0,
    "version": 0,
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "planStatus": "",
    "suspensionReason": "",
    "dataStatus": "",
    "archivedAt": "",
    "expiredAt": "",
    "archivedAt": "",
    "archiveWarningNotifiedAt": "",
    "finalReminderSentAt": "",
    "expirationNotifiedAt": "",
    "dataStatus": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "deletedAt": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "status": "",
    "0": "return",
    "isActive": "return",
    "true": "return",
    "false": "return"
  },
  "employee": {
    "id": 0,
    "tenant": {
      "id": 0,
      "tenantCode": "",
      "companyName": "",
      "status": "UserStatus",
      "isActive": false,
      "adminEmail": "",
      "adminName": "",
      "adminPhone": "",
      "officeAddress": "",
      "city": "",
      "state": "IndianState",
      "stateText": "",
      "country": "",
      "subscriptionPlan": {
        "id": 0,
        "name": "",
        "code": "",
        "description": "",
        "price": 0,
        "validityMonths": 0,
        "currency": "",
        "maxUsers": 0,
        "maxEmployees": 0,
        "features": "",
        "isCustom": false,
        "isActive": false,
        "isPublic": false,
        "displayOrder": 0,
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "isActive": "",
        "deletedAt": "",
        "isActive": "",
        "false": "return",
        "ZERO": "",
        "price": "",
        "validityMonths": "",
        "isActive": "",
        "isPublic": "",
        "currency": "",
        "displayOrder": "",
        "isCustom": "",
        "price": "",
        "isActive": ""
      },
      "maxEmployees": 0,
      "planStatus": "PlanStatus",
      "endsAt": "2026-06-17",
      "suspendedAt": "2026-06-17",
      "suspensionReason": "",
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "expiredAt": "2026-06-17",
      "archivedAt": "2026-06-17",
      "archiveWarningNotifiedAt": "2026-06-17",
      "finalReminderSentAt": "2026-06-17",
      "expirationNotifiedAt": "2026-06-17",
      "dataStatus": "TenantDataStatus",
      "deletedByAdminId": 0,
      "createdBy": 0,
      "updatedBy": 0,
      "version": 0,
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "planStatus": "",
      "suspensionReason": "",
      "dataStatus": "",
      "archivedAt": "",
      "expiredAt": "",
      "archivedAt": "",
      "archiveWarningNotifiedAt": "",
      "finalReminderSentAt": "",
      "expirationNotifiedAt": "",
      "dataStatus": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "deletedAt": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "status": "",
      "0": "return",
      "isActive": "return",
      "true": "return",
      "false": "return"
    },
    "tenantId": 0,
    "employeeCode": "",
    "email": "",
    "passwordHash": "",
    "isActive": false,
    "rolesVersion": 0,
    "cachedAuthorities": [ "? extends GrantedAuthority" ],
    "cachedRolesVersion": 0,
    "lastLoginAt": "2026-06-17",
    "lastLoginIp": "",
    "mustChangePassword": false,
    "firstName": "",
    "lastName": "",
    "phone": "",
    "dateOfBirth": "2026-06-17",
    "gender": "Gender",
    "maritalStatus": "MaritalStatus",
    "bloodGroup": "BloodGroup",
    "nationality": "",
    "personalEmail": "",
    "department": {
      "id": 0,
      "tenant": {
        "id": 0,
        "tenantCode": "",
        "companyName": "",
        "status": "UserStatus",
        "isActive": false,
        "adminEmail": "",
        "adminName": "",
        "adminPhone": "",
        "officeAddress": "",
        "city": "",
        "state": "IndianState",
        "stateText": "",
        "country": "",
        "subscriptionPlan": {
          "id": 0,
          "name": "",
          "code": "",
          "description": "",
          "price": 0,
          "validityMonths": 0,
          "currency": "",
          "maxUsers": 0,
          "maxEmployees": 0,
          "features": "",
          "isCustom": false,
          "isActive": false,
          "isPublic": false,
          "displayOrder": 0,
          "createdAt": "2026-06-17",
          "updatedAt": "2026-06-17",
          "deletedAt": "2026-06-17",
          "isActive": "",
          "deletedAt": "",
          "isActive": "",
          "false": "return",
          "ZERO": "",
          "price": "",
          "validityMonths": "",
          "isActive": "",
          "isPublic": "",
          "currency": "",
          "displayOrder": "",
          "isCustom": "",
          "price": "",
          "isActive": ""
        },
        "maxEmployees": 0,
        "planStatus": "PlanStatus",
        "endsAt": "2026-06-17",
        "suspendedAt": "2026-06-17",
        "suspensionReason": "",
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "expiredAt": "2026-06-17",
        "archivedAt": "2026-06-17",
        "archiveWarningNotifiedAt": "2026-06-17",
        "finalReminderSentAt": "2026-06-17",
        "expirationNotifiedAt": "2026-06-17",
        "dataStatus": "TenantDataStatus",
        "deletedByAdminId": 0,
        "createdBy": 0,
        "updatedBy": 0,
        "version": 0,
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "planStatus": "",
        "suspensionReason": "",
        "dataStatus": "",
        "archivedAt": "",
        "expiredAt": "",
        "archivedAt": "",
        "archiveWarningNotifiedAt": "",
        "finalReminderSentAt": "",
        "expirationNotifiedAt": "",
        "dataStatus": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "deletedAt": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "status": "",
        "0": "return",
        "isActive": "return",
        "true": "return",
        "false": "return"
      },
      "name": "",
      "code": "",
      "description": "",
      "isActive": false,
      "isDeleted": false,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17"
    },
    "shift": {
      "id": 0,
      "tenantId": 0,
      "shiftName": "",
      "shiftCode": "",
      "shiftDescription": "",
      "startTime": "LocalTime",
      "endTime": "LocalTime",
      "totalHours": 0,
      "breakDurationMinutes": 0,
      "minBreakMinutes": 0,
      "maxBreakMinutes": 0,
      "lateGraceMinutes": 0,
      "earlyExitGraceMinutes": 0,
      "checkinBufferBefore": 0,
      "checkoutBufferAfter": 0,
      "fullDayHours": 0,
      "halfDayHours": 0,
      "quarterDayHours": 0,
      "allowOvertime": false,
      "overtimeMultiplier": 0,
      "overtimeThresholdMinutes": 0,
      "maxOvertimeHoursPerDay": 0,
      "weeklyOffs": "",
      "alternateWeekOff": false,
      "effectiveFrom": "2026-06-17",
      "effectiveTo": "2026-06-17",
      "isActive": false,
      "isDefault": false,
      "isDeleted": false,
      "createdBy": 0,
      "updatedBy": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "startTime": "return",
      "endTime": "return",
      "startTime": "return",
      "endTime": "return"
    },
    "position": "",
    "manager": "[Recursive Reference to Employee]",
    "employmentType": "EmploymentType",
    "workLocation": "",
    "workState": "IndianState",
    "workStateText": "",
    "workCountry": "",
    "hireDate": "2026-06-17",
    "confirmationDate": "2026-06-17",
    "resignationDate": "2026-06-17",
    "lastWorkingDate": "2026-06-17",
    "status": "EmployeeStatus",
    "address": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "postalCode": "",
    "emergencyContactName": "",
    "emergencyContactPhone": "",
    "emergencyContactRelation": "",
    "emergencyContactEmail": "",
    "secondaryEmergencyName": "",
    "secondaryEmergencyPhone": "",
    "profilePictureUrl": "",
    "weekendConfig": "WeekendConfig",
    "customWeekendDays": "",
    "linkedinUrl": "",
    "githubUrl": "",
    "twitterUrl": "",
    "createdBy": 0,
    "updatedBy": 0,
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "rolesVersion": "return",
    "rolesVersion": "",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "return",
    "cachedRolesVersion": "",
    "tenantId": "return",
    "0": "return",
    "0": "return",
    "isActive": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "true": "return",
    "isActive": "",
    "passwordHash": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "status": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "isActive": "",
    "status": "",
    "status": "",
    "confirmationDate": "return",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "authorities": "return",
    "passwordHash": "return",
    "email": "return",
    "true": "return",
    "true": "return",
    "mustChangePassword": "return !",
    "status": "return isActive &&",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return"
  },
  "terminationDate": "2026-06-17",
  "lastDrawnBasic": 0,
  "gratuityAmount": 0,
  "gratuityExempt": 0,
  "gratuityTaxable": 0,
  "encashmentAmount": 0,
  "encashmentExempt": 0,
  "encashmentTaxable": 0,
  "proratedSalary": 0,
  "loanRecovery": 0,
  "totalTds": 0,
  "netSettlement": 0,
  "status": ""
}
```

---

## LeaveManagementController

### GET `/api/employees/leaves/policies`
- **Handler Method**: `getLeavePolicies`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>`
#### Response JSON Example:
```json
"LeavePolicyDTO>"
```

---

### PUT `/api/employees/leaves/policies/{leaveType}`
- **Handler Method**: `updateLeavePolicy`
- **Description**: No description provided
- **Request Body Type**: `com`
#### Request JSON Example:
```json
"com"
```
- **Response Type**: `com.sonixhr.dto.leave.LeavePolicyDTO`
#### Response JSON Example:
```json
{
  "allowed": false,
  "daysPerYear": 0,
  "carryForward": false,
  "maxCarryForwardDays": 0,
  "minimumServiceMonths": 0,
  "genderEligibility": "",
  "probationPeriodAllowed": false,
  "prorated": false,
  "allowed": false,
  "daysPerYear": 0,
  "carryForward": false,
  "maxCarryForwardDays": 0,
  "minimumServiceMonths": 0,
  "genderEligibility": "",
  "probationPeriodAllowed": false,
  "prorated": false,
  "allowed": "",
  "this": "return",
  "daysPerYear": "",
  "this": "return",
  "carryForward": "",
  "this": "return",
  "maxCarryForwardDays": "",
  "this": "return",
  "minimumServiceMonths": "",
  "this": "return",
  "genderEligibility": "",
  "this": "return",
  "probationPeriodAllowed": "",
  "this": "return",
  "prorated": "",
  "this": "return",
  "allowed": "",
  "daysPerYear": "",
  "carryForward": "",
  "maxCarryForwardDays": "",
  "minimumServiceMonths": "",
  "genderEligibility": "",
  "probationPeriodAllowed": "",
  "prorated": "",
  "policy": "return"
}
```

---

### GET `/api/employees/leaves/settings`
- **Handler Method**: `getTenantLeaveSettings`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `LeaveSettingsDTO`
#### Response JSON Example:
```json
{
  "leavePolicies": { "key": "value" },
  "policiesConfigured": false,
  "tenantId": 0,
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "countWeekendsAsLeave": false,
  "countHolidaysAsLeave": false,
  "weekendDisplay": "",
  "casualLeavePerYear": 0,
  "sickLeavePerYear": 0,
  "earnedLeavePerYear": 0,
  "emergencyLeavePerYear": 0,
  "maternityLeavePerYear": 0,
  "paternityLeavePerYear": 0,
  "unpaidLeavePerYear": 0,
  "compensatoryLeavePerYear": 0,
  "maxConsecutiveLeaveDays": 0,
  "leaveApprovalRequired": false,
  "autoApproveForManager": false,
  "country": "",
  "state": "IndianState",
  "stateText": "",
  "includeNationalHolidays": false,
  "includeStateHolidays": false,
  "workingDays": "",
  "weekendDays": "",
  "timezone": ""
}
```

---

### PUT `/api/employees/leaves/settings`
- **Handler Method**: `updateTenantLeaveSettings`
- **Description**: No description provided
- **Request Body Type**: `LeaveSettingsDTO`
#### Request JSON Example:
```json
{
  "leavePolicies": { "key": "value" },
  "policiesConfigured": false,
  "tenantId": 0,
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "countWeekendsAsLeave": false,
  "countHolidaysAsLeave": false,
  "weekendDisplay": "",
  "casualLeavePerYear": 0,
  "sickLeavePerYear": 0,
  "earnedLeavePerYear": 0,
  "emergencyLeavePerYear": 0,
  "maternityLeavePerYear": 0,
  "paternityLeavePerYear": 0,
  "unpaidLeavePerYear": 0,
  "compensatoryLeavePerYear": 0,
  "maxConsecutiveLeaveDays": 0,
  "leaveApprovalRequired": false,
  "autoApproveForManager": false,
  "country": "",
  "state": "IndianState",
  "stateText": "",
  "includeNationalHolidays": false,
  "includeStateHolidays": false,
  "workingDays": "",
  "weekendDays": "",
  "timezone": ""
}
```
- **Response Type**: `LeaveSettingsDTO`
#### Response JSON Example:
```json
{
  "leavePolicies": { "key": "value" },
  "policiesConfigured": false,
  "tenantId": 0,
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "countWeekendsAsLeave": false,
  "countHolidaysAsLeave": false,
  "weekendDisplay": "",
  "casualLeavePerYear": 0,
  "sickLeavePerYear": 0,
  "earnedLeavePerYear": 0,
  "emergencyLeavePerYear": 0,
  "maternityLeavePerYear": 0,
  "paternityLeavePerYear": 0,
  "unpaidLeavePerYear": 0,
  "compensatoryLeavePerYear": 0,
  "maxConsecutiveLeaveDays": 0,
  "leaveApprovalRequired": false,
  "autoApproveForManager": false,
  "country": "",
  "state": "IndianState",
  "stateText": "",
  "includeNationalHolidays": false,
  "includeStateHolidays": false,
  "workingDays": "",
  "weekendDays": "",
  "timezone": ""
}
```

---

### GET `/api/employees/leaves/team`
- **Handler Method**: `getTeamLeaveRequests`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<LeaveResponseDTO>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "leaveType": "LeaveType",
  "leaveTypeDisplay": "",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalDays": 0,
  "reason": "",
  "status": "LeaveStatus",
  "statusDisplay": "",
  "rejectionReason": "",
  "approvedBy": 0,
  "approvedByName": "",
  "approvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "isHalfDay": false
}
]
```

---

### PUT `/api/employees/leaves/{id}/approve`
- **Handler Method**: `approveLeave`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `LeaveResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "leaveType": "LeaveType",
  "leaveTypeDisplay": "",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalDays": 0,
  "reason": "",
  "status": "LeaveStatus",
  "statusDisplay": "",
  "rejectionReason": "",
  "approvedBy": 0,
  "approvedByName": "",
  "approvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "isHalfDay": false
}
```

---

### PUT `/api/employees/leaves/{id}/reject`
- **Handler Method**: `rejectLeave`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `LeaveResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "leaveType": "LeaveType",
  "leaveTypeDisplay": "",
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalDays": 0,
  "reason": "",
  "status": "LeaveStatus",
  "statusDisplay": "",
  "rejectionReason": "",
  "approvedBy": 0,
  "approvedByName": "",
  "approvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "isHalfDay": false
}
```

---

### PUT `/api/employees/{employeeId}/leave-settings`
- **Handler Method**: `updateEmployeeLeaveSettings`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeCode": "",
  "userId": 0,
  "isActive": false,
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "initials": "",
  "email": "",
  "phone": "",
  "dateOfBirth": "2026-06-17",
  "gender": "Gender",
  "maritalStatus": "MaritalStatus",
  "bloodGroup": "BloodGroup",
  "nationality": "",
  "personalEmail": "",
  "department": "DepartmentInfo",
  "position": "",
  "manager": "ManagerInfo",
  "shift": "ShiftInfo",
  "employmentType": "EmploymentType",
  "workLocation": "",
  "workState": "IndianState",
  "workStateText": "",
  "workCountry": "",
  "hireDate": "2026-06-17",
  "confirmationDate": "2026-06-17",
  "resignationDate": "2026-06-17",
  "lastWorkingDate": "2026-06-17",
  "tenureInMonths": 0,
  "status": "EmployeeStatus",
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "address": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "postalCode": "",
  "emergencyContactName": "",
  "emergencyContactPhone": "",
  "emergencyContactRelation": "",
  "emergencyContactEmail": "",
  "secondaryEmergencyName": "",
  "secondaryEmergencyPhone": "",
  "profilePictureUrl": "",
  "bankDetails": {
    "id": 0,
    "accountHolderName": "",
    "maskedAccountNumber": "",
    "ifscCode": "",
    "bankName": "",
    "branchName": "",
    "accountType": "",
    "isPrimary": false,
    "isActive": false
  },
  "documents": { "key": "value" },
  "certifications": { "key": "value" },
  "linkedinUrl": "",
  "githubUrl": "",
  "twitterUrl": "",
  "weekendConfig": "WeekendConfig",
  "customWeekendDays": "",
  "directReports": [ "[Recursive Reference to EmployeeResponse]" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "id": 0,
  "fullName": "",
  "email": "",
  "position": "",
  "department": "",
  "employeeCode": "",
  "id": 0,
  "name": "",
  "code": "",
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "startTime": "",
  "endTime": ""
}
```

---

## ManualAttendanceController

### GET `/api/attendance/dashboard`
- **Handler Method**: `getDashboardStats`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ManualDashboardStatsResponse`
#### Response JSON Example:
```json
{
  "date": "2026-06-17",
  "totalEmployees": 0,
  "present": 0,
  "absent": 0,
  "onLeave": 0,
  "pending": 0,
  "attendancePercentage": 0
}
```

---

### GET `/api/attendance/employee/{employeeId}/calendar`
- **Handler Method**: `getEmployeeCalendar`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ManualAttendanceCalendarResponse`
#### Response JSON Example:
```json
{
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "year": 0,
  "month": 0,
  "calendar": { "key": "value" },
  "date": "2026-06-17",
  "dayOfWeek": "",
  "status": "",
  "reason": "",
  "overtimeHours": 0,
  "markedBy": "",
  "isWeekend": false,
  "isHoliday": false
}
```

---

### GET `/api/attendance/employee/{employeeId}/summary`
- **Handler Method**: `getEmployeeMonthlySummary`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ManualAttendanceSummaryResponse`
#### Response JSON Example:
```json
{
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "hireDate": "2026-06-17",
  "year": 0,
  "month": 0,
  "periodStart": "2026-06-17",
  "periodEnd": "2026-06-17",
  "totalDaysInMonth": 0,
  "present": 0,
  "absent": 0,
  "halfDay": 0,
  "late": 0,
  "onLeave": 0,
  "totalOvertimeHours": 0,
  "attendanceRate": 0,
  "message": ""
}
```

---

### POST `/api/attendance/mark`
- **Handler Method**: `markAttendance`
- **Description**: No description provided
- **Request Body Type**: `ManualAttendanceMarkRequest`
#### Request JSON Example:
```json
{
  "employeeId": 0,
  "attendanceDate": "2026-06-17",
  "status": "AttendanceStatus",
  "reason": "",
  "overtimeHours": 0
}
```
- **Response Type**: `ManualAttendanceRecordResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "attendanceDate": "2026-06-17",
  "status": "AttendanceStatus",
  "overtimeHours": 0,
  "reason": "",
  "markedByName": "",
  "markedByRole": "",
  "markedAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### POST `/api/attendance/overtime`
- **Handler Method**: `addOvertime`
- **Description**: No description provided
- **Request Body Type**: `ManualOvertimeRequest`
#### Request JSON Example:
```json
{
  "employeeId": 0,
  "date": "2026-06-17",
  "overtimeHours": 0,
  "reason": ""
}
```
- **Response Type**: `ManualAttendanceRecordResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "attendanceDate": "2026-06-17",
  "status": "AttendanceStatus",
  "overtimeHours": 0,
  "reason": "",
  "markedByName": "",
  "markedByRole": "",
  "markedAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### POST `/api/attendance/team/bulk`
- **Handler Method**: `bulkMarkTeamAttendance`
- **Description**: No description provided
- **Request Body Type**: `ManualBulkAttendanceMarkRequest`
#### Request JSON Example:
```json
{
  "attendanceDate": "2026-06-17",
  "attendanceMap": { "key": "value" },
  "reasonMap": { "key": "value" },
  "overtimeMap": { "key": "value" }
}
```
- **Response Type**: `ManualAttendanceRecordResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantId": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "attendanceDate": "2026-06-17",
  "status": "AttendanceStatus",
  "overtimeHours": 0,
  "reason": "",
  "markedByName": "",
  "markedByRole": "",
  "markedAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/attendance/team/members`
- **Handler Method**: `getTeamWithTodayAttendance`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ManualTeamMemberAttendanceDTO[]`
#### Response JSON Example:
```json
[
{
  "employeeId": 0,
  "employeeCode": "",
  "employeeName": "",
  "email": "",
  "position": "",
  "profilePicture": "",
  "hireDate": "2026-06-17",
  "todayStatus": "AttendanceStatus",
  "todayOvertime": 0,
  "todayReason": "",
  "isMarked": false
}
]
```

---

### POST `/api/attendance/team/quick-mark`
- **Handler Method**: `quickMarkTeamAttendance`
- **Description**: No description provided
- **Request Body Type**: `ManualQuickMarkRequest`
#### Request JSON Example:
```json
{
  "status": "AttendanceStatus",
  "reason": ""
}
```
- **Response Type**: `ManualAttendanceRecordResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantId": 0,
  "employeeId": 0,
  "employeeName": "",
  "employeeCode": "",
  "attendanceDate": "2026-06-17",
  "status": "AttendanceStatus",
  "overtimeHours": 0,
  "reason": "",
  "markedByName": "",
  "markedByRole": "",
  "markedAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/attendance/team/search`
- **Handler Method**: `searchTeamWithTodayAttendance`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ManualTeamMemberAttendanceDTO[]`
#### Response JSON Example:
```json
[
{
  "employeeId": 0,
  "employeeCode": "",
  "employeeName": "",
  "email": "",
  "position": "",
  "profilePicture": "",
  "hireDate": "2026-06-17",
  "todayStatus": "AttendanceStatus",
  "todayOvertime": 0,
  "todayReason": "",
  "isMarked": false
}
]
```

---

### GET `/api/attendance/team/summary`
- **Handler Method**: `getTeamAttendanceSummary`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ManualTeamAttendanceSummaryResponse`
#### Response JSON Example:
```json
{
  "startDate": "2026-06-17",
  "endDate": "2026-06-17",
  "totalTeamMembers": 0,
  "teamTotals": { "key": "value" },
  "employeeSummaries": { "key": "value" },
  "message": ""
}
```

---

## NotificationController

### GET `/api/notifications`
- **Handler Method**: `getMyNotifications`
- **Description**: Retrieve all notifications for the authenticated employee (mobile app polling endpoint).
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Notification[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantId": 0,
  "employeeId": 0,
  "title": "",
  "message": "",
  "type": "",
  "isRead": false,
  "createdAt": "2026-06-17",
  "id": "",
  "tenantId": "",
  "employeeId": "",
  "title": "",
  "message": "",
  "type": "",
  "isRead": "",
  "createdAt": "",
  "id": "return",
  "id": "",
  "tenantId": "return",
  "tenantId": "",
  "employeeId": "return",
  "employeeId": "",
  "title": "return",
  "title": "",
  "message": "return",
  "message": "",
  "type": "return",
  "type": "",
  "isRead": "return",
  "isRead": "",
  "createdAt": "return",
  "createdAt": "",
  "id": 0,
  "tenantId": 0,
  "employeeId": 0,
  "title": "",
  "message": "",
  "type": "",
  "isRead": false,
  "createdAt": "2026-06-17",
  "id": "",
  "this": "return",
  "tenantId": "",
  "this": "return",
  "employeeId": "",
  "this": "return",
  "title": "",
  "this": "return",
  "message": "",
  "this": "return",
  "type": "",
  "this": "return",
  "isRead": "",
  "this": "return",
  "createdAt": "",
  "this": "return"
}
]
```

---

### PUT `/api/notifications/read-all`
- **Handler Method**: `markAllAsRead`
- **Description**: Mark all notifications as read for the authenticated employee.
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/notifications/stream`
- **Handler Method**: `streamNotifications`
- **Description**: Establish a Server-Sent Events (SSE) streaming connection for real-time notifications on mobile/web.
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SseEmitter`
#### Response JSON Example:
```json
"SseEmitter"
```

---

### GET `/api/notifications/unread-count`
- **Handler Method**: `getUnreadCount`
- **Description**: Get the count of unread notifications for the authenticated employee.
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Long>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### PUT `/api/notifications/{id}/read`
- **Handler Method**: `markAsRead`
- **Description**: Mark a specific notification as read.
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

## PayrollConfigurationController

### GET `/api/payroll/config`
- **Handler Method**: `getTenantConfig`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantPayrollConfigResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenantId": 0,
  "lopBasis": "LopBasis",
  "workingDaysPerMonth": 0,
  "enablePfCapping": false,
  "enableEsi": false,
  "enablePt": false,
  "enforceNewLabourCodes": false,
  "defaultCurrency": "",
  "defaultTaxRegime": "",
  "enableOvertime": false,
  "overtimeRatePerHour": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "salaryStructures": [ "SalaryStructureResponse" ],
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "value": 0,
  "evaluationOrder": 0,
  "isPartOfPfWages": false,
  "isPartOfEsiWages": false,
  "isTaxable": false,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "minValue": 0,
  "maxValue": 0,
  "formulaExpression": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false
}
```

---

### PUT `/api/payroll/config`
- **Handler Method**: `updateTenantConfig`
- **Description**: No description provided
- **Request Body Type**: `TenantPayrollConfigRequest`
#### Request JSON Example:
```json
{
  "lopBasis": "LopBasis",
  "workingDaysPerMonth": 0,
  "enablePfCapping": false,
  "enableEsi": false,
  "enablePt": false,
  "enforceNewLabourCodes": false,
  "defaultCurrency": "",
  "defaultTaxRegime": "",
  "enableOvertime": false,
  "overtimeRatePerHour": 0,
  "effectiveFrom": "2026-06-17",
  "salaryStructures": [ "SalaryStructureRequest" ],
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "value": 0,
  "evaluationOrder": 0,
  "isPartOfPfWages": false,
  "isPartOfEsiWages": false,
  "isTaxable": false,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "minValue": 0,
  "maxValue": 0,
  "formulaExpression": ""
}
```
- **Response Type**: `TenantPayrollConfigResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenantId": 0,
  "lopBasis": "LopBasis",
  "workingDaysPerMonth": 0,
  "enablePfCapping": false,
  "enableEsi": false,
  "enablePt": false,
  "enforceNewLabourCodes": false,
  "defaultCurrency": "",
  "defaultTaxRegime": "",
  "enableOvertime": false,
  "overtimeRatePerHour": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "salaryStructures": [ "SalaryStructureResponse" ],
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "value": 0,
  "evaluationOrder": 0,
  "isPartOfPfWages": false,
  "isPartOfEsiWages": false,
  "isTaxable": false,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "minValue": 0,
  "maxValue": 0,
  "formulaExpression": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false
}
```

---

## PayrollController

### POST `/api/payroll/calculate`
- **Handler Method**: `calculate`
- **Description**: No description provided
- **Request Body Type**: `PayrollCalculationRequest`
#### Request JSON Example:
```json
{
  "ctc": 0,
  "state": "IndianState",
  "month": 0,
  "year": 0,
  "lopDays": 0,
  "compliantMode": false,
  "pfCapping": false,
  "esiPeriodStartGross": 0
}
```
- **Response Type**: `PayrollCalculationResponse`
#### Response JSON Example:
```json
{
  "ctc": 0,
  "grossEarnings": 0,
  "totalDeductions": 0,
  "netPay": 0,
  "wagesBase": 0,
  "totalEmployerContributions": 0,
  "reconciledCtc": 0,
  "components": { "key": "value" }
}
```

---

## PayslipController

### GET `/api/employees/{employeeId}/payslips`
- **Handler Method**: `getEmployeePayslips`
- **Description**: Get employee payslips history
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PayslipSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "payrunId": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "fullName": "",
  "month": 0,
  "year": 0,
  "grossEarnings": 0,
  "totalDeductions": 0,
  "netPay": 0
}
]
```

---

### POST `/api/payroll/payruns`
- **Handler Method**: `executePayrun`
- **Description**: Execute payrun for the tenant
- **Request Body Type**: `PayrunRequestDto`
#### Request JSON Example:
```json
"PayrunRequestDto"
```
- **Response Type**: `PayrunResponseDto`
#### Response JSON Example:
```json
"PayrunResponseDto"
```

---

### GET `/api/payroll/payslips`
- **Handler Method**: `all`
- **Description**: Get tenant payslips for a specific payrun month and year
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `description = "Retrieves payslips for a tenant's payrun. EMPLOYEE_VIEW_ALL/SETTINGS_VIEW returns`
#### Response JSON Example:
```json
" EMPLOYEE_VIEW_ALL/SETTINGS_VIEW returns"
```

---

### GET `/api/payroll/payslips/my`
- **Handler Method**: `getMyPayslips`
- **Description**: Get currently authenticated employee payslips
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PayslipSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "payrunId": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "fullName": "",
  "month": 0,
  "year": 0,
  "grossEarnings": 0,
  "totalDeductions": 0,
  "netPay": 0
}
]
```

---

### GET `/api/payroll/payslips/{id}`
- **Handler Method**: `getPayslip`
- **Description**: Get detailed payslip by id
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PayslipResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "payrunId": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "fullName": "",
  "departmentName": "",
  "designation": "",
  "month": 0,
  "year": 0,
  "grossEarnings": 0,
  "totalDeductions": 0,
  "netPay": 0,
  "lopDays": 0,
  "wagesBase": 0,
  "items": [ "PayslipItemDto" ],
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "type": "",
  "amount": 0,
  "expressionUsed": ""
}
```

---

## PlatformAuthController

### POST `/api/platform/auth/activate`
- **Handler Method**: `activateUser`
- **Description**: No description provided
- **Request Body Type**: `ActivationRequest`
#### Request JSON Example:
```json
{
  "token": "",
  "password": "",
  "confirmPassword": ""
}
```
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### POST `/api/platform/auth/forgot-password`
- **Handler Method**: `forgotPassword`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, String>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### POST `/api/platform/auth/login`
- **Handler Method**: `login`
- **Description**: No description provided
- **Request Body Type**: `LoginRequest`
#### Request JSON Example:
```json
{
  "email": "",
  "password": ""
}
```
- **Response Type**: `LoginResponse`
#### Response JSON Example:
```json
{
  "accessToken": "",
  "refreshToken": "",
  "tokenType": "",
  "expiresIn": 0,
  "email": "",
  "fullName": ""
}
```

---

### POST `/api/platform/auth/logout`
- **Handler Method**: `logout`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, String>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### POST `/api/platform/auth/refresh`
- **Handler Method**: `refreshToken`
- **Description**: No description provided
- **Request Body Type**: `RefreshTokenRequest`
#### Request JSON Example:
```json
{
  "refreshToken": ""
}
```
- **Response Type**: `LoginResponse`
#### Response JSON Example:
```json
{
  "accessToken": "",
  "refreshToken": "",
  "tokenType": "",
  "expiresIn": 0,
  "email": "",
  "fullName": ""
}
```

---

### POST `/api/platform/auth/reset-password`
- **Handler Method**: `resetPassword`
- **Description**: No description provided
- **Request Body Type**: `SetPasswordRequest`
#### Request JSON Example:
```json
{
  "token": "",
  "newPassword": "",
  "confirmPassword": ""
}
```
- **Response Type**: `Map<String, String>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/platform/auth/verify-token`
- **Handler Method**: `verifyToken`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

## PlatformDashboardController

### GET `/api/platform/dashboard`
- **Handler Method**: `getDashboard`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformDashboardDTO`
#### Response JSON Example:
```json
{
  "tenantSummary": "TenantSummary",
  "subscriptionSummary": "SubscriptionSummary",
  "systemSummary": "SystemSummary",
  "recentTenants": [ "RecentTenant" ],
  "registrationTrend": [ "RegistrationTrendPoint" ],
  "upsellOpportunities": [ "UpsellOpportunity" ],
  "topResourceConsumers": [ "ResourceConsumer" ],
  "totalTenants": 0,
  "activeTenants": 0,
  "suspendedTenants": 0,
  "deletedTenants": 0,
  "trialTenants": 0,
  "planDistribution": { "key": "value" },
  "activePaidSubscriptions": 0,
  "activeTrials": 0,
  "totalMrr": 0,
  "planStatusDistribution": { "key": "value" },
  "expiredSubscriptions": 0,
  "totalEmployees": 0,
  "totalPlatformUsers": 0,
  "averageEmployeesPerTenant": 0,
  "totalActiveUsers": 0,
  "supportTicketsOpen": 0,
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "planType": "",
  "status": "UserStatus",
  "createdAt": "2026-06-17",
  "period": "",
  "count": 0,
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "currentEmployees": 0,
  "maxEmployees": 0,
  "utilizationPercentage": 0,
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "currentEmployees": 0,
  "currentStorageMb": 0,
  "apiCallsCount": 0
}
```

---

### GET `/api/platform/health`
- **Handler Method**: `getHealth`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SystemHealthDTO`
#### Response JSON Example:
```json
{
  "databaseStatus": "",
  "redisStatus": "",
  "mailSenderStatus": "",
  "diskSpaceStatus": "",
  "freeDiskSpaceBytes": 0,
  "totalDiskSpaceBytes": 0
}
```

---

## PlatformDebugController

### GET `/api/platform/debug/platform-setup`
- **Handler Method**: `checkPlatformSetup`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/platform/debug/security-context`
- **Handler Method**: `debugSecurityContext`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/platform/debug/test-auth`
- **Handler Method**: `testAuth`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

## PlatformNotificationController

### GET `/api/platform/notifications`
- **Handler Method**: `getMyNotifications`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformNotificationResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "title": "",
  "message": "",
  "type": "",
  "isRead": false,
  "createdAt": "2026-06-17"
}
]
```

---

### PUT `/api/platform/notifications/{id}/read`
- **Handler Method**: `markAsRead`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

## PlatformPayrollController

### GET `/api/platform/payroll/pt-configs`
- **Handler Method**: `getAllPtConfigs`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `StateProfessionalTaxConfig[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "stateCode": "IndianState",
  "salaryRangeMin": 0,
  "salaryRangeMax": 0,
  "applicableMonth": 0,
  "amount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
]
```

---

### POST `/api/platform/payroll/pt-configs`
- **Handler Method**: `createPtConfig`
- **Description**: No description provided
- **Request Body Type**: `StateProfessionalTaxConfig`
#### Request JSON Example:
```json
{
  "id": "UUID",
  "stateCode": "IndianState",
  "salaryRangeMin": 0,
  "salaryRangeMax": 0,
  "applicableMonth": 0,
  "amount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```
- **Response Type**: `StateProfessionalTaxConfig`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "stateCode": "IndianState",
  "salaryRangeMin": 0,
  "salaryRangeMax": 0,
  "applicableMonth": 0,
  "amount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```

---

### DELETE `/api/platform/payroll/pt-configs/{id}`
- **Handler Method**: `deletePtConfig`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### PUT `/api/platform/payroll/pt-configs/{id}`
- **Handler Method**: `updatePtConfig`
- **Description**: No description provided
- **Request Body Type**: `StateProfessionalTaxConfig`
#### Request JSON Example:
```json
{
  "id": "UUID",
  "stateCode": "IndianState",
  "salaryRangeMin": 0,
  "salaryRangeMax": 0,
  "applicableMonth": 0,
  "amount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```
- **Response Type**: `StateProfessionalTaxConfig`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "stateCode": "IndianState",
  "salaryRangeMin": 0,
  "salaryRangeMax": 0,
  "applicableMonth": 0,
  "amount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```

---

### GET `/api/platform/payroll/statutory-rates`
- **Handler Method**: `getAllStatutoryRates`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `StatutoryRateConfig[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "componentCode": "",
  "rate": 0,
  "wageBase": "",
  "ceilingAmount": 0,
  "capAmount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
]
```

---

### POST `/api/platform/payroll/statutory-rates`
- **Handler Method**: `createStatutoryRate`
- **Description**: No description provided
- **Request Body Type**: `StatutoryRateConfig`
#### Request JSON Example:
```json
{
  "id": "UUID",
  "componentCode": "",
  "rate": 0,
  "wageBase": "",
  "ceilingAmount": 0,
  "capAmount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```
- **Response Type**: `StatutoryRateConfig`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "componentCode": "",
  "rate": 0,
  "wageBase": "",
  "ceilingAmount": 0,
  "capAmount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```

---

### DELETE `/api/platform/payroll/statutory-rates/{id}`
- **Handler Method**: `deleteStatutoryRate`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### PUT `/api/platform/payroll/statutory-rates/{id}`
- **Handler Method**: `updateStatutoryRate`
- **Description**: No description provided
- **Request Body Type**: `StatutoryRateConfig`
#### Request JSON Example:
```json
{
  "id": "UUID",
  "componentCode": "",
  "rate": 0,
  "wageBase": "",
  "ceilingAmount": 0,
  "capAmount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```
- **Response Type**: `StatutoryRateConfig`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "componentCode": "",
  "rate": 0,
  "wageBase": "",
  "ceilingAmount": 0,
  "capAmount": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isDeleted": false
}
```

---

## PlatformPermissionController

### GET `/api/platform/permissions`
- **Handler Method**: `getAllPermissions`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PermissionDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "permission": "",
  "description": "",
  "category": "",
  "displayOrder": 0,
  "selected": false,
  "null": "return"
}
]
```

---

### GET `/api/platform/permissions/groups`
- **Handler Method**: `getGroupedPermissions`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PermissionGroupDTO[]`
#### Response JSON Example:
```json
[
{
  "groupName": "",
  "permissions": [ "PermissionInfo" ],
  "id": 0,
  "name": "",
  "description": "",
  "category": "",
  "displayOrder": 0,
  "selected": false
}
]
```

---

## PlatformRoleController

### GET `/api/platform/roles`
- **Handler Method**: `getAllRoles`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformRoleResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "name": "",
  "description": "",
  "isSystemRole": false,
  "permissions": [ "PermissionInfo" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": ""
}
]
```

---

### POST `/api/platform/roles`
- **Handler Method**: `createRole`
- **Description**: No description provided
- **Request Body Type**: `PlatformRoleCreateRequest`
#### Request JSON Example:
```json
{
  "name": "",
  "description": "",
  "permissionIds": 0
}
```
- **Response Type**: `PlatformRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isSystemRole": false,
  "permissions": [ "PermissionInfo" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### GET `/api/platform/roles/lookup`
- **Handler Method**: `getRoleLookup`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformRoleLookupResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "name": "",
  "isSystemRole": false
}
]
```

---

### DELETE `/api/platform/roles/{roleId}`
- **Handler Method**: `deleteRole`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/platform/roles/{roleId}`
- **Handler Method**: `getRole`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isSystemRole": false,
  "permissions": [ "PermissionInfo" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### PUT `/api/platform/roles/{roleId}`
- **Handler Method**: `updateRole`
- **Description**: No description provided
- **Request Body Type**: `PlatformRoleUpdateRequest`
#### Request JSON Example:
```json
{
  "name": "",
  "description": "",
  "permissionIds": 0
}
```
- **Response Type**: `PlatformRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isSystemRole": false,
  "permissions": [ "PermissionInfo" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### GET `/api/platform/roles/{roleId}/delete-preview`
- **Handler Method**: `getRoleDeletePreview`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformRoleDeletePreviewResponse`
#### Response JSON Example:
```json
{
  "roleId": 0,
  "roleName": "",
  "affectedUserCount": 0,
  "affectedUsers": [
  {
    "id": 0,
    "email": "",
    "fullName": "",
    "designation": "",
    "status": "UserStatus",
    "isActive": false,
    "lastLoginAt": "2026-06-17",
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "invitationLink": "",
    "invitationExpiryAt": "2026-06-17",
    "roles": [
    {
      "id": 0,
      "name": "",
      "description": "",
      "isSystemRole": false,
      "permissions": [ "PermissionInfo" ],
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "id": 0,
      "name": "",
      "description": ""
    }
    ],
    "id": 0,
    "name": "",
    "description": ""
  }
  ],
  "reassignmentOptions": [
  {
    "id": 0,
    "name": "",
    "isSystemRole": false
  }
  ],
  "deletable": false,
  "validationMessage": ""
}
```

---

### PUT `/api/platform/roles/{roleId}/permissions`
- **Handler Method**: `assignPermissions`
- **Description**: No description provided
- **Request Body Type**: `Set<Long>`
#### Request JSON Example:
```json
0
```
- **Response Type**: `PlatformRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isSystemRole": false,
  "permissions": [ "PermissionInfo" ],
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### GET `/api/platform/roles/{roleId}/users`
- **Handler Method**: `getUsersByRole`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformUserResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "email": "",
  "fullName": "",
  "designation": "",
  "status": "UserStatus",
  "isActive": false,
  "lastLoginAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "invitationLink": "",
  "invitationExpiryAt": "2026-06-17",
  "roles": [
  {
    "id": 0,
    "name": "",
    "description": "",
    "isSystemRole": false,
    "permissions": [ "PermissionInfo" ],
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "id": 0,
    "name": "",
    "description": ""
  }
  ],
  "id": 0,
  "name": "",
  "description": ""
}
]
```

---

### DELETE `/api/platform/roles/{roleId}/users/{userId}`
- **Handler Method**: `removeRoleFromUser`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### POST `/api/platform/roles/{roleId}/users/{userId}`
- **Handler Method**: `assignRoleToUser`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

## PlatformSubscriptionController

### GET `/api/platform/subscriptions/dashboard`
- **Handler Method**: `getDashboard`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SubscriptionDashboardDTO`
#### Response JSON Example:
```json
{
  "monthlyRevenue": [ "ChartPoint" ],
  "subscriptionGrowth": [ "ChartPoint" ],
  "planDistribution": [ "ChartPoint" ],
  "label": "",
  "value": 0
}
```

---

## PlatformSubscriptionPlanController

### GET `/api/platform/subscription-plans`
- **Handler Method**: `getAllPlans`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SubscriptionPlanDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "code": "",
  "name": "",
  "price": 0,
  "validityMonths": 0,
  "isActive": false,
  "description": ""
}
]
```

---

### POST `/api/platform/subscription-plans`
- **Handler Method**: `createPlan`
- **Description**: No description provided
- **Request Body Type**: `SubscriptionPlanDTO`
#### Request JSON Example:
```json
{
  "id": 0,
  "code": "",
  "name": "",
  "price": 0,
  "validityMonths": 0,
  "isActive": false,
  "description": ""
}
```
- **Response Type**: `SubscriptionPlanDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "code": "",
  "name": "",
  "price": 0,
  "validityMonths": 0,
  "isActive": false,
  "description": ""
}
```

---

### DELETE `/api/platform/subscription-plans/{id}`
- **Handler Method**: `deletePlan`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/platform/subscription-plans/{id}`
- **Handler Method**: `getPlanById`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SubscriptionPlanDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "code": "",
  "name": "",
  "price": 0,
  "validityMonths": 0,
  "isActive": false,
  "description": ""
}
```

---

### PUT `/api/platform/subscription-plans/{id}`
- **Handler Method**: `updatePlan`
- **Description**: No description provided
- **Request Body Type**: `SubscriptionPlanDTO`
#### Request JSON Example:
```json
{
  "id": 0,
  "code": "",
  "name": "",
  "price": 0,
  "validityMonths": 0,
  "isActive": false,
  "description": ""
}
```
- **Response Type**: `SubscriptionPlanDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "code": "",
  "name": "",
  "price": 0,
  "validityMonths": 0,
  "isActive": false,
  "description": ""
}
```

---

## PlatformSupportTicketController

### GET `/api/platform/support-tickets`
- **Handler Method**: `getAllTickets`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<SupportTicketResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "ticketNumber": "",
  "tenantId": 0,
  "tenantCompanyName": "",
  "tenantCode": "",
  "raisedByEmployeeId": 0,
  "raisedByEmployeeName": "",
  "raisedByEmployeeEmail": "",
  "title": "",
  "description": "",
  "category": "",
  "priority": "",
  "status": "",
  "resolution": "",
  "resolvedByPlatformUserId": 0,
  "resolvedByPlatformUserName": "",
  "resolvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/platform/support-tickets/{id}`
- **Handler Method**: `getTicketById`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SupportTicketResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "ticketNumber": "",
  "tenantId": 0,
  "tenantCompanyName": "",
  "tenantCode": "",
  "raisedByEmployeeId": 0,
  "raisedByEmployeeName": "",
  "raisedByEmployeeEmail": "",
  "title": "",
  "description": "",
  "category": "",
  "priority": "",
  "status": "",
  "resolution": "",
  "resolvedByPlatformUserId": 0,
  "resolvedByPlatformUserName": "",
  "resolvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### PUT `/api/platform/support-tickets/{id}/status`
- **Handler Method**: `updateTicketStatus`
- **Description**: No description provided
- **Request Body Type**: `SupportTicketStatusUpdateRequest`
#### Request JSON Example:
```json
{
  "status": "",
  "resolution": ""
}
```
- **Response Type**: `SupportTicketResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "ticketNumber": "",
  "tenantId": 0,
  "tenantCompanyName": "",
  "tenantCode": "",
  "raisedByEmployeeId": 0,
  "raisedByEmployeeName": "",
  "raisedByEmployeeEmail": "",
  "title": "",
  "description": "",
  "category": "",
  "priority": "",
  "status": "",
  "resolution": "",
  "resolvedByPlatformUserId": 0,
  "resolvedByPlatformUserName": "",
  "resolvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

## PlatformTenantController

### GET `/api/platform/tenants`
- **Handler Method**: `getAllTenants`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<PlatformTenantResponseDTO>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
]
```

---

### POST `/api/platform/tenants`
- **Handler Method**: `createTenant`
- **Description**: No description provided
- **Request Body Type**: `TenantRegistrationRequest`
#### Request JSON Example:
```json
{
  "companyName": "",
  "adminEmail": "",
  "adminName": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planCode": ""
}
```
- **Response Type**: `PlatformTenantResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
```

---

### DELETE `/api/platform/tenants/{id}`
- **Handler Method**: `deleteTenant`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/platform/tenants/{id}`
- **Handler Method**: `getTenantById`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformTenantResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
```

---

### PUT `/api/platform/tenants/{id}`
- **Handler Method**: `updateTenant`
- **Description**: No description provided
- **Request Body Type**: `TenantUpdateRequest`
#### Request JSON Example:
```json
{
  "companyName": "",
  "adminEmail": "",
  "adminName": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": ""
}
```
- **Response Type**: `PlatformTenantResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
```

---

### PUT `/api/platform/tenants/{id}/activate`
- **Handler Method**: `activateTenant`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformTenantResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
```

---

### PUT `/api/platform/tenants/{id}/deactivate`
- **Handler Method**: `deactivateTenant`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformTenantResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
```

---

### PUT `/api/platform/tenants/{id}/plan-override`
- **Handler Method**: `overrideTenantPlan`
- **Description**: No description provided
- **Request Body Type**: `TenantPlanOverrideDTO`
#### Request JSON Example:
```json
{
  "planType": "",
  "maxEmployees": 0,
  "maxStorageMb": 0
}
```
- **Response Type**: `PlatformTenantResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
```

---

### PUT `/api/platform/tenants/{id}/suspend`
- **Handler Method**: `suspendTenant`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformTenantResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantCode": "",
  "companyName": "",
  "adminName": "",
  "adminEmail": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planType": "",
  "status": "UserStatus",
  "isActive": false,
  "maxEmployees": 0,
  "planStatus": "",
  "endsAt": "2026-06-17",
  "suspendedAt": "2026-06-17",
  "suspensionReason": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "dataStatus": "",
  "expiredAt": "2026-06-17",
  "archivedAt": "2026-06-17",
  "deletedAt": "2026-06-17",
  "deletedByAdminId": 0
}
```

---

## PlatformUserController

### GET `/api/platform/users`
- **Handler Method**: `getAllUsers`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PageResult<PlatformUserResponse>`
#### Response JSON Example:
```json
"PageResult<PlatformUserResponse>"
```

---

### POST `/api/platform/users`
- **Handler Method**: `createUser`
- **Description**: No description provided
- **Request Body Type**: `PlatformUserCreateRequest`
#### Request JSON Example:
```json
{
  "email": "",
  "fullName": "",
  "designation": "",
  "tenantId": 0,
  "roleIds": 0
}
```
- **Response Type**: `PlatformUserResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "email": "",
  "fullName": "",
  "designation": "",
  "status": "UserStatus",
  "isActive": false,
  "lastLoginAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "invitationLink": "",
  "invitationExpiryAt": "2026-06-17",
  "roles": [
  {
    "id": 0,
    "name": "",
    "description": "",
    "isSystemRole": false,
    "permissions": [ "PermissionInfo" ],
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "id": 0,
    "name": "",
    "description": ""
  }
  ],
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### GET `/api/platform/users/by-email/{email}`
- **Handler Method**: `getUserByEmail`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformUserResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "email": "",
  "fullName": "",
  "designation": "",
  "status": "UserStatus",
  "isActive": false,
  "lastLoginAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "invitationLink": "",
  "invitationExpiryAt": "2026-06-17",
  "roles": [
  {
    "id": 0,
    "name": "",
    "description": "",
    "isSystemRole": false,
    "permissions": [ "PermissionInfo" ],
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "id": 0,
    "name": "",
    "description": ""
  }
  ],
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### GET `/api/platform/users/me`
- **Handler Method**: `getCurrentUser`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformUserResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "email": "",
  "fullName": "",
  "designation": "",
  "status": "UserStatus",
  "isActive": false,
  "lastLoginAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "invitationLink": "",
  "invitationExpiryAt": "2026-06-17",
  "roles": [
  {
    "id": 0,
    "name": "",
    "description": "",
    "isSystemRole": false,
    "permissions": [ "PermissionInfo" ],
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "id": 0,
    "name": "",
    "description": ""
  }
  ],
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### GET `/api/platform/users/statistics`
- **Handler Method**: `getUserStatistics`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformUserStatistics`
#### Response JSON Example:
```json
{
  "totalUsers": 0,
  "activeUsers": 0,
  "inactiveUsers": 0,
  "pendingVerification": 0,
  "suspendedUsers": 0,
  "lockedUsers": 0
}
```

---

### DELETE `/api/platform/users/{id}`
- **Handler Method**: `deleteUser`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/platform/users/{id}`
- **Handler Method**: `getUserById`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PlatformUserResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "email": "",
  "fullName": "",
  "designation": "",
  "status": "UserStatus",
  "isActive": false,
  "lastLoginAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "invitationLink": "",
  "invitationExpiryAt": "2026-06-17",
  "roles": [
  {
    "id": 0,
    "name": "",
    "description": "",
    "isSystemRole": false,
    "permissions": [ "PermissionInfo" ],
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "id": 0,
    "name": "",
    "description": ""
  }
  ],
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### PUT `/api/platform/users/{id}`
- **Handler Method**: `updateUser`
- **Description**: No description provided
- **Request Body Type**: `PlatformUserUpdateRequest`
#### Request JSON Example:
```json
{
  "fullName": "",
  "designation": "",
  "email": ""
}
```
- **Response Type**: `PlatformUserResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "email": "",
  "fullName": "",
  "designation": "",
  "status": "UserStatus",
  "isActive": false,
  "lastLoginAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "invitationLink": "",
  "invitationExpiryAt": "2026-06-17",
  "roles": [
  {
    "id": 0,
    "name": "",
    "description": "",
    "isSystemRole": false,
    "permissions": [ "PermissionInfo" ],
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "id": 0,
    "name": "",
    "description": ""
  }
  ],
  "id": 0,
  "name": "",
  "description": ""
}
```

---

### PATCH `/api/platform/users/{id}/activate`
- **Handler Method**: `activateUserByAdmin`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### POST `/api/platform/users/{id}/resend-activation`
- **Handler Method**: `resendActivationEmail`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### POST `/api/platform/users/{id}/reset-password`
- **Handler Method**: `resetPassword`
- **Description**: No description provided
- **Request Body Type**: `ResetPasswordRequest`
#### Request JSON Example:
```json
{
  "newPassword": ""
}
```
- **Response Type**: `Void`

---

### PUT `/api/platform/users/{id}/roles`
- **Handler Method**: `updateUserRoles`
- **Description**: No description provided
- **Request Body Type**: `Set<Long>`
#### Request JSON Example:
```json
0
```
- **Response Type**: `Void`

---

### PATCH `/api/platform/users/{id}/suspend`
- **Handler Method**: `suspendUser`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

## ReimbursementClaimController

### POST `/api/payroll/reimbursements/submit`
- **Handler Method**: `submit`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ReimbursementClaim`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenant": {
    "id": 0,
    "tenantCode": "",
    "companyName": "",
    "status": "UserStatus",
    "isActive": false,
    "adminEmail": "",
    "adminName": "",
    "adminPhone": "",
    "officeAddress": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "subscriptionPlan": {
      "id": 0,
      "name": "",
      "code": "",
      "description": "",
      "price": 0,
      "validityMonths": 0,
      "currency": "",
      "maxUsers": 0,
      "maxEmployees": 0,
      "features": "",
      "isCustom": false,
      "isActive": false,
      "isPublic": false,
      "displayOrder": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "isActive": "",
      "deletedAt": "",
      "isActive": "",
      "false": "return",
      "ZERO": "",
      "price": "",
      "validityMonths": "",
      "isActive": "",
      "isPublic": "",
      "currency": "",
      "displayOrder": "",
      "isCustom": "",
      "price": "",
      "isActive": ""
    },
    "maxEmployees": 0,
    "planStatus": "PlanStatus",
    "endsAt": "2026-06-17",
    "suspendedAt": "2026-06-17",
    "suspensionReason": "",
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "deletedAt": "2026-06-17",
    "expiredAt": "2026-06-17",
    "archivedAt": "2026-06-17",
    "archiveWarningNotifiedAt": "2026-06-17",
    "finalReminderSentAt": "2026-06-17",
    "expirationNotifiedAt": "2026-06-17",
    "dataStatus": "TenantDataStatus",
    "deletedByAdminId": 0,
    "createdBy": 0,
    "updatedBy": 0,
    "version": 0,
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "planStatus": "",
    "suspensionReason": "",
    "dataStatus": "",
    "archivedAt": "",
    "expiredAt": "",
    "archivedAt": "",
    "archiveWarningNotifiedAt": "",
    "finalReminderSentAt": "",
    "expirationNotifiedAt": "",
    "dataStatus": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "deletedAt": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "status": "",
    "0": "return",
    "isActive": "return",
    "true": "return",
    "false": "return"
  },
  "employee": {
    "id": 0,
    "tenant": {
      "id": 0,
      "tenantCode": "",
      "companyName": "",
      "status": "UserStatus",
      "isActive": false,
      "adminEmail": "",
      "adminName": "",
      "adminPhone": "",
      "officeAddress": "",
      "city": "",
      "state": "IndianState",
      "stateText": "",
      "country": "",
      "subscriptionPlan": {
        "id": 0,
        "name": "",
        "code": "",
        "description": "",
        "price": 0,
        "validityMonths": 0,
        "currency": "",
        "maxUsers": 0,
        "maxEmployees": 0,
        "features": "",
        "isCustom": false,
        "isActive": false,
        "isPublic": false,
        "displayOrder": 0,
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "isActive": "",
        "deletedAt": "",
        "isActive": "",
        "false": "return",
        "ZERO": "",
        "price": "",
        "validityMonths": "",
        "isActive": "",
        "isPublic": "",
        "currency": "",
        "displayOrder": "",
        "isCustom": "",
        "price": "",
        "isActive": ""
      },
      "maxEmployees": 0,
      "planStatus": "PlanStatus",
      "endsAt": "2026-06-17",
      "suspendedAt": "2026-06-17",
      "suspensionReason": "",
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "expiredAt": "2026-06-17",
      "archivedAt": "2026-06-17",
      "archiveWarningNotifiedAt": "2026-06-17",
      "finalReminderSentAt": "2026-06-17",
      "expirationNotifiedAt": "2026-06-17",
      "dataStatus": "TenantDataStatus",
      "deletedByAdminId": 0,
      "createdBy": 0,
      "updatedBy": 0,
      "version": 0,
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "planStatus": "",
      "suspensionReason": "",
      "dataStatus": "",
      "archivedAt": "",
      "expiredAt": "",
      "archivedAt": "",
      "archiveWarningNotifiedAt": "",
      "finalReminderSentAt": "",
      "expirationNotifiedAt": "",
      "dataStatus": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "deletedAt": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "status": "",
      "0": "return",
      "isActive": "return",
      "true": "return",
      "false": "return"
    },
    "tenantId": 0,
    "employeeCode": "",
    "email": "",
    "passwordHash": "",
    "isActive": false,
    "rolesVersion": 0,
    "cachedAuthorities": [ "? extends GrantedAuthority" ],
    "cachedRolesVersion": 0,
    "lastLoginAt": "2026-06-17",
    "lastLoginIp": "",
    "mustChangePassword": false,
    "firstName": "",
    "lastName": "",
    "phone": "",
    "dateOfBirth": "2026-06-17",
    "gender": "Gender",
    "maritalStatus": "MaritalStatus",
    "bloodGroup": "BloodGroup",
    "nationality": "",
    "personalEmail": "",
    "department": {
      "id": 0,
      "tenant": {
        "id": 0,
        "tenantCode": "",
        "companyName": "",
        "status": "UserStatus",
        "isActive": false,
        "adminEmail": "",
        "adminName": "",
        "adminPhone": "",
        "officeAddress": "",
        "city": "",
        "state": "IndianState",
        "stateText": "",
        "country": "",
        "subscriptionPlan": {
          "id": 0,
          "name": "",
          "code": "",
          "description": "",
          "price": 0,
          "validityMonths": 0,
          "currency": "",
          "maxUsers": 0,
          "maxEmployees": 0,
          "features": "",
          "isCustom": false,
          "isActive": false,
          "isPublic": false,
          "displayOrder": 0,
          "createdAt": "2026-06-17",
          "updatedAt": "2026-06-17",
          "deletedAt": "2026-06-17",
          "isActive": "",
          "deletedAt": "",
          "isActive": "",
          "false": "return",
          "ZERO": "",
          "price": "",
          "validityMonths": "",
          "isActive": "",
          "isPublic": "",
          "currency": "",
          "displayOrder": "",
          "isCustom": "",
          "price": "",
          "isActive": ""
        },
        "maxEmployees": 0,
        "planStatus": "PlanStatus",
        "endsAt": "2026-06-17",
        "suspendedAt": "2026-06-17",
        "suspensionReason": "",
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "expiredAt": "2026-06-17",
        "archivedAt": "2026-06-17",
        "archiveWarningNotifiedAt": "2026-06-17",
        "finalReminderSentAt": "2026-06-17",
        "expirationNotifiedAt": "2026-06-17",
        "dataStatus": "TenantDataStatus",
        "deletedByAdminId": 0,
        "createdBy": 0,
        "updatedBy": 0,
        "version": 0,
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "planStatus": "",
        "suspensionReason": "",
        "dataStatus": "",
        "archivedAt": "",
        "expiredAt": "",
        "archivedAt": "",
        "archiveWarningNotifiedAt": "",
        "finalReminderSentAt": "",
        "expirationNotifiedAt": "",
        "dataStatus": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "deletedAt": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "status": "",
        "0": "return",
        "isActive": "return",
        "true": "return",
        "false": "return"
      },
      "name": "",
      "code": "",
      "description": "",
      "isActive": false,
      "isDeleted": false,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17"
    },
    "shift": {
      "id": 0,
      "tenantId": 0,
      "shiftName": "",
      "shiftCode": "",
      "shiftDescription": "",
      "startTime": "LocalTime",
      "endTime": "LocalTime",
      "totalHours": 0,
      "breakDurationMinutes": 0,
      "minBreakMinutes": 0,
      "maxBreakMinutes": 0,
      "lateGraceMinutes": 0,
      "earlyExitGraceMinutes": 0,
      "checkinBufferBefore": 0,
      "checkoutBufferAfter": 0,
      "fullDayHours": 0,
      "halfDayHours": 0,
      "quarterDayHours": 0,
      "allowOvertime": false,
      "overtimeMultiplier": 0,
      "overtimeThresholdMinutes": 0,
      "maxOvertimeHoursPerDay": 0,
      "weeklyOffs": "",
      "alternateWeekOff": false,
      "effectiveFrom": "2026-06-17",
      "effectiveTo": "2026-06-17",
      "isActive": false,
      "isDefault": false,
      "isDeleted": false,
      "createdBy": 0,
      "updatedBy": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "startTime": "return",
      "endTime": "return",
      "startTime": "return",
      "endTime": "return"
    },
    "position": "",
    "manager": "[Recursive Reference to Employee]",
    "employmentType": "EmploymentType",
    "workLocation": "",
    "workState": "IndianState",
    "workStateText": "",
    "workCountry": "",
    "hireDate": "2026-06-17",
    "confirmationDate": "2026-06-17",
    "resignationDate": "2026-06-17",
    "lastWorkingDate": "2026-06-17",
    "status": "EmployeeStatus",
    "address": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "postalCode": "",
    "emergencyContactName": "",
    "emergencyContactPhone": "",
    "emergencyContactRelation": "",
    "emergencyContactEmail": "",
    "secondaryEmergencyName": "",
    "secondaryEmergencyPhone": "",
    "profilePictureUrl": "",
    "weekendConfig": "WeekendConfig",
    "customWeekendDays": "",
    "linkedinUrl": "",
    "githubUrl": "",
    "twitterUrl": "",
    "createdBy": 0,
    "updatedBy": 0,
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "rolesVersion": "return",
    "rolesVersion": "",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "return",
    "cachedRolesVersion": "",
    "tenantId": "return",
    "0": "return",
    "0": "return",
    "isActive": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "true": "return",
    "isActive": "",
    "passwordHash": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "status": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "isActive": "",
    "status": "",
    "status": "",
    "confirmationDate": "return",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "authorities": "return",
    "passwordHash": "return",
    "email": "return",
    "true": "return",
    "true": "return",
    "mustChangePassword": "return !",
    "status": "return isActive &&",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return"
  },
  "category": "ReimbursementCategory",
  "claimAmount": 0,
  "attachmentUrl": "",
  "status": "ReimbursementStatus",
  "targetMonth": 0,
  "targetYear": 0,
  "submittedAt": "2026-06-17",
  "processedAt": "2026-06-17"
}
```

---

### POST `/api/payroll/reimbursements/{claimId}/approve`
- **Handler Method**: `approve`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ReimbursementClaim`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenant": {
    "id": 0,
    "tenantCode": "",
    "companyName": "",
    "status": "UserStatus",
    "isActive": false,
    "adminEmail": "",
    "adminName": "",
    "adminPhone": "",
    "officeAddress": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "subscriptionPlan": {
      "id": 0,
      "name": "",
      "code": "",
      "description": "",
      "price": 0,
      "validityMonths": 0,
      "currency": "",
      "maxUsers": 0,
      "maxEmployees": 0,
      "features": "",
      "isCustom": false,
      "isActive": false,
      "isPublic": false,
      "displayOrder": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "isActive": "",
      "deletedAt": "",
      "isActive": "",
      "false": "return",
      "ZERO": "",
      "price": "",
      "validityMonths": "",
      "isActive": "",
      "isPublic": "",
      "currency": "",
      "displayOrder": "",
      "isCustom": "",
      "price": "",
      "isActive": ""
    },
    "maxEmployees": 0,
    "planStatus": "PlanStatus",
    "endsAt": "2026-06-17",
    "suspendedAt": "2026-06-17",
    "suspensionReason": "",
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "deletedAt": "2026-06-17",
    "expiredAt": "2026-06-17",
    "archivedAt": "2026-06-17",
    "archiveWarningNotifiedAt": "2026-06-17",
    "finalReminderSentAt": "2026-06-17",
    "expirationNotifiedAt": "2026-06-17",
    "dataStatus": "TenantDataStatus",
    "deletedByAdminId": 0,
    "createdBy": 0,
    "updatedBy": 0,
    "version": 0,
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "planStatus": "",
    "suspensionReason": "",
    "dataStatus": "",
    "archivedAt": "",
    "expiredAt": "",
    "archivedAt": "",
    "archiveWarningNotifiedAt": "",
    "finalReminderSentAt": "",
    "expirationNotifiedAt": "",
    "dataStatus": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "deletedAt": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "status": "",
    "0": "return",
    "isActive": "return",
    "true": "return",
    "false": "return"
  },
  "employee": {
    "id": 0,
    "tenant": {
      "id": 0,
      "tenantCode": "",
      "companyName": "",
      "status": "UserStatus",
      "isActive": false,
      "adminEmail": "",
      "adminName": "",
      "adminPhone": "",
      "officeAddress": "",
      "city": "",
      "state": "IndianState",
      "stateText": "",
      "country": "",
      "subscriptionPlan": {
        "id": 0,
        "name": "",
        "code": "",
        "description": "",
        "price": 0,
        "validityMonths": 0,
        "currency": "",
        "maxUsers": 0,
        "maxEmployees": 0,
        "features": "",
        "isCustom": false,
        "isActive": false,
        "isPublic": false,
        "displayOrder": 0,
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "isActive": "",
        "deletedAt": "",
        "isActive": "",
        "false": "return",
        "ZERO": "",
        "price": "",
        "validityMonths": "",
        "isActive": "",
        "isPublic": "",
        "currency": "",
        "displayOrder": "",
        "isCustom": "",
        "price": "",
        "isActive": ""
      },
      "maxEmployees": 0,
      "planStatus": "PlanStatus",
      "endsAt": "2026-06-17",
      "suspendedAt": "2026-06-17",
      "suspensionReason": "",
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "expiredAt": "2026-06-17",
      "archivedAt": "2026-06-17",
      "archiveWarningNotifiedAt": "2026-06-17",
      "finalReminderSentAt": "2026-06-17",
      "expirationNotifiedAt": "2026-06-17",
      "dataStatus": "TenantDataStatus",
      "deletedByAdminId": 0,
      "createdBy": 0,
      "updatedBy": 0,
      "version": 0,
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "planStatus": "",
      "suspensionReason": "",
      "dataStatus": "",
      "archivedAt": "",
      "expiredAt": "",
      "archivedAt": "",
      "archiveWarningNotifiedAt": "",
      "finalReminderSentAt": "",
      "expirationNotifiedAt": "",
      "dataStatus": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "deletedAt": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "status": "",
      "0": "return",
      "isActive": "return",
      "true": "return",
      "false": "return"
    },
    "tenantId": 0,
    "employeeCode": "",
    "email": "",
    "passwordHash": "",
    "isActive": false,
    "rolesVersion": 0,
    "cachedAuthorities": [ "? extends GrantedAuthority" ],
    "cachedRolesVersion": 0,
    "lastLoginAt": "2026-06-17",
    "lastLoginIp": "",
    "mustChangePassword": false,
    "firstName": "",
    "lastName": "",
    "phone": "",
    "dateOfBirth": "2026-06-17",
    "gender": "Gender",
    "maritalStatus": "MaritalStatus",
    "bloodGroup": "BloodGroup",
    "nationality": "",
    "personalEmail": "",
    "department": {
      "id": 0,
      "tenant": {
        "id": 0,
        "tenantCode": "",
        "companyName": "",
        "status": "UserStatus",
        "isActive": false,
        "adminEmail": "",
        "adminName": "",
        "adminPhone": "",
        "officeAddress": "",
        "city": "",
        "state": "IndianState",
        "stateText": "",
        "country": "",
        "subscriptionPlan": {
          "id": 0,
          "name": "",
          "code": "",
          "description": "",
          "price": 0,
          "validityMonths": 0,
          "currency": "",
          "maxUsers": 0,
          "maxEmployees": 0,
          "features": "",
          "isCustom": false,
          "isActive": false,
          "isPublic": false,
          "displayOrder": 0,
          "createdAt": "2026-06-17",
          "updatedAt": "2026-06-17",
          "deletedAt": "2026-06-17",
          "isActive": "",
          "deletedAt": "",
          "isActive": "",
          "false": "return",
          "ZERO": "",
          "price": "",
          "validityMonths": "",
          "isActive": "",
          "isPublic": "",
          "currency": "",
          "displayOrder": "",
          "isCustom": "",
          "price": "",
          "isActive": ""
        },
        "maxEmployees": 0,
        "planStatus": "PlanStatus",
        "endsAt": "2026-06-17",
        "suspendedAt": "2026-06-17",
        "suspensionReason": "",
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "expiredAt": "2026-06-17",
        "archivedAt": "2026-06-17",
        "archiveWarningNotifiedAt": "2026-06-17",
        "finalReminderSentAt": "2026-06-17",
        "expirationNotifiedAt": "2026-06-17",
        "dataStatus": "TenantDataStatus",
        "deletedByAdminId": 0,
        "createdBy": 0,
        "updatedBy": 0,
        "version": 0,
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "planStatus": "",
        "suspensionReason": "",
        "dataStatus": "",
        "archivedAt": "",
        "expiredAt": "",
        "archivedAt": "",
        "archiveWarningNotifiedAt": "",
        "finalReminderSentAt": "",
        "expirationNotifiedAt": "",
        "dataStatus": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "deletedAt": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "status": "",
        "0": "return",
        "isActive": "return",
        "true": "return",
        "false": "return"
      },
      "name": "",
      "code": "",
      "description": "",
      "isActive": false,
      "isDeleted": false,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17"
    },
    "shift": {
      "id": 0,
      "tenantId": 0,
      "shiftName": "",
      "shiftCode": "",
      "shiftDescription": "",
      "startTime": "LocalTime",
      "endTime": "LocalTime",
      "totalHours": 0,
      "breakDurationMinutes": 0,
      "minBreakMinutes": 0,
      "maxBreakMinutes": 0,
      "lateGraceMinutes": 0,
      "earlyExitGraceMinutes": 0,
      "checkinBufferBefore": 0,
      "checkoutBufferAfter": 0,
      "fullDayHours": 0,
      "halfDayHours": 0,
      "quarterDayHours": 0,
      "allowOvertime": false,
      "overtimeMultiplier": 0,
      "overtimeThresholdMinutes": 0,
      "maxOvertimeHoursPerDay": 0,
      "weeklyOffs": "",
      "alternateWeekOff": false,
      "effectiveFrom": "2026-06-17",
      "effectiveTo": "2026-06-17",
      "isActive": false,
      "isDefault": false,
      "isDeleted": false,
      "createdBy": 0,
      "updatedBy": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "startTime": "return",
      "endTime": "return",
      "startTime": "return",
      "endTime": "return"
    },
    "position": "",
    "manager": "[Recursive Reference to Employee]",
    "employmentType": "EmploymentType",
    "workLocation": "",
    "workState": "IndianState",
    "workStateText": "",
    "workCountry": "",
    "hireDate": "2026-06-17",
    "confirmationDate": "2026-06-17",
    "resignationDate": "2026-06-17",
    "lastWorkingDate": "2026-06-17",
    "status": "EmployeeStatus",
    "address": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "postalCode": "",
    "emergencyContactName": "",
    "emergencyContactPhone": "",
    "emergencyContactRelation": "",
    "emergencyContactEmail": "",
    "secondaryEmergencyName": "",
    "secondaryEmergencyPhone": "",
    "profilePictureUrl": "",
    "weekendConfig": "WeekendConfig",
    "customWeekendDays": "",
    "linkedinUrl": "",
    "githubUrl": "",
    "twitterUrl": "",
    "createdBy": 0,
    "updatedBy": 0,
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "rolesVersion": "return",
    "rolesVersion": "",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "return",
    "cachedRolesVersion": "",
    "tenantId": "return",
    "0": "return",
    "0": "return",
    "isActive": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "true": "return",
    "isActive": "",
    "passwordHash": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "status": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "isActive": "",
    "status": "",
    "status": "",
    "confirmationDate": "return",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "authorities": "return",
    "passwordHash": "return",
    "email": "return",
    "true": "return",
    "true": "return",
    "mustChangePassword": "return !",
    "status": "return isActive &&",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return"
  },
  "category": "ReimbursementCategory",
  "claimAmount": 0,
  "attachmentUrl": "",
  "status": "ReimbursementStatus",
  "targetMonth": 0,
  "targetYear": 0,
  "submittedAt": "2026-06-17",
  "processedAt": "2026-06-17"
}
```

---

### POST `/api/payroll/reimbursements/{claimId}/reject`
- **Handler Method**: `reject`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ReimbursementClaim`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenant": {
    "id": 0,
    "tenantCode": "",
    "companyName": "",
    "status": "UserStatus",
    "isActive": false,
    "adminEmail": "",
    "adminName": "",
    "adminPhone": "",
    "officeAddress": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "subscriptionPlan": {
      "id": 0,
      "name": "",
      "code": "",
      "description": "",
      "price": 0,
      "validityMonths": 0,
      "currency": "",
      "maxUsers": 0,
      "maxEmployees": 0,
      "features": "",
      "isCustom": false,
      "isActive": false,
      "isPublic": false,
      "displayOrder": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "isActive": "",
      "deletedAt": "",
      "isActive": "",
      "false": "return",
      "ZERO": "",
      "price": "",
      "validityMonths": "",
      "isActive": "",
      "isPublic": "",
      "currency": "",
      "displayOrder": "",
      "isCustom": "",
      "price": "",
      "isActive": ""
    },
    "maxEmployees": 0,
    "planStatus": "PlanStatus",
    "endsAt": "2026-06-17",
    "suspendedAt": "2026-06-17",
    "suspensionReason": "",
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "deletedAt": "2026-06-17",
    "expiredAt": "2026-06-17",
    "archivedAt": "2026-06-17",
    "archiveWarningNotifiedAt": "2026-06-17",
    "finalReminderSentAt": "2026-06-17",
    "expirationNotifiedAt": "2026-06-17",
    "dataStatus": "TenantDataStatus",
    "deletedByAdminId": 0,
    "createdBy": 0,
    "updatedBy": 0,
    "version": 0,
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "planStatus": "",
    "suspensionReason": "",
    "dataStatus": "",
    "archivedAt": "",
    "expiredAt": "",
    "archivedAt": "",
    "archiveWarningNotifiedAt": "",
    "finalReminderSentAt": "",
    "expirationNotifiedAt": "",
    "dataStatus": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "isActive": "",
    "deletedAt": "",
    "suspendedAt": "",
    "suspensionReason": "",
    "status": "",
    "isActive": "",
    "status": "",
    "status": "",
    "0": "return",
    "isActive": "return",
    "true": "return",
    "false": "return"
  },
  "employee": {
    "id": 0,
    "tenant": {
      "id": 0,
      "tenantCode": "",
      "companyName": "",
      "status": "UserStatus",
      "isActive": false,
      "adminEmail": "",
      "adminName": "",
      "adminPhone": "",
      "officeAddress": "",
      "city": "",
      "state": "IndianState",
      "stateText": "",
      "country": "",
      "subscriptionPlan": {
        "id": 0,
        "name": "",
        "code": "",
        "description": "",
        "price": 0,
        "validityMonths": 0,
        "currency": "",
        "maxUsers": 0,
        "maxEmployees": 0,
        "features": "",
        "isCustom": false,
        "isActive": false,
        "isPublic": false,
        "displayOrder": 0,
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "isActive": "",
        "deletedAt": "",
        "isActive": "",
        "false": "return",
        "ZERO": "",
        "price": "",
        "validityMonths": "",
        "isActive": "",
        "isPublic": "",
        "currency": "",
        "displayOrder": "",
        "isCustom": "",
        "price": "",
        "isActive": ""
      },
      "maxEmployees": 0,
      "planStatus": "PlanStatus",
      "endsAt": "2026-06-17",
      "suspendedAt": "2026-06-17",
      "suspensionReason": "",
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "deletedAt": "2026-06-17",
      "expiredAt": "2026-06-17",
      "archivedAt": "2026-06-17",
      "archiveWarningNotifiedAt": "2026-06-17",
      "finalReminderSentAt": "2026-06-17",
      "expirationNotifiedAt": "2026-06-17",
      "dataStatus": "TenantDataStatus",
      "deletedByAdminId": 0,
      "createdBy": 0,
      "updatedBy": 0,
      "version": 0,
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "planStatus": "",
      "suspensionReason": "",
      "dataStatus": "",
      "archivedAt": "",
      "expiredAt": "",
      "archivedAt": "",
      "archiveWarningNotifiedAt": "",
      "finalReminderSentAt": "",
      "expirationNotifiedAt": "",
      "dataStatus": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "isActive": "",
      "deletedAt": "",
      "suspendedAt": "",
      "suspensionReason": "",
      "status": "",
      "isActive": "",
      "status": "",
      "status": "",
      "0": "return",
      "isActive": "return",
      "true": "return",
      "false": "return"
    },
    "tenantId": 0,
    "employeeCode": "",
    "email": "",
    "passwordHash": "",
    "isActive": false,
    "rolesVersion": 0,
    "cachedAuthorities": [ "? extends GrantedAuthority" ],
    "cachedRolesVersion": 0,
    "lastLoginAt": "2026-06-17",
    "lastLoginIp": "",
    "mustChangePassword": false,
    "firstName": "",
    "lastName": "",
    "phone": "",
    "dateOfBirth": "2026-06-17",
    "gender": "Gender",
    "maritalStatus": "MaritalStatus",
    "bloodGroup": "BloodGroup",
    "nationality": "",
    "personalEmail": "",
    "department": {
      "id": 0,
      "tenant": {
        "id": 0,
        "tenantCode": "",
        "companyName": "",
        "status": "UserStatus",
        "isActive": false,
        "adminEmail": "",
        "adminName": "",
        "adminPhone": "",
        "officeAddress": "",
        "city": "",
        "state": "IndianState",
        "stateText": "",
        "country": "",
        "subscriptionPlan": {
          "id": 0,
          "name": "",
          "code": "",
          "description": "",
          "price": 0,
          "validityMonths": 0,
          "currency": "",
          "maxUsers": 0,
          "maxEmployees": 0,
          "features": "",
          "isCustom": false,
          "isActive": false,
          "isPublic": false,
          "displayOrder": 0,
          "createdAt": "2026-06-17",
          "updatedAt": "2026-06-17",
          "deletedAt": "2026-06-17",
          "isActive": "",
          "deletedAt": "",
          "isActive": "",
          "false": "return",
          "ZERO": "",
          "price": "",
          "validityMonths": "",
          "isActive": "",
          "isPublic": "",
          "currency": "",
          "displayOrder": "",
          "isCustom": "",
          "price": "",
          "isActive": ""
        },
        "maxEmployees": 0,
        "planStatus": "PlanStatus",
        "endsAt": "2026-06-17",
        "suspendedAt": "2026-06-17",
        "suspensionReason": "",
        "createdAt": "2026-06-17",
        "updatedAt": "2026-06-17",
        "deletedAt": "2026-06-17",
        "expiredAt": "2026-06-17",
        "archivedAt": "2026-06-17",
        "archiveWarningNotifiedAt": "2026-06-17",
        "finalReminderSentAt": "2026-06-17",
        "expirationNotifiedAt": "2026-06-17",
        "dataStatus": "TenantDataStatus",
        "deletedByAdminId": 0,
        "createdBy": 0,
        "updatedBy": 0,
        "version": 0,
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "planStatus": "",
        "suspensionReason": "",
        "dataStatus": "",
        "archivedAt": "",
        "expiredAt": "",
        "archivedAt": "",
        "archiveWarningNotifiedAt": "",
        "finalReminderSentAt": "",
        "expirationNotifiedAt": "",
        "dataStatus": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "isActive": "",
        "deletedAt": "",
        "suspendedAt": "",
        "suspensionReason": "",
        "status": "",
        "isActive": "",
        "status": "",
        "status": "",
        "0": "return",
        "isActive": "return",
        "true": "return",
        "false": "return"
      },
      "name": "",
      "code": "",
      "description": "",
      "isActive": false,
      "isDeleted": false,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17"
    },
    "shift": {
      "id": 0,
      "tenantId": 0,
      "shiftName": "",
      "shiftCode": "",
      "shiftDescription": "",
      "startTime": "LocalTime",
      "endTime": "LocalTime",
      "totalHours": 0,
      "breakDurationMinutes": 0,
      "minBreakMinutes": 0,
      "maxBreakMinutes": 0,
      "lateGraceMinutes": 0,
      "earlyExitGraceMinutes": 0,
      "checkinBufferBefore": 0,
      "checkoutBufferAfter": 0,
      "fullDayHours": 0,
      "halfDayHours": 0,
      "quarterDayHours": 0,
      "allowOvertime": false,
      "overtimeMultiplier": 0,
      "overtimeThresholdMinutes": 0,
      "maxOvertimeHoursPerDay": 0,
      "weeklyOffs": "",
      "alternateWeekOff": false,
      "effectiveFrom": "2026-06-17",
      "effectiveTo": "2026-06-17",
      "isActive": false,
      "isDefault": false,
      "isDeleted": false,
      "createdBy": 0,
      "updatedBy": 0,
      "createdAt": "2026-06-17",
      "updatedAt": "2026-06-17",
      "startTime": "return",
      "endTime": "return",
      "startTime": "return",
      "endTime": "return"
    },
    "position": "",
    "manager": "[Recursive Reference to Employee]",
    "employmentType": "EmploymentType",
    "workLocation": "",
    "workState": "IndianState",
    "workStateText": "",
    "workCountry": "",
    "hireDate": "2026-06-17",
    "confirmationDate": "2026-06-17",
    "resignationDate": "2026-06-17",
    "lastWorkingDate": "2026-06-17",
    "status": "EmployeeStatus",
    "address": "",
    "city": "",
    "state": "IndianState",
    "stateText": "",
    "country": "",
    "postalCode": "",
    "emergencyContactName": "",
    "emergencyContactPhone": "",
    "emergencyContactRelation": "",
    "emergencyContactEmail": "",
    "secondaryEmergencyName": "",
    "secondaryEmergencyPhone": "",
    "profilePictureUrl": "",
    "weekendConfig": "WeekendConfig",
    "customWeekendDays": "",
    "linkedinUrl": "",
    "githubUrl": "",
    "twitterUrl": "",
    "createdBy": 0,
    "updatedBy": 0,
    "createdAt": "2026-06-17",
    "updatedAt": "2026-06-17",
    "rolesVersion": "return",
    "rolesVersion": "",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "return",
    "cachedRolesVersion": "",
    "tenantId": "return",
    "0": "return",
    "0": "return",
    "isActive": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "status": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "true": "return",
    "isActive": "",
    "passwordHash": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "status": "",
    "isActive": "",
    "status": "",
    "resignationDate": "",
    "lastWorkingDate": "",
    "isActive": "",
    "status": "",
    "status": "",
    "confirmationDate": "return",
    "cachedAuthorities": "return",
    "cachedAuthorities": "",
    "cachedRolesVersion": "",
    "authorities": "return",
    "passwordHash": "return",
    "email": "return",
    "true": "return",
    "true": "return",
    "mustChangePassword": "return !",
    "status": "return isActive &&",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return",
    "false": "return"
  },
  "category": "ReimbursementCategory",
  "claimAmount": 0,
  "attachmentUrl": "",
  "status": "ReimbursementStatus",
  "targetMonth": 0,
  "targetYear": 0,
  "submittedAt": "2026-06-17",
  "processedAt": "2026-06-17"
}
```

---

## ShiftConfigurationController

### POST `/api/shift-configurations`
- **Handler Method**: `createShift`
- **Description**: No description provided
- **Request Body Type**: `ShiftConfigurationRequestDTO`
#### Request JSON Example:
```json
{
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "fullDayHours": "return",
  "halfDayHours": "return",
  "minBreakMinutes": "return",
  "true": "return"
}
```
- **Response Type**: `ShiftConfigurationDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "totalHours": 0,
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "isDefault": false,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "weeklyOffs": "",
  "hours": 0,
  "minutes": 0,
  "true": "return"
}
```

---

### GET `/api/shift-configurations/active`
- **Handler Method**: `getAllActiveShifts`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ShiftConfigurationSummaryDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftTimeDisplay": "",
  "shiftDurationDisplay": "",
  "isActive": false,
  "isDefault": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/shift-configurations/all`
- **Handler Method**: `getAllShifts`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ShiftConfigurationSummaryDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftTimeDisplay": "",
  "shiftDurationDisplay": "",
  "isActive": false,
  "isDefault": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### GET `/api/shift-configurations/attendance-status`
- **Handler Method**: `getAttendanceStatus`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `String`
#### Response JSON Example:
```json
""
```

---

### GET `/api/shift-configurations/code/{shiftCode}`
- **Handler Method**: `getShiftByCode`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ShiftConfigurationDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "totalHours": 0,
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "isDefault": false,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "weeklyOffs": "",
  "hours": 0,
  "minutes": 0,
  "true": "return"
}
```

---

### GET `/api/shift-configurations/early-exit-minutes`
- **Handler Method**: `calculateEarlyExitMinutes`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Integer`
#### Response JSON Example:
```json
0
```

---

### GET `/api/shift-configurations/effective`
- **Handler Method**: `getEffectiveShift`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ShiftConfigurationDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "totalHours": 0,
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "isDefault": false,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "weeklyOffs": "",
  "hours": 0,
  "minutes": 0,
  "true": "return"
}
```

---

### GET `/api/shift-configurations/expected-hours`
- **Handler Method**: `getExpectedWorkingHours`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Double`
#### Response JSON Example:
```json
0
```

---

### GET `/api/shift-configurations/is-weekoff`
- **Handler Method**: `isWeekOff`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Boolean`
#### Response JSON Example:
```json
false
```

---

### GET `/api/shift-configurations/late-minutes`
- **Handler Method**: `calculateLateMinutes`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Integer`
#### Response JSON Example:
```json
0
```

---

### GET `/api/shift-configurations/my-shift`
- **Handler Method**: `getMyShift`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ShiftConfigurationDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "totalHours": 0,
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "isDefault": false,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "weeklyOffs": "",
  "hours": 0,
  "minutes": 0,
  "true": "return"
}
```

---

### GET `/api/shift-configurations/overtime`
- **Handler Method**: `calculateOvertime`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Double`
#### Response JSON Example:
```json
0
```

---

### GET `/api/shift-configurations/validate-checkin`
- **Handler Method**: `isValidCheckinTime`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Boolean`
#### Response JSON Example:
```json
false
```

---

### GET `/api/shift-configurations/working-hours`
- **Handler Method**: `calculateWorkingHours`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Double`
#### Response JSON Example:
```json
0
```

---

### DELETE `/api/shift-configurations/{id}`
- **Handler Method**: `softDeleteShift`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/shift-configurations/{id}`
- **Handler Method**: `getShiftById`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `ShiftConfigurationDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "totalHours": 0,
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "isDefault": false,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "weeklyOffs": "",
  "hours": 0,
  "minutes": 0,
  "true": "return"
}
```

---

### PUT `/api/shift-configurations/{id}`
- **Handler Method**: `updateShift`
- **Description**: No description provided
- **Request Body Type**: `ShiftConfigurationRequestDTO`
#### Request JSON Example:
```json
{
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "fullDayHours": "return",
  "halfDayHours": "return",
  "minBreakMinutes": "return",
  "true": "return"
}
```
- **Response Type**: `ShiftConfigurationDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "tenantId": 0,
  "shiftName": "",
  "shiftCode": "",
  "shiftDescription": "",
  "startTime": "LocalTime",
  "endTime": "LocalTime",
  "totalHours": 0,
  "breakDurationMinutes": 0,
  "minBreakMinutes": 0,
  "maxBreakMinutes": 0,
  "lateGraceMinutes": 0,
  "earlyExitGraceMinutes": 0,
  "checkinBufferBefore": 0,
  "checkoutBufferAfter": 0,
  "fullDayHours": 0,
  "halfDayHours": 0,
  "quarterDayHours": 0,
  "allowOvertime": false,
  "overtimeMultiplier": 0,
  "overtimeThresholdMinutes": 0,
  "maxOvertimeHoursPerDay": 0,
  "weeklyOffs": "",
  "alternateWeekOff": false,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "isDefault": false,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "createdBy": 0,
  "updatedBy": 0,
  "weeklyOffs": "",
  "hours": 0,
  "minutes": 0,
  "true": "return"
}
```

---

### POST `/api/shift-configurations/{id}/activate`
- **Handler Method**: `activateShift`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### POST `/api/shift-configurations/{id}/deactivate`
- **Handler Method**: `deactivateShift`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### DELETE `/api/shift-configurations/{id}/hard`
- **Handler Method**: `hardDeleteShift`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### POST `/api/shift-configurations/{id}/set-default`
- **Handler Method**: `setDefaultShift`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

## TenantAuthController

### POST `/api/tenant/auth/activate`
- **Handler Method**: `activateUser`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### POST `/api/tenant/auth/forgot-password`
- **Handler Method**: `forgotPassword`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, String>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### POST `/api/tenant/auth/login`
- **Handler Method**: `login`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `LoginResponse`
#### Response JSON Example:
```json
{
  "accessToken": "",
  "refreshToken": "",
  "tokenType": "",
  "expiresIn": 0,
  "email": "",
  "fullName": ""
}
```

---

### POST `/api/tenant/auth/logout`
- **Handler Method**: `logout`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, String>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/tenant/auth/me`
- **Handler Method**: `getCurrentUser`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### POST `/api/tenant/auth/refresh`
- **Handler Method**: `refreshToken`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `LoginResponse`
#### Response JSON Example:
```json
{
  "accessToken": "",
  "refreshToken": "",
  "tokenType": "",
  "expiresIn": 0,
  "email": "",
  "fullName": ""
}
```

---

### POST `/api/tenant/auth/reset-password`
- **Handler Method**: `resetPassword`
- **Description**: No description provided
- **Request Body Type**: `SetPasswordRequest`
#### Request JSON Example:
```json
{
  "token": "",
  "newPassword": "",
  "confirmPassword": ""
}
```
- **Response Type**: `Map<String, String>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/tenant/auth/test-auth`
- **Handler Method**: `testAuth`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/tenant/auth/verify-token`
- **Handler Method**: `verifyToken`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

## TenantPayrollConfigurationController

### GET `/api/payroll/config/tenants/{tenantId}/components`
- **Handler Method**: `getComponents`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SalaryComponentDefinitionResponse[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "defaultValue": 0,
  "formulaExpression": "",
  "evaluationOrder": 0,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "isAllowedByTenant": false,
  "minValue": 0,
  "maxValue": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false
}
]
```

---

### POST `/api/payroll/config/tenants/{tenantId}/components`
- **Handler Method**: `createComponent`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SalaryComponentDefinitionResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "defaultValue": 0,
  "formulaExpression": "",
  "evaluationOrder": 0,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "isAllowedByTenant": false,
  "minValue": 0,
  "maxValue": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false
}
```

---

### GET `/api/payroll/config/tenants/{tenantId}/components/allowed`
- **Handler Method**: `getAllowedComponents`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SalaryComponentDefinitionResponse[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "defaultValue": 0,
  "formulaExpression": "",
  "evaluationOrder": 0,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "isAllowedByTenant": false,
  "minValue": 0,
  "maxValue": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false
}
]
```

---

### DELETE `/api/payroll/config/tenants/{tenantId}/components/{componentId}`
- **Handler Method**: `deleteComponent`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### PATCH `/api/payroll/config/tenants/{tenantId}/components/{componentId}/allowed`
- **Handler Method**: `toggleComponentAllowed`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/payroll/config/tenants/{tenantId}/employees/profiles`
- **Handler Method**: `getAllEmployeeProfilesForTenant`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSalaryProfileResponse[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "employeeName": "",
  "version": 0,
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "lopBasisOverride": "LopBasis",
  "workingDaysOverride": 0,
  "promotionReason": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "componentOverrides": [
  {
    "componentCode": "",
    "overrideType": "",
    "overrideValue": 0,
    "overrideFormula": "",
    "isEnabled": false
  }
  ]
}
]
```

---

### POST `/api/payroll/config/tenants/{tenantId}/employees/sync-all`
- **Handler Method**: `syncAllEmployeeProfiles`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/payroll/config/tenants/{tenantId}/employees/{employeeId}/history`
- **Handler Method**: `getEmployeeSalaryHistory`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSalaryProfileHistory[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "profileId": "UUID",
  "employeeId": 0,
  "version": 0,
  "monthlyCtc": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "changeReason": "",
  "changedBy": 0,
  "changedAt": "2026-06-17",
  "componentSnapshot": ""
}
]
```

---

### GET `/api/payroll/config/tenants/{tenantId}/employees/{employeeId}/profile`
- **Handler Method**: `getEmployeeProfile`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSalaryProfileResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "employeeName": "",
  "version": 0,
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "lopBasisOverride": "LopBasis",
  "workingDaysOverride": 0,
  "promotionReason": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "componentOverrides": [
  {
    "componentCode": "",
    "overrideType": "",
    "overrideValue": 0,
    "overrideFormula": "",
    "isEnabled": false
  }
  ]
}
```

---

### POST `/api/payroll/config/tenants/{tenantId}/employees/{employeeId}/profile`
- **Handler Method**: `createEmployeeProfile`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSalaryProfileResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "employeeName": "",
  "version": 0,
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "lopBasisOverride": "LopBasis",
  "workingDaysOverride": 0,
  "promotionReason": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "componentOverrides": [
  {
    "componentCode": "",
    "overrideType": "",
    "overrideValue": 0,
    "overrideFormula": "",
    "isEnabled": false
  }
  ]
}
```

---

### PUT `/api/payroll/config/tenants/{tenantId}/employees/{employeeId}/profile/{profileId}/components`
- **Handler Method**: `updateEmployeeComponent`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeComponentOverrideDTO`
#### Response JSON Example:
```json
{
  "componentCode": "",
  "overrideType": "",
  "overrideValue": 0,
  "overrideFormula": "",
  "isEnabled": false
}
```

---

### POST `/api/payroll/config/tenants/{tenantId}/employees/{employeeId}/profile/{profileId}/sync`
- **Handler Method**: `syncEmployeeProfileWithGlobal`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/payroll/config/tenants/{tenantId}/employees/{employeeId}/profiles/all`
- **Handler Method**: `getAllEmployeeProfiles`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSalaryProfileResponse[]`
#### Response JSON Example:
```json
[
{
  "id": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "employeeName": "",
  "version": 0,
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "lopBasisOverride": "LopBasis",
  "workingDaysOverride": 0,
  "promotionReason": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "componentOverrides": [
  {
    "componentCode": "",
    "overrideType": "",
    "overrideValue": 0,
    "overrideFormula": "",
    "isEnabled": false
  }
  ]
}
]
```

---

### POST `/api/payroll/config/tenants/{tenantId}/employees/{employeeId}/promote`
- **Handler Method**: `promoteEmployee`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSalaryProfileResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "employeeId": 0,
  "employeeCode": "",
  "employeeName": "",
  "version": 0,
  "monthlyCtc": 0,
  "currency": "",
  "taxRegime": "",
  "lopBasisOverride": "LopBasis",
  "workingDaysOverride": 0,
  "promotionReason": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "componentOverrides": [
  {
    "componentCode": "",
    "overrideType": "",
    "overrideValue": 0,
    "overrideFormula": "",
    "isEnabled": false
  }
  ]
}
```

---

### GET `/api/payroll/config/tenants/{tenantId}/global`
- **Handler Method**: `getGlobalConfig`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantPayrollConfigResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenantId": 0,
  "lopBasis": "LopBasis",
  "workingDaysPerMonth": 0,
  "enablePfCapping": false,
  "enableEsi": false,
  "enablePt": false,
  "enforceNewLabourCodes": false,
  "defaultCurrency": "",
  "defaultTaxRegime": "",
  "enableOvertime": false,
  "overtimeRatePerHour": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "salaryStructures": [ "SalaryStructureResponse" ],
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "value": 0,
  "evaluationOrder": 0,
  "isPartOfPfWages": false,
  "isPartOfEsiWages": false,
  "isTaxable": false,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "minValue": 0,
  "maxValue": 0,
  "formulaExpression": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false
}
```

---

### PUT `/api/payroll/config/tenants/{tenantId}/global`
- **Handler Method**: `updateGlobalConfig`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantPayrollConfigResponse`
#### Response JSON Example:
```json
{
  "id": "UUID",
  "tenantId": 0,
  "lopBasis": "LopBasis",
  "workingDaysPerMonth": 0,
  "enablePfCapping": false,
  "enableEsi": false,
  "enablePt": false,
  "enforceNewLabourCodes": false,
  "defaultCurrency": "",
  "defaultTaxRegime": "",
  "enableOvertime": false,
  "overtimeRatePerHour": 0,
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false,
  "salaryStructures": [ "SalaryStructureResponse" ],
  "id": "UUID",
  "componentCode": "",
  "componentName": "",
  "componentType": "",
  "calculationType": "",
  "value": 0,
  "evaluationOrder": 0,
  "isPartOfPfWages": false,
  "isPartOfEsiWages": false,
  "isTaxable": false,
  "isLopApplicable": false,
  "isEmployerContribution": false,
  "isMandatory": false,
  "allowEmployeeOverride": false,
  "minValue": 0,
  "maxValue": 0,
  "formulaExpression": "",
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-06-17",
  "isActive": false
}
```

---

### POST `/api/payroll/config/tenants/{tenantId}/validate-formula`
- **Handler Method**: `validateFormula`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `FormulaValidationResponse`
#### Response JSON Example:
```json
{
  "valid": false,
  "formula": "",
  "message": ""
}
```

---

## TenantPermissionController

### GET `/api/tenant/permissions`
- **Handler Method**: `getAllPermissions`
- **Description**: Get all permissions (DTO version - safe) Access: Admin and role managers only
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PermissionDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "permission": "",
  "description": "",
  "category": "",
  "displayOrder": 0,
  "selected": false,
  "null": "return"
}
]
```

---

### GET `/api/tenant/permissions/category/{category}`
- **Handler Method**: `getPermissionsByCategory`
- **Description**: Get permissions by category
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PermissionGroupDTO.PermissionInfo[]`
#### Response JSON Example:
```json
[ "PermissionInfo" ]
```

---

### GET `/api/tenant/permissions/groups`
- **Handler Method**: `getGroupedPermissions`
- **Description**: Get all permissions grouped by category Access: Users who can view employee information or manage roles
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PermissionGroupDTO[]`
#### Response JSON Example:
```json
[
{
  "groupName": "",
  "permissions": [ "PermissionInfo" ],
  "id": 0,
  "name": "",
  "description": "",
  "category": "",
  "displayOrder": 0,
  "selected": false
}
]
```

---

### GET `/api/tenant/permissions/{id}`
- **Handler Method**: `getPermissionById`
- **Description**: Get permission by ID
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `PermissionDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "permission": "",
  "description": "",
  "category": "",
  "displayOrder": 0,
  "selected": false,
  "null": "return"
}
```

---

## TenantRegistrationController

### GET `/api/public/check-email`
- **Handler Method**: `checkEmail`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### GET `/api/public/debug-redis`
- **Handler Method**: `debugRedis`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Map<String, Object>`
#### Response JSON Example:
```json
{ "key": "value" }
```

---

### POST `/api/public/register`
- **Handler Method**: `register`
- **Description**: No description provided
- **Request Body Type**: `TenantRegistrationRequest`
#### Request JSON Example:
```json
{
  "companyName": "",
  "adminEmail": "",
  "adminName": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "planCode": ""
}
```
- **Response Type**: `TenantRegistrationResponse`
#### Response JSON Example:
```json
{
  "success": false,
  "message": "",
  "tenantId": 0,
  "tenantCode": "",
  "companyName": "",
  "planType": "",
  "planStatus": "",
  "endsAt": "2026-06-17",
  "status": "",
  "isActive": false,
  "adminEmail": "",
  "adminName": "",
  "adminPhone": "",
  "officeAddress": "",
  "city": "",
  "state": "IndianState",
  "stateText": "",
  "country": "",
  "activationToken": "",
  "activationTokenExpiry": "2026-06-17",
  "superAdminEmployeeId": 0,
  "superAdminEmployeeCode": "",
  "superAdminFullName": "",
  "superAdminEmail": "",
  "superAdminPosition": "",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "deletedAt": "2026-06-17"
}
```

---

### POST `/api/public/set-password`
- **Handler Method**: `setPassword`
- **Description**: No description provided
- **Request Body Type**: `SetPasswordRequest`
#### Request JSON Example:
```json
{
  "token": "",
  "newPassword": "",
  "confirmPassword": ""
}
```
- **Response Type**: `Void`

---

## TenantRoleController

### GET `/api/tenant/roles`
- **Handler Method**: `getAllRoles`
- **Description**: Get all roles for the tenant
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantRoleSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "name": "",
  "description": "",
  "isDefault": false,
  "employeeCount": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### POST `/api/tenant/roles`
- **Handler Method**: `createRole`
- **Description**: Create a new role (Admin only)
- **Request Body Type**: `TenantRoleCreateRequest`
#### Request JSON Example:
```json
{
  "name": "",
  "description": "",
  "permissionIds": 0
}
```
- **Response Type**: `TenantRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isDefault": false,
  "permissions": [ "PermissionInfo" ],
  "employeeCount": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": "",
  "category": ""
}
```

---

### GET `/api/tenant/roles/default`
- **Handler Method**: `getDefaultRoles`
- **Description**: Get default roles for the tenant
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantRoleResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "name": "",
  "description": "",
  "isDefault": false,
  "permissions": [ "PermissionInfo" ],
  "employeeCount": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": "",
  "category": ""
}
]
```

---

### GET `/api/tenant/roles/lookup`
- **Handler Method**: `getRoleLookup`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantRoleLookupResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "name": "",
  "isDefault": false
}
]
```

---

### DELETE `/api/tenant/roles/{roleId}`
- **Handler Method**: `deleteRole`
- **Description**: Delete role (Admin only)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### GET `/api/tenant/roles/{roleId}`
- **Handler Method**: `getRole`
- **Description**: Get role by ID
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isDefault": false,
  "permissions": [ "PermissionInfo" ],
  "employeeCount": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": "",
  "category": ""
}
```

---

### PUT `/api/tenant/roles/{roleId}`
- **Handler Method**: `updateRole`
- **Description**: Update role details (Admin only)
- **Request Body Type**: `TenantRoleUpdateRequest`
#### Request JSON Example:
```json
{
  "name": "",
  "description": "",
  "permissionIds": 0
}
```
- **Response Type**: `TenantRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isDefault": false,
  "permissions": [ "PermissionInfo" ],
  "employeeCount": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": "",
  "category": ""
}
```

---

### POST `/api/tenant/roles/{roleId}/default`
- **Handler Method**: `setDefaultRole`
- **Description**: Set default role for the tenant (Admin only)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isDefault": false,
  "permissions": [ "PermissionInfo" ],
  "employeeCount": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": "",
  "category": ""
}
```

---

### GET `/api/tenant/roles/{roleId}/delete-preview`
- **Handler Method**: `getRoleDeletePreview`
- **Description**: Get delete preview for a role (Admin only)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantRoleDeletePreviewResponse`
#### Response JSON Example:
```json
{
  "roleId": 0,
  "roleName": "",
  "affectedEmployeeCount": 0,
  "affectedEmployees": [
  {
    "id": 0,
    "employeeCode": "",
    "firstName": "",
    "lastName": "",
    "fullName": "",
    "email": "",
    "phone": "",
    "position": "",
    "departmentName": "",
    "status": "EmployeeStatus",
    "isActive": false,
    "profilePictureUrl": "",
    "hireDate": "2026-06-17",
    "managerName": "",
    "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
  }
  ],
  "reassignmentOptions": [
  {
    "id": 0,
    "name": "",
    "isDefault": false
  }
  ],
  "deletable": false,
  "validationMessage": ""
}
```

---

### PUT `/api/tenant/roles/{roleId}/permissions`
- **Handler Method**: `updateRolePermissions`
- **Description**: Update role permissions (Admin only)
- **Request Body Type**: `Set<Long>`
#### Request JSON Example:
```json
0
```
- **Response Type**: `TenantRoleResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "name": "",
  "description": "",
  "isDefault": false,
  "permissions": [ "PermissionInfo" ],
  "employeeCount": 0,
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17",
  "id": 0,
  "name": "",
  "description": "",
  "category": ""
}
```

---

### GET `/api/tenant/roles/{roleId}/users`
- **Handler Method**: `getUsersByRole`
- **Description**: Get users assigned to a role
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `EmployeeSummaryResponse[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "employeeCode": "",
  "firstName": "",
  "lastName": "",
  "fullName": "",
  "email": "",
  "phone": "",
  "position": "",
  "departmentName": "",
  "status": "EmployeeStatus",
  "isActive": false,
  "profilePictureUrl": "",
  "hireDate": "2026-06-17",
  "managerName": "",
  "directReports": [ "[Recursive Reference to EmployeeSummaryResponse]" ]
}
]
```

---

### DELETE `/api/tenant/roles/{roleId}/users/{userId}`
- **Handler Method**: `removeRoleFromUser`
- **Description**: Remove role from user (Admin only)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

### POST `/api/tenant/roles/{roleId}/users/{userId}`
- **Handler Method**: `assignRoleToUser`
- **Description**: Assign role to user (Admin only)
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Void`

---

## TenantSubscriptionController

### POST `/api/tenant/subscriptions/cancel`
- **Handler Method**: `cancelSubscription`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantSubscriptionResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "planType": "",
  "planName": "",
  "planStatus": "PlanStatus",
  "maxEmployees": 0,
  "maxStorageMb": 0,
  "startedAt": "2026-06-17",
  "endsAt": "2026-06-17",
  "amount": 0,
  "currency": "",
  "isActive": false,
  "createdAt": "2026-06-17",
  "billingPeriodStart": "2026-06-17",
  "billingPeriodEnd": "2026-06-17",
  "gracePeriodEnd": "2026-06-17",
  "cancellationReason": "",
  "cancelledAtEndOfPeriod": false
}
```

---

### GET `/api/tenant/subscriptions/current`
- **Handler Method**: `currentSubscription`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantSubscriptionResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "planType": "",
  "planName": "",
  "planStatus": "PlanStatus",
  "maxEmployees": 0,
  "maxStorageMb": 0,
  "startedAt": "2026-06-17",
  "endsAt": "2026-06-17",
  "amount": 0,
  "currency": "",
  "isActive": false,
  "createdAt": "2026-06-17",
  "billingPeriodStart": "2026-06-17",
  "billingPeriodEnd": "2026-06-17",
  "gracePeriodEnd": "2026-06-17",
  "cancellationReason": "",
  "cancelledAtEndOfPeriod": false
}
```

---

### GET `/api/tenant/subscriptions/history`
- **Handler Method**: `getSubscriptionHistory`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantSubscriptionResponseDTO[]`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "planType": "",
  "planName": "",
  "planStatus": "PlanStatus",
  "maxEmployees": 0,
  "maxStorageMb": 0,
  "startedAt": "2026-06-17",
  "endsAt": "2026-06-17",
  "amount": 0,
  "currency": "",
  "isActive": false,
  "createdAt": "2026-06-17",
  "billingPeriodStart": "2026-06-17",
  "billingPeriodEnd": "2026-06-17",
  "gracePeriodEnd": "2026-06-17",
  "cancellationReason": "",
  "cancelledAtEndOfPeriod": false
}
]
```

---

### POST `/api/tenant/subscriptions/renew`
- **Handler Method**: `renewSubscription`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantSubscriptionResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "planType": "",
  "planName": "",
  "planStatus": "PlanStatus",
  "maxEmployees": 0,
  "maxStorageMb": 0,
  "startedAt": "2026-06-17",
  "endsAt": "2026-06-17",
  "amount": 0,
  "currency": "",
  "isActive": false,
  "createdAt": "2026-06-17",
  "billingPeriodStart": "2026-06-17",
  "billingPeriodEnd": "2026-06-17",
  "gracePeriodEnd": "2026-06-17",
  "cancellationReason": "",
  "cancelledAtEndOfPeriod": false
}
```

---

### POST `/api/tenant/subscriptions/upgrade`
- **Handler Method**: `upgradeSubscription`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `TenantSubscriptionResponseDTO`
#### Response JSON Example:
```json
{
  "id": 0,
  "planType": "",
  "planName": "",
  "planStatus": "PlanStatus",
  "maxEmployees": 0,
  "maxStorageMb": 0,
  "startedAt": "2026-06-17",
  "endsAt": "2026-06-17",
  "amount": 0,
  "currency": "",
  "isActive": false,
  "createdAt": "2026-06-17",
  "billingPeriodStart": "2026-06-17",
  "billingPeriodEnd": "2026-06-17",
  "gracePeriodEnd": "2026-06-17",
  "cancellationReason": "",
  "cancelledAtEndOfPeriod": false
}
```

---

## TenantSupportTicketController

### GET `/api/employee/support-tickets`
- **Handler Method**: `getMyTenantTickets`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `Page<SupportTicketResponse>`
#### Response JSON Example:
```json
[
{
  "id": 0,
  "ticketNumber": "",
  "tenantId": 0,
  "tenantCompanyName": "",
  "tenantCode": "",
  "raisedByEmployeeId": 0,
  "raisedByEmployeeName": "",
  "raisedByEmployeeEmail": "",
  "title": "",
  "description": "",
  "category": "",
  "priority": "",
  "status": "",
  "resolution": "",
  "resolvedByPlatformUserId": 0,
  "resolvedByPlatformUserName": "",
  "resolvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
]
```

---

### POST `/api/employee/support-tickets`
- **Handler Method**: `createTicket`
- **Description**: No description provided
- **Request Body Type**: `SupportTicketCreateRequest`
#### Request JSON Example:
```json
{
  "title": "",
  "description": "",
  "category": "",
  "priority": ""
}
```
- **Response Type**: `SupportTicketResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "ticketNumber": "",
  "tenantId": 0,
  "tenantCompanyName": "",
  "tenantCode": "",
  "raisedByEmployeeId": 0,
  "raisedByEmployeeName": "",
  "raisedByEmployeeEmail": "",
  "title": "",
  "description": "",
  "category": "",
  "priority": "",
  "status": "",
  "resolution": "",
  "resolvedByPlatformUserId": 0,
  "resolvedByPlatformUserName": "",
  "resolvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---

### GET `/api/employee/support-tickets/{id}`
- **Handler Method**: `getTicketById`
- **Description**: No description provided
- **Request Body**: None (Query parameters / Path variables only)
- **Response Type**: `SupportTicketResponse`
#### Response JSON Example:
```json
{
  "id": 0,
  "ticketNumber": "",
  "tenantId": 0,
  "tenantCompanyName": "",
  "tenantCode": "",
  "raisedByEmployeeId": 0,
  "raisedByEmployeeName": "",
  "raisedByEmployeeEmail": "",
  "title": "",
  "description": "",
  "category": "",
  "priority": "",
  "status": "",
  "resolution": "",
  "resolvedByPlatformUserId": 0,
  "resolvedByPlatformUserName": "",
  "resolvedAt": "2026-06-17",
  "createdAt": "2026-06-17",
  "updatedAt": "2026-06-17"
}
```

---
