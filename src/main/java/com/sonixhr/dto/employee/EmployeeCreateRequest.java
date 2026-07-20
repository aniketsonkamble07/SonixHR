package com.sonixhr.dto.employee;

import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.enums.employee.SalaryType;
import com.sonixhr.enums.leave.WeekendConfig;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public IndianState getWorkState() { return workState; }
    public void setWorkState(IndianState workState) { this.workState = workState; }
    public String getWorkStateText() { return workStateText; }
    public void setWorkStateText(String workStateText) { this.workStateText = workStateText; }
    public String getWorkCountry() { return workCountry; }
    public void setWorkCountry(String workCountry) { this.workCountry = workCountry; }
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
    public String getManagerCode() { return managerCode; }
    public void setManagerCode(String managerCode) { this.managerCode = managerCode; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public Set<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(Set<Long> roleIds) { this.roleIds = roleIds; }
    public WeekendConfig getWeekendConfig() { return weekendConfig; }
    public void setWeekendConfig(WeekendConfig weekendConfig) { this.weekendConfig = weekendConfig; }
    public String getCustomWeekendDays() { return customWeekendDays; }
    public void setCustomWeekendDays(String customWeekendDays) { this.customWeekendDays = customWeekendDays; }
    public java.math.BigDecimal getSalary() { return salary; }
    public void setSalary(java.math.BigDecimal salary) { this.salary = salary; }
    public SalaryType getSalaryType() { return salaryType; }
    public void setSalaryType(SalaryType salaryType) { this.salaryType = salaryType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getTaxRegime() { return taxRegime; }
    public void setTaxRegime(String taxRegime) { this.taxRegime = taxRegime; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public BankAccountRequest getBankDetails() { return bankDetails; }
    public void setBankDetails(BankAccountRequest bankDetails) { this.bankDetails = bankDetails; }
}