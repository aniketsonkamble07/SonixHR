package com.sonixhr.service.employee;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeCodeGenerator {

    private final EmployeeRepository employeeRepository;

    /**
     * Generate employee code based on tenant
     * Format: {TenantCode}{Sequential Number}
     * Example: ACME001, ACME002
     */
    public String generateSequentialCode(Tenant tenant) {
        String prefix = getTenantPrefix(tenant);

        // Get the latest employee code for this tenant
        String lastCode = employeeRepository.findLastEmployeeCodeByTenant(tenant.getId());

        int nextNumber = 1;
        if (lastCode != null && lastCode.startsWith(prefix)) {
            String numberPart = lastCode.substring(prefix.length());
            try {
                nextNumber = Integer.parseInt(numberPart) + 1;
            } catch (NumberFormatException e) {
                nextNumber = 1;
            }
        }

        // Use 3 digits to ensure unique codes, but ensure total length <= 50
        return String.format("%s%03d", prefix, nextNumber);
    }

    /**
     * Get tenant prefix from tenant code or company name
     * Keep prefix short (max 4 chars) to fit within VARCHAR limit
     */
    private String getTenantPrefix(Tenant tenant) {
        // Use tenant code
        if (tenant.getTenantCode() != null && !tenant.getTenantCode().isEmpty()) {
            String cleaned = tenant.getTenantCode().toUpperCase()
                    .replaceAll("[^A-Z0-9]", "");
            // Take first 4 characters, but ensure total code length <= 50
            return cleaned.substring(0, Math.min(4, cleaned.length()));
        }

        // Fallback to company name
        if (tenant.getCompanyName() != null && !tenant.getCompanyName().isEmpty()) {
            String cleaned = tenant.getCompanyName().toUpperCase()
                    .replaceAll("[^A-Z]", "");
            return cleaned.substring(0, Math.min(4, cleaned.length()));
        }

        return "EMP";
    }

    /**
     * Generate employee code with year
     * Format: {TenantCode}-{Year}-{Sequential}
     * Example: ACME-2026-001
     */
    public String generateCodeWithYear(Tenant tenant) {
        String prefix = getTenantPrefix(tenant);
        int currentYear = Year.now().getValue();

        String lastCode = employeeRepository.findLastEmployeeCodeByTenantAndYear(
                tenant.getId(), currentYear, prefix);

        int nextNumber = 1;
        if (lastCode != null && lastCode.contains("-")) {
            String[] parts = lastCode.split("-");
            if (parts.length == 3) {
                try {
                    nextNumber = Integer.parseInt(parts[2]) + 1;
                } catch (NumberFormatException e) {
                    nextNumber = 1;
                }
            }
        }

        return String.format("%s-%d-%03d", prefix, currentYear, nextNumber);
    }

    /**
     * Generate department-based employee code
     * Format: {DeptCode}-{Year}-{Sequential}
     * Example: ENG-2026-001
     */
    public String generateDepartmentBasedCode(Tenant tenant, String department) {
        String deptCode = getDepartmentCode(department);
        int currentYear = Year.now().getValue();

        String lastCode = employeeRepository.findLastEmployeeCodeByTenantAndDepartment(
                tenant.getId(), department, currentYear, deptCode);

        int nextNumber = 1;
        if (lastCode != null && lastCode.contains("-")) {
            String[] parts = lastCode.split("-");
            if (parts.length == 3) {
                try {
                    nextNumber = Integer.parseInt(parts[2]) + 1;
                } catch (NumberFormatException e) {
                    nextNumber = 1;
                }
            }
        }

        return String.format("%s-%d-%03d", deptCode, currentYear, nextNumber);
    }

    /**
     * Get department code
     */
    private String getDepartmentCode(String department) {
        if (department == null || department.isEmpty()) {
            return "GEN";
        }

        return switch (department.toUpperCase()) {
            case "ENGINEERING" -> "ENG";
            case "HUMAN RESOURCES", "HR" -> "HR";
            case "SALES" -> "SAL";
            case "MARKETING" -> "MKT";
            case "FINANCE" -> "FIN";
            case "OPERATIONS" -> "OPS";
            case "IT", "INFORMATION TECHNOLOGY" -> "IT";
            case "PRODUCT" -> "PRD";
            case "DESIGN" -> "DES";
            case "LEGAL" -> "LEG";
            case "ADMINISTRATION" -> "ADM";
            default -> {
                String deptUpper = department.toUpperCase();
                yield deptUpper.substring(0, Math.min(3, deptUpper.length()));
            }
        };
    }
}