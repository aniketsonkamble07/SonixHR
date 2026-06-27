package com.sonixhr.dto.employee;

import com.sonixhr.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeProfileUpdateRequest {

    // Personal Information
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;
    private MaritalStatus maritalStatus;
    private BloodGroup bloodGroup;
    private String nationality;
    private String personalEmail;

    // Address Information
    private String address;
    private String city;
    private IndianState state;
    private String country;
    private String postalCode;
    private String permanentAddress;

    // Emergency Contact
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;
    private String emergencyContactEmail;
    private String secondaryEmergencyName;
    private String secondaryEmergencyPhone;

    // Bank Details
    private Map<String, Object> bankDetails;

    // Social Links
    private String linkedinUrl;
    private String githubUrl;
    private String twitterUrl;

    // =====================================================
    // ONLY SUPER ADMIN / HR CAN UPDATE
    // =====================================================
    private Long departmentId;
    private String position;
    private String workLocation;
    private Long managerId;



}