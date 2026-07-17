# SonixHR - Comprehensive Technical Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Feature Modules](#feature-modules)
5. [Core Entity Structure](#core-entity-structure)
6. [API Organization](#api-organization)
7. [Security Model](#security-model)
8. [Getting Started](#getting-started)
9. [Development Patterns](#development-patterns)
10. [Database Schema](#database-schema)

---

## Project Overview

**SonixHR** is a comprehensive **multi-tenant Software-as-a-Service (SaaS) Human Resource Management (HRM) platform** designed to manage organizations of all sizes. It provides integrated solutions for employee lifecycle management, attendance tracking, payroll processing, leave management, task allocation, and organizational operations.

### Key Characteristics
- **Multi-Tenant Architecture**: Complete isolation between tenant organizations using ThreadLocal context and PostgreSQL Row-Level Security (RLS)
- **Dual Authentication System**: Separate authentication layers for Platform Administrators and Tenant Users
- **Cloud-Ready SaaS**: Designed for scalability with support for multiple organizations on a single deployment
- **RESTful API**: Comprehensive REST endpoints covering all HRM operations
- **Modern Stack**: Built with Spring Boot 3.1.5, Java 17, and PostgreSQL

### Default Bootstrap Credentials
```
Email: admin@sonixhr.com
Password: Admin@123
```

---

## Architecture

### Multi-Tenancy & Authentication Model

SonixHR implements a sophisticated multi-tenant architecture with two distinct security contexts:

#### Platform Layer (`/api/platform/auth/**`)
- **Purpose**: Super Admin management of the entire platform
- **Entities**: `PlatformUser`, `PlatformRole`, `PlatformPermission`
- **Bootstrap**: Initialized by `PlatformDataInitializer` at application startup
- **Access**: Limited to platform administrators only

#### Tenant Layer (`/api/auth/**`)
- **Purpose**: Employee and user management within each organization (tenant)
- **Entities**: `Employee`, `TenantUser`, `TenantRole`, `TenantPermission`
- **Isolation**: Uses `TenantContext` (ThreadLocal) to track and enforce current tenant
- **Access**: All tenant-based operations require valid JWT with embedded tenantId

### Authentication Flow Diagram

```
1. User Login Request
   ↓
2. JWT Token Generation (includes tenantId)
   ↓
3. JwtAuthFilter validates token
   ↓
4. TenantContext.setCurrentTenant(tenantId)
   ↓
5. TenantRLSService enforces DB-level filtering via PostgreSQL set_current_tenant()
   ↓
6. All queries automatically filtered to current tenant
```

### Data Isolation Strategy

**ThreadLocal Tenant Context:**
```java
// TenantContext holds the current tenant UUID
UUID tenantId = TenantContext.getCurrentTenant();
```

**Database-Level Security:**
- PostgreSQL Row-Level Security (RLS) function: `set_current_tenant()`
- All tenant data queries automatically filtered at database level
- Employee entity includes: `@ManyToOne Tenant tenant`

---

## Technology Stack

### Core Framework
| Component | Version | Purpose |
|-----------|---------|---------|
| **Spring Boot** | 3.1.5 | Application framework |
| **Java** | 17 | Programming language |
| **Maven** | 3.8.x | Dependency management & build |

### Database & Persistence
| Component | Version | Purpose |
|-----------|---------|---------|
| **PostgreSQL** | 13+ | Primary relational database |
| **Flyway** | Latest | Database migrations (currently disabled) |
| **Hibernate** | 6.x | ORM framework |
| **hibernate-types** | 60 | Support for JSONB columns |
| **Spring Data JPA** | - | Data access abstraction |

### Authentication & Security
| Component | Version | Purpose |
|-----------|---------|---------|
| **JJWT** | 0.11.5 | JWT token generation & validation |
| **Spring Security** | 6.x | Authentication & authorization |
| **BCrypt** | - | Password encoding (strength: 12) |

### Caching & Performance
| Component | Version | Purpose |
|-----------|---------|---------|
| **Redis** | Latest | Distributed token blacklist |
| **Caffeine** | 3.1.8 | In-memory caching |

### Additional Libraries
| Component | Version | Purpose |
|-----------|---------|---------|
| **Lombok** | 1.18.x | Boilerplate code reduction (@Builder, @Getter, etc.) |
| **Jackson** | - | JSON serialization/deserialization |
| **Jakarta Bean Validation** | - | Input validation (@NotNull, @Email, etc.) |
| **SendGrid** | Latest | Email delivery service |
| **spring-dotenv** | Latest | Load environment variables from .env file |

### Development Tools
- **Swagger/Springdoc OpenAPI**: API documentation
- **Spring DevTools**: Hot reload during development
- **H2 Database**: Optional in-memory testing database

---

## Feature Modules

SonixHR is organized into 19 major feature modules, each handling distinct aspects of HR management:

### 1. **Platform Management** (`/api/platform/**`)
- Platform-level user administration
- Super admin dashboard
- System configuration
- **Key Controllers**: `PlatformController`, `PlatformDashboardController`

### 2. **Tenant Management** (`/api/tenants/**`)
- Organization (tenant) registration and lifecycle
- Tenant configuration
- Subdomain management
- **Key Controllers**: `TenantController`

### 3. **Employee Management** (`/api/employees/**`)
- Employee profile creation & updates
- Employee records & documentation
- Employee lifecycle (hire, transfer, resign)
- **Key Controllers**: `EmployeeController`
- **Related Entities**: `Employee`, `EmployeeAddress`, `EmployeeEducation`, `EmployeeFamily`

### 4. **Organization Structure** (`/api/departments/**`, `/api/designations/**`)
- Department management
- Job designations/positions
- Organizational hierarchy
- **Key Controllers**: `DepartmentController`, `DesignationController`
- **Related Entities**: `Department`, `Designation`

### 5. **Attendance & Time Tracking** (`/api/attendance/**`, `/api/shifts/**`)
- Daily attendance recording
- Shift management and scheduling
- Attendance reports
- **Key Controllers**: `AttendanceController`, `ShiftController`
- **Related Entities**: `Attendance`, `Shift`, `AttendanceLog`

### 6. **Leave Management** (`/api/leave/**`)
- Leave type configuration
- Leave requests and approvals
- Leave balance tracking
- Leave policy management
- **Key Controllers**: `LeaveManagementController`, `LeaveTypeController`, `LeavePolicyController`
- **Related Entities**: `Leave`, `LeaveType`, `LeavePolicy`, `LeaveBalance`

### 7. **Payroll & Compensation** (`/api/payroll/**`)
- Salary configuration
- Payroll processing
- Payslip generation
- **Key Controllers**: `PayrollController`, `SalaryController`, `PayslipController`
- **Related Entities**: `Salary`, `Payroll`, `Payslip`, `PayslipComponent`

### 8. **Tax Management** (`/api/tax/**`)
- Tax configuration per tenant
- Tax deductions
- Tax calculations and reporting
- **Related Entities**: `TaxConfiguration`, `TaxSlab`, `TaxDeduction`

### 9. **Task Management** (`/api/tasks/**`)
- Task creation and assignment
- Project management
- Task status tracking
- **Key Controllers**: `TaskController`
- **Related Entities**: `Task`, `Project`, `TaskAssignment`, `TaskStatus`

### 10. **Leave Encashment** (`/api/leave-encashment/**`)
- Leave conversion to cash
- Encashment requests
- Policy enforcement
- **Related Entities**: `LeaveEncashment`

### 11. **Employee Separation** (`/api/employee-separation/**`)
- Resignation tracking
- Exit procedures
- Separation documents
- **Related Entities**: `EmployeeSeparation`

### 12. **Notifications & Alerts** (`/api/notifications/**`)
- Event-based notifications
- Email notifications
- Notification templates
- **Related Entities**: `Notification`, `NotificationTemplate`, `EmailNotification`

### 13. **Communication** (`/api/announcements/**`, `/api/emails/**`)
- Organizational announcements
- Bulk email campaigns
- Employee communication
- **Related Entities**: `Announcement`, `Email`

### 14. **Support & Ticketing** (`/api/support-tickets/**`)
- Help desk tickets
- Issue tracking
- Support request management
- **Related Entities**: `SupportTicket`

### 15. **Holiday Calendar** (`/api/calendar/**`)
- Holiday configuration
- Public holidays
- Calendar management
- **Key Controllers**: `CalendarController`
- **Related Entities**: `Holiday`, `Calendar`

### 16. **Grievance Management** (`/api/grievances/**`)
- Employee grievance tracking
- Grievance resolution workflow
- **Related Entities**: `Grievance`

### 17. **Training & Development** (`/api/training/**`)
- Training program management
- Employee training enrollment
- Skill tracking
- **Related Entities**: `Training`, `TrainingEnrollment`

### 18. **Performance Management**
- Performance reviews
- Ratings and feedback
- **Related Entities**: `PerformanceReview`

### 19. **Reports & Analytics** (`/api/reports/**`)
- Custom report generation
- HR analytics
- Business intelligence dashboards
- **Key Controllers**: `ReportController`

---

## Core Entity Structure

### Entity Domains Overview

SonixHR contains **48+ JPA entities** organized into 8 domains:

#### 1. **Attendance Domain** (4 entities)
- `Attendance`: Daily attendance records
- `AttendanceLog`: Audit logs of attendance changes
- `Shift`: Shift definitions and scheduling
- `ShiftAssignment`: Employee to shift mappings

#### 2. **Department Domain** (2 entities)
- `Department`: Organizational departments
- `Designation`: Job positions/titles

#### 3. **Employee Domain** (8 entities)
- `Employee`: Core employee records
- `EmployeeAddress`: Employee address information
- `EmployeeBank`: Bank account details
- `EmployeeContact`: Contact information
- `EmployeeDocuments`: Document storage
- `EmployeeEducation`: Educational background
- `EmployeeEmergencyContact`: Emergency contacts
- `EmployeeFamily`: Family member information

#### 4. **Leave Domain** (6 entities)
- `Leave`: Leave requests
- `LeaveType`: Types of leave (Annual, Sick, etc.)
- `LeavePolicy`: Leave policies per tenant
- `LeaveBalance`: Employee leave balance tracking
- `LeaveEncashment`: Leave to cash conversion
- `LeaveSetting`: Tenant-specific leave settings

#### 5. **Payroll Domain** (8 entities)
- `Payroll`: Monthly payroll records
- `Payslip`: Individual payslips
- `PayslipComponent`: Payslip line items
- `Salary`: Employee salary configuration
- `SalaryStructure`: Template salary structures
- `SalaryComponent`: Salary line items (base, allowances, deductions)
- `TaxConfiguration`: Tax configuration
- `TaxSlab`: Tax brackets and rates

#### 6. **Platform Domain** (3 entities)
- `PlatformUser`: Super admin users
- `PlatformRole`: Super admin roles
- `PlatformPermission`: Super admin permissions

#### 7. **Task Domain** (5 entities)
- `Task`: Task records
- `TaskAssignment`: Task to employee mappings
- `TaskStatus`: Task status definitions
- `Project`: Project groupings
- `TaskPriority`: Priority levels

#### 8. **Tenant Domain** (12+ entities)
- `Tenant`: Organization records
- `TenantUser`: Employee user accounts
- `TenantRole`: Role definitions
- `TenantPermission`: Permission definitions
- `Announcement`: Organizational announcements
- `Calendar`: Calendar/holiday management
- `Holiday`: Public holidays
- `Grievance`: Employee grievances
- `Notification`: System notifications
- `SupportTicket`: Help desk tickets
- `Training`: Training programs
- `PerformanceReview`: Performance evaluations

### Entity Relationship Patterns

**Many-to-One (Tenant Isolation):**
```java
@Entity
public class Employee {
    @ManyToOne
    private Tenant tenant;  // Every employee belongs to exactly one tenant
    // ... other fields
}
```

**One-to-Many (Collections):**
```java
@Entity
public class Department {
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<Employee> employees;  // Department has many employees
}
```

**JSONB Custom Fields (PostgreSQL):**
```java
@Entity
public class Employee {
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> customFields;  // Flexible schema
}
```

---

## API Organization

### API Structure

SonixHR exposes REST endpoints organized by feature domain:

```
/api/
├── platform/              # Platform admin operations
│   ├── auth/             # Platform login
│   └── dashboard/        # Super admin dashboards
├── auth/                 # Tenant user authentication
├── tenants/              # Tenant management
├── employees/            # Employee operations
├── departments/          # Department management
├── designations/         # Job designations
├── attendance/           # Attendance tracking
├── shifts/               # Shift management
├── leave/                # Leave requests & management
├── payroll/              # Payroll & salary
├── tax/                  # Tax configuration
├── tasks/                # Task management
├── calendar/             # Holiday calendar
├── notifications/        # System notifications
├── announcements/        # Organizational announcements
├── support-tickets/      # Support/help desk
├── grievances/           # Grievance tracking
├── training/             # Training programs
├── performance/          # Performance reviews
└── reports/              # Analytics & reports
```

### Endpoint Security

**Open Endpoints** (no authentication required):
```
POST   /api/auth/login              # Tenant user login
POST   /api/auth/register           # Tenant user registration
POST   /api/platform/auth/login     # Platform admin login
POST   /api/tenants/register        # New tenant registration
POST   /api/forgot-password/**      # Password recovery
GET    /swagger-ui/**               # API documentation
GET    /v3/api-docs/**              # OpenAPI schema
```

**Protected Endpoints** (require JWT authentication):
```
All other /api/** endpoints require:
- Header: Authorization: Bearer <JWT_TOKEN>
- Token must be valid and not blacklisted
- Request must include valid tenantId in JWT claims
```

### DTO Request/Response Flow

All endpoints follow a consistent DTO pattern:

```
Client Request
  ↓
Request DTO (validated with @NotNull, @Email, etc.)
  ↓
Service Layer (Mapper: toEntity)
  ↓
JPA Entity (persisted to database)
  ↓
Service Layer (Mapper: toResponse)
  ↓
Response DTO (@Builder, null-safe conversions)
  ↓
Client Response (JSON)
```

**Example: Create Employee**
```java
// Request
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "designationId": "uuid-1234",
  "departmentId": "uuid-5678"
}

// Response
{
  "id": "uuid-9999",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "designation": { "id": "uuid-1234", "name": "Engineer" },
  "department": { "id": "uuid-5678", "name": "IT" },
  "status": "ACTIVE"
}
```

---

## Security Model

### JWT Authentication

**Token Structure:**
```
Header: { "alg": "HS256", "typ": "JWT" }
Payload: {
  "sub": "user@example.com",
  "iat": 1234567890,
  "exp": 1234571490,
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",  // Critical for tenant isolation
  "permissions": ["READ_EMPLOYEE", "WRITE_LEAVE"]
}
Signature: HS256(secret)
```

**Configuration:**
```properties
app.jwt.secret=your-secret-key-min-32-characters
app.jwt.expiration=86400000  # 24 hours in milliseconds
```

### Role-Based Access Control (RBAC)

**Platform Roles:**
- `SUPER_ADMIN`: Full platform access

**Tenant Roles:**
- `ADMIN`: Full tenant access
- `MANAGER`: Department/team management
- `EMPLOYEE`: Standard employee access
- `HR`: HR operations (leave, payroll)
- `FINANCE`: Financial operations

**Permission Annotations:**
```java
@PreAuthorize("@permissionEvaluator.hasPermission(#employeeId, 'Employee', 'READ')")
public Employee getEmployee(@PathVariable UUID employeeId) { }

@PreAuthorize("hasRole('ADMIN')")
public void deleteEmployee(@PathVariable UUID employeeId) { }
```

### Password Security

- **Encoding**: BCryptPasswordEncoder with strength 12
- **Validation**: Passwords must meet complexity requirements
- **Storage**: Never stored in plain text

### Token Blacklist

Logout invalidates tokens via Redis-backed in-memory cache:
```java
jwtService.tokenBlacklist.put(token, true);
```

### CORS Configuration

Current configuration:
```
Allowed Origins: http://localhost:3000, http://localhost:5173
Methods: GET, POST, PUT, DELETE, OPTIONS
Credentials: Allowed
```

> **Note for Production**: Add production domain origins in `SecurityConfig.corsConfigurationSource()`

---

## Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.8+
- PostgreSQL 13+
- Redis (optional, for distributed caching)

### Environment Setup

**1. Clone Repository**
```bash
git clone https://github.com/aniketsonkamble07/SonixHR.git
cd sonixhr
```

**2. Configure Environment Variables**

Create `.env` file in project root:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sonixhr
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your-password
APP_JWT_SECRET=your-secret-key-min-32-characters
APP_JWT_EXPIRATION=86400000
REDIS_HOST=localhost
REDIS_PORT=6379
```

Or edit `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sonixhr
spring.datasource.username=postgres
spring.datasource.password=your-password
app.jwt.secret=your-secret-key
app.jwt.expiration=86400000
```

**3. Build Project**
```bash
mvn clean install
```

**4. Run Application**
```bash
mvn spring-boot:run
```

Application starts on: `http://localhost:8081`

**5. Verify Installation**

Access Swagger UI:
```
http://localhost:8081/swagger-ui/index.html
```

Test Platform Login:
```bash
curl -X POST http://localhost:8081/api/platform/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@sonixhr.com",
    "password": "Admin@123"
  }'
```

**6. Database Migrations**

Enable Flyway migrations (currently disabled):
```properties
spring.flyway.enabled=true
```

---

## Development Patterns

### Adding a New Tenant-Aware Entity

**1. Create Entity Class**
```java
@Entity
@Table(name = "my_entities", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
public class MyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    private String name;
    // ... other fields
}
```

**2. Create Repository**
```java
@Repository
public interface MyEntityRepository extends JpaRepository<MyEntity, UUID> {
    Optional<MyEntity> findByIdAndTenant(UUID id, Tenant tenant);
    List<MyEntity> findAllByTenant(Tenant tenant);
}
```

**3. Create DTOs**
```java
@Data
@Builder
public class MyEntityRequest {
    @NotBlank
    private String name;
}

@Data
@Builder
public class MyEntityResponse {
    private UUID id;
    private String name;
}
```

**4. Create Mapper (in Service)**
```java
@Service
public class MyEntityService {
    @Autowired
    private MyEntityRepository repository;

    @Autowired
    private MyEntityMapper mapper;

    private Tenant getCurrentTenant() {
        return tenantService.getTenantById(TenantContext.getCurrentTenant());
    }

    public MyEntityResponse create(MyEntityRequest request) {
        MyEntity entity = mapper.toEntity(request, getCurrentTenant());
        entity = repository.save(entity);
        return mapper.toResponse(entity);
    }
}
```

**5. Create Controller**
```java
@RestController
@RequestMapping("/api/my-entities")
public class MyEntityController {
    @Autowired
    private MyEntityService service;

    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(null, 'MyEntity', 'CREATE')")
    public ResponseEntity<MyEntityResponse> create(@RequestBody MyEntityRequest request) {
        return ResponseEntity.ok(service.create(request));
    }
}
```

### Exception Handling

All exceptions are caught by `GlobalExceptionHandler` and return standardized JSON:

```java
{
  "success": false,
  "message": "Error description",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Custom Exceptions:**
```java
throw new DuplicateResourceException("Employee email already exists");
throw new NotFoundException("Employee not found");
throw new RoleNotFoundException("Role not found");
throw new SubdomainExistsException("Subdomain already registered");
```

### Transactional Operations

Use `@Transactional` for operations modifying multiple entities:

```java
@Transactional
public void updateEmployeeAndSalary(UUID employeeId, EmployeeUpdateRequest request) {
    // Both employee and salary updates are atomic
    updateEmployee(employeeId, request);
    updateSalary(employeeId, request.getSalary());
}
```

---

## Database Schema

### Key Tables

**Tenant Isolation Table:**
```sql
-- All tenant data includes tenant_id foreign key
CREATE TABLE employees (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    status ENUM('ACTIVE', 'INACTIVE', 'RESIGNED'),
    custom_fields JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(tenant_id, email)
);
```

**Row-Level Security:**
```sql
-- PostgreSQL RLS function for tenant isolation
CREATE FUNCTION set_current_tenant(tenant_id UUID) RETURNS void AS $$
BEGIN
    EXECUTE 'SELECT set_config(''app.current_tenant'', $1::TEXT, false)';
END;
$$ LANGUAGE plpgsql;

-- Enable RLS on all tenant tables
ALTER TABLE employees ENABLE ROW LEVEL SECURITY;
```

### Flexible Schema with JSONB

Employees can store custom fields:
```java
employee.setCustomFields(Map.of(
    "certifications", List.of("AWS", "Azure"),
    "references", Map.of("name", "John", "phone", "123-456-7890")
));
```

---

## Known Issues & Considerations

### Bootstrap Bug
Line 91 in `Employee.resign()` currently sets status to `ACTIVE` but should be `RESIGNED`.

### Database Migrations
Flyway is currently disabled. Enable in production:
```properties
spring.flyway.enabled=true
```

### CORS Configuration
Update CORS origins for production deployments in `SecurityConfig.corsConfigurationSource()`.

---

## Configuration Profiles

### Development Profile (`application-dev.properties`)
- Enhanced logging
- Lower JWT expiration for testing
- H2 in-memory database option

### Production Profile (`application-prod.properties`)
- Optimized logging
- Extended JWT expiration
- PostgreSQL with connection pooling
- Redis caching enabled
- Flyway migrations enabled

**Activate Profile:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"
```

---

## Monitoring & Debugging

### Enable Debug Logging

Add to `application.properties`:
```properties
logging.level.com.sonixhr=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.springframework.security=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

### JWT Token Inspection

Decode JWT at [jwt.io](https://jwt.io) to verify claims and expiration.

### Database Connection Testing

```bash
psql -U postgres -d sonixhr -c "SELECT current_setting('app.current_tenant');"
```

---

## API Documentation

**Interactive API Docs:**
```
Swagger UI: http://localhost:8081/swagger-ui/index.html
OpenAPI Schema: http://localhost:8081/v3/api-docs
```

---

## Support & Resources

- **GitHub**: https://github.com/aniketsonkamble07/SonixHR
- **README**: See project root README.md for additional setup details
- **Issue Tracking**: GitHub Issues for bug reports and feature requests

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Technology Stack**: Spring Boot 3.1.5, Java 17, PostgreSQL, JWT, Redis
