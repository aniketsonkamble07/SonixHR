package com.sonixhr.entity.employee;

import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.User;
import com.sonixhr.entity.tenant.Tenant;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

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
public class Employee {

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
    // USER ACCOUNT LINK
    // =====================================================
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // =====================================================
    // EMPLOYEE IDENTIFICATION
    // =====================================================
    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // =====================================================
    // PERSONAL INFORMATION
    // =====================================================
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

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

    public UUID getTenantId() {
        return tenant != null ? tenant.getId() : null;
    }

    public UUID getUserId() {
        return user != null ? user.getId() : null;
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
        return status == EmployeeStatus.ACTIVE;
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

    // =====================================================
    // BUSINESS METHODS
    // =====================================================
    public void activate() {
        this.status = EmployeeStatus.ACTIVE;
        this.resignationDate = null;
        this.lastWorkingDate = null;
    }

    public void putOnProbation() {
        this.status = EmployeeStatus.PROBATION;
    }

    public void resign(LocalDate resignationDate, LocalDate lastWorkingDate) {
        this.status = EmployeeStatus.RESIGNED;
        this.resignationDate = resignationDate;
        this.lastWorkingDate = lastWorkingDate;
    }

    public void terminate() {
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
}