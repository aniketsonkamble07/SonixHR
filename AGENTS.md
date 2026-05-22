# AGENTS.md - SonixHR Codebase Guide

SonixHR is a **multi-tenant SaaS HRM platform** built with Spring Boot 3.1.5, Java 17, and PostgreSQL. This guide provides essential patterns and workflows for AI agents.

## Architecture Overview

### Multi-Tenancy & Authentication (Critical)

The system uses **two separate authentication contexts**:

1. **Platform Layer** (`/api/platform/auth/**`)
   - Super Admin manages the entire platform
   - `PlatformUser` + `PlatformRole` + `PlatformPermission` entities
   - Bootstrapped via `PlatformDataInitializer` (runs at startup)

2. **Tenant Layer** (`/api/auth/**`)
   - Each tenant has isolated employees and user accounts
   - Uses `TenantContext` (ThreadLocal) to track current tenant
   - JWT tokens include `tenantId` claim (see `JwtService.extractTenantIdAsUUID`)

**Critical Data Flow:**
- JWT token → `JwtAuthFilter` extracts tenantId → sets `TenantContext` (ThreadLocal)
- Tenant isolation enforced via `TenantRLSService` (sets PostgreSQL `set_current_tenant()`)
- All tenant data queries are automatically filtered at DB level

**When adding features:**
- Always inject `TenantContext` to get `UUID getCurrentTenant()`
- Use `TenantRLSService` for explicit DB-level tenant filtering
- Employee entity has `@ManyToOne Tenant tenant` - queries MUST filter by tenant

### Security Architecture

- **JWT Strategy**: Stateless, token blacklist in `JwtService.tokenBlacklist`
- **Custom Evaluator**: `CustomPermissionEvaluator` enables `@PreAuthorize` annotations
- **Session**: Stateless (`SessionCreationPolicy.STATELESS`)
- **CORS**: Configured for localhost:3000 & localhost:5173; add production origins in `SecurityConfig.corsConfigurationSource()`
- **Password Encoding**: BCryptPasswordEncoder with strength 12

**Problematic bug in bootstrap**: Line 91 in `Employee.resign()` should be `RESIGNED`, not `ACTIVE`.

## Project Structure & Patterns

### DTO Pattern (Request → Entity → Response)

All API operations follow strict DTO flow:

```
EmployeeCreateRequest (validation in DTO)
  ↓
EmployeeMapper.toEntity() (in service layer)
  ↓
Employee entity (persisted)
  ↓
EmployeeMapper.toResponse() (in service layer)
  ↓
EmployeeResponse (JSON to client)
```

- **Mapper**: Manual bean injected in service; handles null-safe conversions
- **DTO Builders**: Use Lombok `@Builder` for all DTOs
- **Partial Updates**: `EmployeeUpdateRequest` uses null checks in mapper

### Exception Handling

- `GlobalExceptionHandler` catches ALL exceptions → returns JSON with `success: false, message, timestamp`
- Custom exceptions: `DuplicateResourceException`, `NotFoundException`, `RoleNotFoundException`, `SubdomainExistsException`
- **Pattern**: Throw custom exceptions in services; handler returns 409 (CONFLICT) for duplicates, 500 for generic errors

### Scheduled/Async Patterns

- `ApplicationRunner` with `@Order(1)` used for bootstrap (e.g., `PlatformDataInitializer`)
- No async/scheduled jobs observed; use `@Async` or `@Scheduled` if adding background work

## Key Technologies & Configuration

| Component | Details |
|-----------|---------|
| **Database** | PostgreSQL with Flyway (currently disabled; migrations needed) |
| **ORM** | JPA/Hibernate with custom `@JdbcTypeCode(SqlTypes.JSON)` for JSONB fields |
| **JWT** | JJWT 0.11.5; secret in `app.jwt.secret`, expiration in `app.jwt.expiration` |
| **Mail** | Spring Boot Starter Mail configured; used by `EmailService` |
| **Validation** | Jakarta Bean Validation (in DTOs) |
| **Dev Tool** | spring-dotenv (load `.env` file if exists) |

**PostgreSQL-Specific:**
- Employee has `customFields` stored as JSONB (leverages Hibernate types-60)
- RLS trigger function `set_current_tenant()` must exist in DB

## Common Development Workflows

### Build & Run
```bash
mvn clean install              # Full build
mvn spring-boot:run            # Start app on port 8081
mvn test                        # Run tests
```

### Enable/Disable Features
- **Flyway migrations**: Set `spring.flyway.enabled=true` in properties
- **Debug logging**: Already enabled for `com.sonixhr` and `org.springframework.web`
- **JWT tokens**: Edit `app.jwt.expiration` in properties

### Add New Tenant-Aware Entity

1. Create entity class with `@ManyToOne Tenant tenant`
2. Add unique constraint: `@UniqueConstraint(columnNames = {"tenant_id", "field_name"})`
3. Create repo extending `JpaRepository<Entity, ID>`
4. Use repo in service; tenant filtering via `TenantContext.getCurrentTenant()`
5. Create DTOs + Mapper with null-safe field handling
6. Create controller endpoint with `@PreAuthorize("@permissionEvaluator.hasPermission(...)")`

## Endpoint Security Patterns

**Open endpoints** (no auth required):
- `/api/auth/**` (tenant login/signup)
- `/api/platform/auth/**` (platform login)
- `/api/tenants/register`, `/api/forgot-password/**`
- `/swagger-ui/**`, `/v3/api-docs/**`

**Protected endpoints:**
- All other `/api/**` → require JWT token in `Authorization: Bearer <token>`
- Token must be valid and not blacklisted

## Common Pitfalls for AI Agents

1. **Forgetting tenant isolation**: Always filter Employee queries by `TenantContext.getCurrentTenant()`
2. **Hardcoded UUIDs**: Bootstrap creates default super-admin with email `admin@sonixhr.com`; credentials are `Admin@123`
3. **Mapper null checks**: Nested objects (like Manager in EmployeeResponse) need null-guards
4. **JSONB fields**: Use `@JdbcTypeCode(SqlTypes.JSON)` + hibernate-types-60 for custom fields
5. **ThreadLocal cleanup**: `TenantContext.clear()` should be called in filters on request end
6. **JWT claims**: Always extract tenantId from token claims, not just username

## Persistence & Queries

- **Repositories**: Standard Spring Data JPA
- **Transactions**: `@Transactional` on service methods that modify state
- **Lazy loading**: Most relationships use `FetchType.LAZY` (be cautious with detached entities)
- **Custom DB functions**: RLS relies on PostgreSQL `set_current_tenant()` function

## File Reference Quick Start

| Purpose | File Path |
|---------|-----------|
| Entry point | `SonixhrApplication.java` |
| Auth flow | `controller/TenantAuthController.java`, `security/JwtAuthFilter.java` |
| Tenant isolation | `tenant/TenantContext.java`, `tenant/TenantRLSService.java` |
| Exception handling | `exceptions/GlobalExceptionHandler.java` |
| Bootstrap data | `bootstrap/PlatformDataInitializer.java` |
| Security config | `config/SecurityConfig.java` |
| JWT logic | `security/JwtService.java` |
| Employee feature | `entity/Employee.java`, `service/*Employee*.java`, `dto/Employee*.java`, `controller/*Employee*.java` |

---

**Last Updated**: May 2026  
**Stack**: Spring Boot 3.1.5, Java 17, PostgreSQL, JWT, Multi-Tenant SaaS

