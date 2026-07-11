package com.sonixhr.dto.employee;

import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.enums.employee.SalaryType;
import com.sonixhr.enums.leave.WeekendConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

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
    private IndianState workState;
    private String workStateText;
    private String workCountry;
    private Long managerId;
    private String managerCode;

    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    private Set<Long> roleIds;

    private WeekendConfig weekendConfig;
    private String customWeekendDays;

    private java.math.BigDecimal salary;
    private SalaryType salaryType; // MONTHLY or YEARLY
    private String currency;
    private String taxRegime;
    private Long shiftId;
    @jakarta.validation.Valid
    private BankAccountRequest bankDetails;
}