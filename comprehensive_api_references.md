# SonixHR Comprehensive API Reference Guide

This document provides a detailed catalog of the API endpoints available in the SonixHR multi-tenant platform. All requests must respect the dual authentication context (Platform vs. Tenant layers) and include a valid JWT token where required.

---

## 1. Authentication & Security Contexts

SonixHR isolates security contexts by request prefix:
- **Platform Layer (`/api/platform/**`)**: Access restricted to Super Admins and Platform Staff (`PlatformUser` context).
- **Tenant Layer (`/api/**`)**: Access scoped to specific organization employees (`Employee` context). The `TenantContext` is populated automatically from the JWT payload.

**Headers**:
- `Authorization: Bearer <JWT_TOKEN>` (Required for all protected endpoints).

---

## 2. Platform Admin APIs

### Authentication (`/api/platform/auth`)
| Method | Path | Description | Payload DTO | Response DTO |
|---|---|---|---|---|
| `POST` | `/login` | Platform login | `LoginRequest` | `LoginResponse` |
| `POST` | `/logout` | Platform logout (Blacklist token) | - | - |
| `POST` | `/refresh` | Refresh JWT Token pair | `TokenPair` | `TokenPair` |
| `POST` | `/activate` | Activate admin profile | `ActivationRequest` | `PlatformUserResponse` |
| `POST` | `/forgot-password` | Request password reset email | - | - |
| `POST` | `/reset-password` | Reset password | - | - |
| `GET` | `/verify-token` | Validate platform token | - | Verification status |

### Dashboard & Health (`/api/platform`)
| Method | Path | Description | Query Param / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/dashboard` | System usage, MRR, trends | `VIEW_SYSTEM_METRICS` | `PlatformDashboardDTO` |
| `GET` | `/health` | System DB, Redis, Mail health | `VIEW_SYSTEM_HEALTH` | `SystemHealthDTO` |

### Tenant Management (`/api/platform/tenants`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/` | List all tenants | `VIEW_TENANTS` | `Page<PlatformTenantResponseDTO>` |
| `GET` | `/{id}` | Get tenant details | `VIEW_TENANTS` | `PlatformTenantResponseDTO` |
| `POST` | `/` | Register new tenant | `TenantRegistrationRequest` | `PlatformTenantResponseDTO` |
| `PUT` | `/{id}` | Update tenant profile | `TenantUpdateRequest` | `PlatformTenantResponseDTO` |
| `PUT` | `/{id}/plan-override` | Override seat & storage limits | `TenantPlanOverrideDTO` | `PlatformTenantResponseDTO` |
| `PUT` | `/{id}/suspend` | Suspend tenant organization | `MANAGE_TENANTS` | `PlatformTenantResponseDTO` |
| `PUT` | `/{id}/activate` | Reactivate tenant | `MANAGE_TENANTS` | `PlatformTenantResponseDTO` |
| `PUT` | `/{id}/deactivate` | Set tenant as inactive | `MANAGE_TENANTS` | `PlatformTenantResponseDTO` |
| `DELETE` | `/{id}` | Hard delete tenant (with caution) | `DELETE_TENANTS` | Success Message |

### Platform Users (`/api/platform/users`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `POST` | `/` | Invite/Create platform staff | `PlatformUserCreateRequest` | `PlatformUserCreateResponse` |
| `GET` | `/{id}` | Retrieve staff details | `VIEW_PLATFORM_USERS` | `PlatformUserResponse` |
| `PUT` | `/{id}` | Update staff details | `PlatformUserUpdateRequest` | `PlatformUserResponse` |
| `PUT` | `/{id}/status` | Suspend/Reactivate user | `MANAGE_PLATFORM_USERS` | `PlatformUserResponse` |

