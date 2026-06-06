package com.sonixhr.service.employee;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeCodeGenerator {

    private final EmployeeRepository employeeRepository;

    private static final int SEQUENTIAL_DIGITS = 4;

    /**
     * Generate employee code
     * Format: {Prefix}{Year}{Sequential Number}
     * Example: ACME20260001, ACME20260002
     *
     * This single method handles duplicate names by using sequential numbering
     * regardless of name similarity.
     */
    public String generateEmployeeCode(Tenant tenant) {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant cannot be null");
        }

        // Get tenant prefix (e.g., ACME from tenant code or company name)
        String prefix = getTenantPrefix(tenant);

        // Get current year
        int currentYear = Year.now().getValue();

        // Find the last employee code for this tenant
        String lastCode = employeeRepository.findLastEmployeeCodeByTenant(tenant.getId());

        // Calculate next sequential number
        int nextNumber = getNextNumber(lastCode, prefix, currentYear);

        // Generate code: PREFIX + YEAR + SEQUENTIAL_NUMBER
        // Example: ACME20260001
        return String.format("%s%d%0" + SEQUENTIAL_DIGITS + "d", prefix, currentYear, nextNumber);
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    /**
     * Get next sequential number from last code
     */
    private int getNextNumber(String lastCode, String prefix, int currentYear) {
        int nextNumber = 1;

        // Expected pattern: PREFIX + YEAR + NUMBER
        // Example: ACME20260001
        String expectedStart = prefix + currentYear;

        if (lastCode != null && lastCode.startsWith(expectedStart)) {
            String numberPart = lastCode.substring(expectedStart.length());
            try {
                nextNumber = Integer.parseInt(numberPart) + 1;
                // Reset if exceeds 9999
                if (nextNumber > 9999) {
                    nextNumber = 1;
                    log.warn("Employee code exceeded limit, resetting to 1 for tenant");
                }
            } catch (NumberFormatException e) {
                log.warn("Failed to parse number from code: {}, using default 1", lastCode);
                nextNumber = 1;
            }
        }

        return nextNumber;
    }

    /**
     * Get tenant prefix from tenant code or company name
     */
    private String getTenantPrefix(Tenant tenant) {
        // Try tenant code first
        if (tenant.getTenantCode() != null && !tenant.getTenantCode().isEmpty()) {
            String cleaned = tenant.getTenantCode().toUpperCase()
                    .replaceAll("[^A-Z0-9]", "");
            if (!cleaned.isEmpty()) {
                // Take first 4 characters
                return cleaned.length() > 4 ? cleaned.substring(0, 4) : cleaned;
            }
        }

        // Fallback to company name
        if (tenant.getCompanyName() != null && !tenant.getCompanyName().isEmpty()) {
            String cleaned = tenant.getCompanyName().toUpperCase()
                    .replaceAll("[^A-Z]", "");
            if (!cleaned.isEmpty()) {
                return cleaned.length() > 4 ? cleaned.substring(0, 4) : cleaned;
            }
        }

        // Default prefix
        return "EMP";
    }
}