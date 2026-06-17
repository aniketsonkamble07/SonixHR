package com.sonixhr.entity.employee;

import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.attendance.ShiftConfiguration;
import com.sonixhr.enums.*;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.enums.leave.WeekendConfig;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Data
@Entity
@ToString(exclude = {"documents", "certifications", "customFields", "bankDetails"})
@Table(name = "employees", uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_code_tenant", columnNames = {"tenant_id", "employee_code"}),
        @UniqueConstraint(name = "uk_employee_email_tenant", columnNames = {"tenant_id", "email"})
}, indexes = {
        @Index(name = "idx_employees_tenant", columnList = "tenant_id"),
        @Index(name = "idx_employees_email", columnList = "email"),
        @Index(name = "idx_employees_code", columnList = "employee_code"),
        @Index(name = "idx_employees_manager", columnList = "manager_id"),
        @Index(name = "idx_employees_department", columnList = "department_id"),
        @Index(name = "idx_employees_status", columnList = "status"),
        @Index(name = "idx_employees_hire_date", columnList = "hire_date")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee implements UserDetails {

    // =====================================================
    // PRIMARY KEY
    // =====================================================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // TENANT RELATIONSHIP
    // =====================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // =====================================================
    // EMPLOYEE IDENTIFICATION & LOGIN CREDENTIALS
    // =====================================================
    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = false;

    // =====================================================
    // ROLES & PERMISSIONS
    // =====================================================
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "employee_roles",
            joinColumns = @JoinColumn(name = "employee_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<TenantRole> roles = new HashSet<>();

    // =====================================================
    // ROLES VERSION TRACKING (for cache invalidation)
    // =====================================================
    @Column(name = "roles_version")
    @Builder.Default
    private Integer rolesVersion = 1;

    // =====================================================
    // CACHED AUTHORITIES (transient - not stored in DB)
    // =====================================================
    @Transient
    private Collection<? extends GrantedAuthority> cachedAuthorities;

    @Transient
    private Integer cachedRolesVersion;

    // =====================================================
    // LOGIN TRACKING
    // =====================================================
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Column(name = "must_change_password")
    @Builder.Default
    private boolean mustChangePassword = false;

    // =====================================================
    // PERSONAL INFORMATION
    // =====================================================
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 5)
    private BloodGroup bloodGroup;

    @Column(length = 50)
    private String nationality;

    @Column(name = "personal_email", length = 255)
    private String personalEmail;

    // =====================================================
    // PROFESSIONAL INFORMATION
    // =====================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private ShiftConfiguration shift;

    @Column(length = 100)
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(name = "work_location", length = 200)
    private String workLocation;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "probation_months")
    @Builder.Default
    private Integer probationMonths = 3;

    @Column(name = "confirmation_date")
    private LocalDate confirmationDate;

    @Column(name = "resignation_date")
    private LocalDate resignationDate;

    @Column(name = "last_working_date")
    private LocalDate lastWorkingDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.PROBATION;

    // =====================================================
    // ADDRESS INFORMATION
    // =====================================================
    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 50)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    // =====================================================
    // EMERGENCY CONTACT
    // =====================================================
    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relation", length = 50)
    private String emergencyContactRelation;

    @Column(name = "emergency_contact_email", length = 255)
    private String emergencyContactEmail;

    @Column(name = "secondary_emergency_name", length = 200)
    private String secondaryEmergencyName;

    @Column(name = "secondary_emergency_phone", length = 20)
    private String secondaryEmergencyPhone;

    // =====================================================
    // PROFILE & DOCUMENTS
    // =====================================================
    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bank_details", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> bankDetails = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> documents = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certifications", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> certifications = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> customFields = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "weekend_config")
    private WeekendConfig weekendConfig;

    @Column(name = "custom_weekend_days")
    private String customWeekendDays;

    // =====================================================
    // SOCIAL LINKS
    // =====================================================
    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "github_url", length = 255)
    private String githubUrl;

    @Column(name = "twitter_url", length = 255)
    private String twitterUrl;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // ROLES VERSION METHODS
    // =====================================================

    public void incrementRolesVersion() {
        this.rolesVersion = (this.rolesVersion == null ? 1 : this.rolesVersion + 1);
    }

    public Integer getRolesVersion() {
        return rolesVersion;
    }

    public void setRolesVersion(Integer rolesVersion) {
        this.rolesVersion = rolesVersion;
    }

    public void clearAuthoritiesCache() {
        this.cachedAuthorities = null;
        this.cachedRolesVersion = null;
    }

    public Collection<? extends GrantedAuthority> getCachedAuthorities() {
        return cachedAuthorities;
    }

    public void setCachedAuthorities(Collection<? extends GrantedAuthority> cachedAuthorities) {
        this.cachedAuthorities = cachedAuthorities;
    }

    public Integer getCachedRolesVersion() {
        return cachedRolesVersion;
    }

    public void setCachedRolesVersion(Integer cachedRolesVersion) {
        this.cachedRolesVersion = cachedRolesVersion;
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public String getInitials() {
        if (firstName == null || firstName.isEmpty() || lastName == null || lastName.isEmpty()) {
            return "";
        }
        return (firstName.charAt(0) + "" + lastName.charAt(0)).toUpperCase();
    }

    public Long getTenantId() {
        return tenant != null ? tenant.getId() : null;
    }

    public int getTenureInMonths() {
        if (hireDate == null) return 0;
        return (int) ChronoUnit.MONTHS.between(hireDate, LocalDate.now());
    }

    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isOnProbation() {
        return status == EmployeeStatus.PROBATION;
    }

    public boolean isResigned() {
        return status == EmployeeStatus.RESIGNED;
    }

    public boolean isTerminated() {
        return status == EmployeeStatus.TERMINATED;
    }

    public boolean isOnLeave() {
        return status == EmployeeStatus.ON_LEAVE;
    }

    public boolean isAccountLocked() {
        return false;
    }

    // =====================================================
    // PERMISSION METHODS (Single definition)
    // =====================================================

    public Set<String> getEffectivePermissions() {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }

        return roles.stream()
                .filter(Objects::nonNull)
                .filter(role -> role.getPermissions() != null)
                .flatMap(role -> role.getPermissions().stream())
                .filter(Objects::nonNull)
                .map(TenantPermission::getPermission)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public boolean hasPermission(String permissionName) {
        if (permissionName == null || permissionName.isEmpty()) {
            return false;
        }
        return getEffectivePermissions().contains(permissionName);
    }

    // Convenience method for enum-based permission check
    public boolean hasPermission(TenantPermissionEnum permission) {
        if (permission == null) {
            return false;
        }
        return hasPermission(permission.name());
    }

    public boolean hasAnyPermission(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) {
            return false;
        }
        Set<String> perms = getEffectivePermissions();
        return Arrays.stream(permissionNames).anyMatch(perms::contains);
    }

    public boolean hasAnyPermission(TenantPermissionEnum... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        Set<String> permNames = Arrays.stream(permissions)
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toSet());
        return getEffectivePermissions().stream().anyMatch(permNames::contains);
    }

    public boolean hasAllPermissions(String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) {
            return true;
        }
        Set<String> perms = getEffectivePermissions();
        return Arrays.stream(permissionNames).allMatch(perms::contains);
    }

    // =====================================================
    // SETTER METHODS
    // =====================================================
    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
        this.passwordHash = passwordHash;
    }

    // =====================================================
    // BUSINESS METHODS
    // =====================================================
    public void activate() {
        if (passwordHash == null || passwordHash.isEmpty()) {
            throw new IllegalStateException("Cannot activate employee without password");
        }
        this.isActive = true;
        this.status = EmployeeStatus.ACTIVE;
        this.resignationDate = null;
        this.lastWorkingDate = null;
    }

    public void putOnProbation() {
        this.status = EmployeeStatus.PROBATION;
    }

    public void resign(LocalDate resignationDate, LocalDate lastWorkingDate) {
        if (resignationDate == null || lastWorkingDate == null) {
            throw new IllegalArgumentException("Resignation date and last working date are required");
        }
        this.isActive = false;
        this.status = EmployeeStatus.RESIGNED;
        this.resignationDate = resignationDate;
        this.lastWorkingDate = lastWorkingDate;
    }

    public void terminate() {
        this.isActive = false;
        this.status = EmployeeStatus.TERMINATED;
        this.lastWorkingDate = LocalDate.now();
    }

    public void confirmEmployee() {
        if (status == EmployeeStatus.PROBATION) {
            this.status = EmployeeStatus.ACTIVE;
            this.confirmationDate = LocalDate.now();
        }
    }

    public boolean isConfirmationPending() {
        return confirmationDate == null && status == EmployeeStatus.PROBATION;
    }

    public String getDepartmentName() {
        return department != null ? department.getName() : "";
    }

    public Long getDepartmentId() {
        return department != null ? department.getId() : null;
    }

    public Map<String, Object> getDocuments() {
        return documents != null ? documents : new HashMap<>();
    }

    public Map<String, Object> getCertifications() {
        return certifications != null ? certifications : new HashMap<>();
    }

    public Map<String, Object> getCustomFields() {
        return customFields != null ? customFields : new HashMap<>();
    }

    public Map<String, Object> getBankDetails() {
        return bankDetails != null ? bankDetails : new HashMap<>();
    }

    // =====================================================
    // UserDetails Implementation (Single definition)
    // =====================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Return cached authorities if available and version matches
        if (cachedAuthorities != null && cachedRolesVersion != null &&
                cachedRolesVersion.equals(this.rolesVersion)) {
            return cachedAuthorities;
        }

        Set<String> permissions = getEffectivePermissions();
        if (permissions.isEmpty()) {
            return Collections.emptySet();
        }

        Collection<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        // Cache the authorities
        this.cachedAuthorities = authorities;
        this.cachedRolesVersion = this.rolesVersion;

        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !mustChangePassword;
    }

    @Override
    public boolean isEnabled() {
        return isActive && status == EmployeeStatus.ACTIVE;
    }

    // =====================================================
    // ROLE CHECK METHODS
    // =====================================================

    public boolean isSuperAdmin() {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() != null &&
                        ("Super Admin".equals(role.getName()) || "SUPER_ADMIN".equalsIgnoreCase(role.getName())));
    }

    public boolean isAdmin() {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() != null &&
                        ("Admin".equals(role.getName()) || "ADMIN".equalsIgnoreCase(role.getName())));
    }

    public boolean isManager() {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() != null &&
                        ("Manager".equals(role.getName()) || "MANAGER".equalsIgnoreCase(role.getName())));
    }

    public boolean isEmployee() {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() != null &&
                        ("Employee".equals(role.getName()) || "EMPLOYEE".equalsIgnoreCase(role.getName())));
    }

    public boolean hasRole(String roleName) {
        if (roles == null || roles.isEmpty() || roleName == null || roleName.isEmpty()) {
            return false;
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() != null && role.getName().equalsIgnoreCase(roleName));
    }

    public boolean hasAnyRole(String... roleNames) {
        if (roles == null || roles.isEmpty() || roleNames == null || roleNames.length == 0) {
            return false;
        }
        Set<String> roleSet = new HashSet<>(Arrays.asList(roleNames));
        return roles.stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() != null && roleSet.contains(role.getName()));
    }

    // =====================================================
    // JPA LIFECYCLE METHODS
    // =====================================================

    @PrePersist
    @PreUpdate
    private void validate() {
        if (isActive && (passwordHash == null || passwordHash.isEmpty())) {
            throw new IllegalStateException("Active employee must have a password hash");
        }

        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalStateException("Valid email is required");
        }

        if (employeeCode == null || employeeCode.isEmpty()) {
            throw new IllegalStateException("Employee code is required");
        }

        if (firstName == null || firstName.isEmpty()) {
            throw new IllegalStateException("First name is required");
        }

        if (lastName == null || lastName.isEmpty()) {
            throw new IllegalStateException("Last name is required");
        }

        if (isActive && (roles == null || roles.isEmpty())) {
            throw new IllegalStateException("Active employee must have at least one role");
        }
    }
}