### Platform Roles & Permissions (`/api/platform/roles` & `/api/platform/permissions`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/roles` | List all platform roles | `VIEW_PLATFORM_ROLES` | `List<PlatformRoleResponse>` |
| `POST` | `/roles` | Create new platform role | `PlatformRoleCreateRequest` | `PlatformRoleResponse` |
| `GET` | `/roles/{id}/delete-preview`| Delete preview impact validation | `VIEW_PLATFORM_ROLES` | `PlatformRoleDeletePreviewResponse` |
| `GET` | `/permissions` | List all platform permissions | `VIEW_PLATFORM_ROLES` | `List<PermissionDTO>` |
| `GET` | `/permissions/groups` | Grouped permissions | `VIEW_PLATFORM_ROLES` | `List<PermissionGroupDTO>` |

---

## 3. Tenant Administration APIs

### Tenant Auth & Registration (`/api/tenant/auth` & `/api/tenants`)
| Method | Path | Description | Payload DTO | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/tenants/register` | Self-service register tenant | `TenantRegistrationRequest`| `PlatformTenantResponseDTO`|
| `POST` | `/api/tenant/auth/login` | Tenant portal login | `LoginRequest` | `LoginResponse` |
| `POST` | `/api/tenant/auth/logout`| Invalidate token session | - | - |
| `POST` | `/api/tenant/auth/activate`| Activate employee profile | `ActivationRequest` | `EmployeeResponse` |
| `GET` | `/api/tenant/auth/me` | Fetch active user credentials | - | `EmployeeResponse` |

### Department Management (`/api/departments`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/` | List all departments | `DEPT_VIEW` | `List<DepartmentResponse>` |
| `POST` | `/` | Create department | `DepartmentRequest` / `DEPT_CREATE`| `DepartmentResponse` |
| `PUT` | `/{id}` | Update department | `DEPT_EDIT` | `DepartmentResponse` |
| `DELETE` | `/{id}` | Delete department | `DEPT_DELETE` | Success Message |

### Employee Management (`/api/employees`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/` | List/filter employees | `EMPLOYEE_VIEW` | `Page<EmployeeSummaryResponse>`|
| `GET` | `/{id}` | Fetch employee profile details | `EMPLOYEE_VIEW` | `EmployeeResponse` |
| `POST` | `/` | Create/Onboard new employee | `EmployeeCreateRequest` | `EmployeeResponse` |
| `PUT` | `/{id}` | Update employee profile details | `EmployeeUpdateRequest` | `EmployeeResponse` |
| `DELETE` | `/{id}` | Delete employee account | `EMPLOYEE_DELETE` | Success Message |
| `PUT` | `/{id}/activate` | Reactivate employee status | `EMPLOYEE_EDIT` | `EmployeeResponse` |
| `PUT` | `/{id}/deactivate`| Deactivate employee status | `EMPLOYEE_EDIT` | `EmployeeResponse` |

### Tenant Roles & Permissions (`/api/roles` & `/api/permissions`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/roles` | List all tenant roles | `ROLE_VIEW` | `List<TenantRoleResponse>` |
| `POST` | `/roles` | Create tenant role | `TenantRoleCreateRequest` | `TenantRoleResponse` |
| `GET` | `/roles/{id}/delete-preview`| Validate role delete impact | `ROLE_VIEW` | `TenantRoleDeletePreviewResponse`|
| `DELETE` | `/roles/{id}` | Delete role | `ROLE_DELETE` | Success Message |
| `GET` | `/permissions` | List tenant permissions | `ROLE_VIEW` | `List<PermissionDTO>` |
| `GET` | `/permissions/groups` | Fetch categorized permission groups | `ROLE_VIEW` | `List<PermissionGroupDTO>` |

### Shift Configurations (`/api/shifts`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/` | List shift schedules | `SHIFT_VIEW` | `List<ShiftResponse>` |
| `POST` | `/` | Create shift template | `ShiftRequest` / `SHIFT_CREATE` | `ShiftResponse` |
| `PUT` | `/{id}` | Update shift schedule | `SHIFT_EDIT` | `ShiftResponse` |
| `DELETE` | `/{id}` | Delete shift template | `SHIFT_DELETE` | Success Message |

---

## 4. Employee Self-Service (ESS) APIs

