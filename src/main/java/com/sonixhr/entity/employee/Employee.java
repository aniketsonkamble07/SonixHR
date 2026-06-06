package com.sonixhr.entity.employee;

import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.enums.*;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Entity
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

    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = false;

    // =====================================================
    // ROLES & PERMISSIONS (Tenant-level) - ✅ Changed to TenantRole
    // =====================================================
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "employee_roles",
            joinColumns = @JoinColumn(name = "employee_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<TenantRole> roles = new HashSet<>();

    // =====================================================
    // SECURITY & LOGIN TRACKING
    // =====================================================
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

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
    private Map<String, Object> bankDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> documents = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certifications", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> certifications = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> customFields = Map.of();

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
    // HELPER METHODS
    // =====================================================
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getInitials() {
        if (firstName == null || lastName == null) return "";
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
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public Set<String> getEffectivePermissions() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getPermission().name())
                .collect(Collectors.toSet());
    }

    public boolean hasPermission(String permissionName) {
        return getEffectivePermissions().contains(permissionName);
    }

    // =====================================================
    // SETTER METHODS (for activation)
    // =====================================================
    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    // =====================================================
    // BUSINESS METHODS
    // =====================================================
    public void activate() {
        this.isActive = true;
        this.status = EmployeeStatus.ACTIVE;
        this.resignationDate = null;
        this.lastWorkingDate = null;
    }

    public void putOnProbation() {
        this.status = EmployeeStatus.PROBATION;
    }

    public void resign(LocalDate resignationDate, LocalDate lastWorkingDate) {
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
        return department != null ? department.getName() : null;
    }

    public Long getDepartmentId() {
        return department != null ? department.getId() : null;
    }

    public Map<String, Object> getDocuments() {
        return documents != null ? documents : Map.of();
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    // =====================================================
    // UserDetails Implementation
    // =====================================================
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getEffectivePermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
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
        return !isAccountLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}