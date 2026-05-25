package com.sonixhr.dto.employee;

import com.sonixhr.enums.employee.EmploymentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCreateRequest {

    // =====================================================
    // REQUIRED FIELDS (HR MUST FILL)
    // =====================================================

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @NotBlank(message = "Position is required")
    private String position;

    @NotNull(message = "Hire date is required")
    private LocalDate hireDate;

    // =====================================================
    // OPTIONAL FIELDS (HR CAN FILL IF AVAILABLE)
    // =====================================================

    private String phone;
    private String workLocation;
    private Long managerId;

    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Builder.Default
    private Integer probationMonths = 3;

}