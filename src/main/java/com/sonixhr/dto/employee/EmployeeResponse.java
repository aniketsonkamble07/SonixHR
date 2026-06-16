package com.sonixhr.dto.employee;

import com.sonixhr.enums.*;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.enums.leave.WeekendConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class EmployeeResponse {

    // =====================================================
    // BASIC IDENTIFIERS
    // =====================================================
    private Long id;
    private Long tenantId;
    private String employeeCode;
    private UUID userId;
    private boolean isActive;
    // =====================================================
    // PERSONAL INFORMATION
    // =====================================================
    private String firstName;
    private String lastName;
    private String fullName;
    private String initials;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;
    private MaritalStatus maritalStatus;
    private BloodGroup bloodGroup;
    private String nationality;
    private String personalEmail;

    // =====================================================
    // PROFESSIONAL INFORMATION
    // =====================================================
    private DepartmentInfo department;
    private String position;
    private ManagerInfo manager;
    private EmploymentType employmentType;
    private String workLocation;
    private LocalDate hireDate;
    private Integer probationMonths;
    private LocalDate confirmationDate;
    private LocalDate resignationDate;
    private LocalDate lastWorkingDate;
    private Integer tenureInMonths;
    private EmployeeStatus status;

    // =====================================================
    // ADDRESS INFORMATION
    // =====================================================
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String permanentAddress;

    // =====================================================
    // EMERGENCY CONTACT
    // =====================================================
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;
    private String emergencyContactEmail;
    private String secondaryEmergencyName;
    private String secondaryEmergencyPhone;

    // =====================================================
    // PROFILE & DOCUMENTS
    // =====================================================
    private String profilePictureUrl;
    private Map<String, Object> bankDetails;
    private Map<String, Object> documents;
    private Map<String, Object> certifications;
    private Map<String, Object> customFields;

    // =====================================================
    // SOCIAL LINKS
    // =====================================================
    private String linkedinUrl;
    private String githubUrl;
    private String twitterUrl;

    private WeekendConfig weekendConfig;
    private String customWeekendDays;

    // =====================================================
    // ORGANIZATIONAL HIERARCHY
    // =====================================================
    private List<EmployeeResponse> directReports;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    // =====================================================
    // NESTED DTO FOR MANAGER INFO
    // =====================================================
    @Data
    @Builder
    public static class ManagerInfo {
        private Long id;
        private String fullName;
        private String email;
        private String position;
        private String department;
        private String employeeCode;
    }
    @Data
    @Builder
    public static class DepartmentInfo {
        private Long id;
        private String name;
        private String code;
    }

}