### Profile & Org Chart (`/api/employee`)
| Method | Path | Description | Payload DTO | Response DTO |
|---|---|---|---|---|
| `GET` | `/profile` | Get logged-in employee profile | - | `EmployeeResponse` |
| `PUT` | `/profile` | Update own contact/address | `EmployeeProfileUpdateRequest`| `EmployeeResponse` |
| `GET` | `/organization-chart`| Fetch reporting structure | - | `MyOrgChartResponse` |

---

## 5. Leave Management APIs

### Employee Leave Panel (`/api/employee/leaves`)
| Method | Path | Description | Payload DTO | Response DTO |
|---|---|---|---|---|
| `POST` | `/` | Request leave request | `LeaveRequestDTO` | `LeaveResponseDTO` |
| `GET` | `/` | Fetch own leave request logs | - | `List<LeaveResponseDTO>` |
| `GET` | `/balance` | Fetch remaining leave balances | - | `Map<String, Object>` |
| `PUT` | `/{id}/cancel` | Cancel own leave request | Optional cancellation reason | `LeaveResponseDTO` |

### Manager & Admin Leave Controls (`/api/employees/leaves`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/settings` | Get global leave rules | `SETTINGS_VIEW` | `LeaveSettingsDTO` |
| `PUT` | `/settings` | Update global leave rules | `LeaveSettingsDTO` / `SETTINGS_EDIT`| `LeaveSettingsDTO` |
| `GET` | `/team` | Fetch team pending requests | `status` (Query) / `LEAVE_VIEW_TEAM`| `Page<LeaveResponseDTO>` |
| `PUT` | `/{id}/approve` | Approve leave request | `LEAVE_APPROVE` | `LeaveResponseDTO` |
| `PUT` | `/{id}/reject` | Reject leave request | `rejectionReason` (Query) | `LeaveResponseDTO` |
| `GET` | `/policies` | Fetch leave policies (allocations)| `SETTINGS_VIEW` | `Map<String, LeavePolicyDTO>`|
| `PUT` | `/policies/{leaveType}`| Update policy parameters | `LeavePolicyDTO` / `SETTINGS_EDIT`| `LeavePolicyDTO` |

---

## 6. Attendance APIs

### Clock-In & Time Logs (`/api/attendance`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `POST` | `/mark` | Mark current attendance check-in/out| - | `AttendanceLogResponse` |
| `POST` | `/overtime` | Log request for overtime | Overtime duration payload | Overtime Response DTO |
| `GET` | `/team/members` | Get reporting team members | `ATTENDANCE_VIEW_TEAM` | `List<EmployeeSummaryResponse>` |
| `GET` | `/team/summary` | Get summary stats for today | `ATTENDANCE_VIEW_TEAM` | `AttendanceSummaryResponse` |
| `GET` | `/dashboard` | Retrieve stats dashboard | - | `ManualDashboardStatsResponse` |

---

## 7. Payroll & Salary Slips

### Configuration & Retrieval (`/api/payroll` & `/api/payslips`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/payslips/me` | Fetch own payroll slip logs | - | `List<PayslipResponse>` |
| `GET` | `/payslips/{id}` | Download payslip PDF | - | PDF Byte Array |
| `POST` | `/payroll/process` | Run payroll cycle calculation | `PAYROLL_RUN` | Process Response DTO |
| `PUT` | `/payroll/settings` | Configure base salary parameters | `SETTINGS_EDIT` | Configuration Response DTO|

---

## 8. Tasks & Operations

### Task Allocation (`/api/tasks`)
| Method | Path | Description | Payload / Authority | Response DTO |
|---|---|---|---|---|
| `GET` | `/` | List tasks assigned to user | - | `List<TaskResponse>` |
| `POST` | `/` | Assign task to employee | `TaskCreateRequest` / `TASK_CREATE` | `TaskResponse` |
| `PUT` | `/{id}/status` | Update progress status (e.g. DONE) | Status payload | `TaskResponse` |
