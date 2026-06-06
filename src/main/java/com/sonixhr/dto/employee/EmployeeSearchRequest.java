package com.sonixhr.dto.employee;

import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSearchRequest {

    // =====================================================
    // BASIC SEARCH
    // =====================================================

    /**
     * Search term for name, email, code (optional)
     * If provided, searches across firstName, lastName, email, employeeCode
     */
    private String searchTerm;

    // =====================================================
    // FILTERS
    // =====================================================

    /**
     * Filter by employee status
     * Example: ACTIVE, PROBATION, RESIGNED, TERMINATED, ON_LEAVE
     */
    private EmployeeStatus status;

    /**
     * Filter by department ID
     */
    private Long departmentId;

    /**
     * Filter by department name
     */
    private String departmentName;

    /**
     * Filter by employment type
     * Example: FULL_TIME, PART_TIME, CONTRACT, INTERN
     */
    private EmploymentType employmentType;

    /**
     * Filter by manager ID (get team members)
     */
    private Long managerId;

    /**
     * Filter by position/title
     */
    private String position;

    /**
     * Filter by work location
     */
    private String workLocation;

    // =====================================================
    // DATE RANGE FILTERS
    // =====================================================

    /**
     * Filter by hire date from
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hireDateFrom;

    /**
     * Filter by hire date to
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hireDateTo;

    // =====================================================
    // BOOLEAN FLAGS
    // =====================================================

    /**
     * Get only employees with no manager (top-level)
     */
    private Boolean noManagerOnly;

    /**
     * Get only employees who are managers (have direct reports)
     */
    private Boolean isManagerOnly;

    /**
     * Get only active employees (status = ACTIVE or PROBATION)
     */
    private Boolean activeOnly;

    // =====================================================
    // PAGINATION
    // =====================================================

    /**
     * Page number (0-indexed)
     */
    @Builder.Default
    private int page = 0;

    /**
     * Page size
     */
    @Builder.Default
    private int size = 20;

    /**
     * Sort field (e.g., "firstName", "employeeCode", "hireDate")
     */
    private String sortBy;

    /**
     * Sort direction (ASC or DESC)
     */
    @Builder.Default
    private String sortDirection = "ASC";

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public boolean hasSearchTerm() {
        return searchTerm != null && !searchTerm.trim().isEmpty();
    }

    public boolean hasStatusFilter() {
        return status != null;
    }

    public boolean hasDepartmentFilter() {
        return departmentId != null || (departmentName != null && !departmentName.trim().isEmpty());
    }

    public boolean hasManagerFilter() {
        return managerId != null;
    }

    public boolean hasDateRangeFilter() {
        return hireDateFrom != null || hireDateTo != null;
    }

    public String getTrimmedSearchTerm() {
        return searchTerm != null ? searchTerm.trim() : null;
    }

    public String getTrimmedDepartmentName() {
        return departmentName != null ? departmentName.trim() : null;
    }

    public String getTrimmedPosition() {
        return position != null ? position.trim() : null;
    }

    public String getTrimmedWorkLocation() {
        return workLocation != null ? workLocation.trim() : null;
    }
}