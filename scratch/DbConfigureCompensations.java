package com.sonixhr;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DbConfigureCompensations {

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java DbConfigureCompensations <dbUrl> <dbUser> <dbPassword> <tenantId> <employeeEmail:ctc,employeeEmail:ctc,...>");
            System.exit(1);
        }

        String dbUrl = args[0];
        String dbUser = args[1];
        String dbPassword = args[2];
        long tenantId = Long.parseLong(args[3]);
        String empCtcList = args[4];

        System.out.println("Running DB salary configuration override...");
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            System.out.println("Connected to database successfully!");

            // 0. Seed tenant_salary_structures if not already present
            UUID configId = null;
            String getConfigSql = "SELECT id FROM tenant_payroll_configs WHERE tenant_id = ? AND is_active = true";
            try (PreparedStatement stmt = conn.prepareStatement(getConfigSql)) {
                stmt.setLong(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        configId = (UUID) rs.getObject("id");
                    }
                }
            }

            if (configId != null) {
                // Delete existing structures for this config to avoid duplication
                String deleteStructSql = "DELETE FROM tenant_salary_structures WHERE tenant_payroll_config_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteStructSql)) {
                    stmt.setObject(1, configId);
                    stmt.executeUpdate();
                }

                String insertStructSql = "INSERT INTO tenant_salary_structures (id, tenant_id, tenant_payroll_config_id, component_code, calculation_type, value, evaluation_order, is_part_of_pf_wages, is_part_of_esi_wages, is_taxable, effective_from) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                // BASIC
                try (PreparedStatement stmt = conn.prepareStatement(insertStructSql)) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setLong(2, tenantId);
                    stmt.setObject(3, configId);
                    stmt.setString(4, "BASIC");
                    stmt.setString(5, "PERCENTAGE_OF_CTC");
                    stmt.setBigDecimal(6, new BigDecimal("50.00"));
                    stmt.setInt(7, 1);
                    stmt.setBoolean(8, true);
                    stmt.setBoolean(9, true);
                    stmt.setBoolean(10, true);
                    stmt.setDate(11, java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
                    stmt.executeUpdate();
                }

                // HRA
                try (PreparedStatement stmt = conn.prepareStatement(insertStructSql)) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setLong(2, tenantId);
                    stmt.setObject(3, configId);
                    stmt.setString(4, "HRA");
                    stmt.setString(5, "PERCENTAGE_OF_CTC");
                    stmt.setBigDecimal(6, new BigDecimal("20.00"));
                    stmt.setInt(7, 2);
                    stmt.setBoolean(8, false);
                    stmt.setBoolean(9, true);
                    stmt.setBoolean(10, true);
                    stmt.setDate(11, java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
                    stmt.executeUpdate();
                }

                // SA
                try (PreparedStatement stmt = conn.prepareStatement(insertStructSql)) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setLong(2, tenantId);
                    stmt.setObject(3, configId);
                    stmt.setString(4, "SA");
                    stmt.setString(5, "PERCENTAGE_OF_CTC");
                    stmt.setBigDecimal(6, new BigDecimal("30.00"));
                    stmt.setInt(7, 3);
                    stmt.setBoolean(8, false);
                    stmt.setBoolean(9, true);
                    stmt.setBoolean(10, true);
                    stmt.setDate(11, java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
                    stmt.executeUpdate();
                }
                System.out.println("Tenant salary structures seeded successfully in DB.");
            } else {
                System.out.println("Warning: Active tenant payroll config not found. Skipping structure seeding.");
            }

            // 1. Get salary component definitions for the tenant
            List<ComponentDefinition> components = new ArrayList<>();
            String getCompSql = "SELECT id, component_code, default_value, is_mandatory FROM salary_component_definitions WHERE tenant_id = ? AND is_active = true";
            try (PreparedStatement stmt = conn.prepareStatement(getCompSql)) {
                stmt.setLong(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ComponentDefinition cd = new ComponentDefinition();
                        cd.id = (UUID) rs.getObject("id");
                        cd.code = rs.getString("component_code");
                        cd.defaultValue = rs.getBigDecimal("default_value");
                        cd.isMandatory = rs.getBoolean("is_mandatory");
                        components.add(cd);
                    }
                }
            }
            System.out.println("Found " + components.size() + " active salary components for tenant.");

            // Parse employees and CTCs
            String[] pairs = empCtcList.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split(":");
                String email = parts[0];
                BigDecimal ctc = new BigDecimal(parts[1]);

                // Get employee ID
                long employeeId;
                String empSql = "SELECT id FROM employees WHERE tenant_id = ? AND email = ?";
                try (PreparedStatement stmt = conn.prepareStatement(empSql)) {
                    stmt.setLong(1, tenantId);
                    stmt.setString(2, email);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            employeeId = rs.getLong("id");
                        } else {
                            System.out.println("Employee not found for email: " + email);
                            continue;
                        }
                    }
                }

                // Deactivate any active profiles for this employee
                String deactivateSql = "UPDATE employee_salary_profiles SET is_active = false, effective_to = ? WHERE employee_id = ? AND is_active = true";
                try (PreparedStatement stmt = conn.prepareStatement(deactivateSql)) {
                    stmt.setDate(1, java.sql.Date.valueOf(LocalDate.of(2026, 5, 31)));
                    stmt.setLong(2, employeeId);
                    int updated = stmt.executeUpdate();
                    if (updated > 0) {
                        System.out.println("Deactivated " + updated + " active salary profiles for employee ID: " + employeeId);
                    }
                }

                // Get next version
                int nextVersion = 1;
                String versionSql = "SELECT COALESCE(MAX(version), 0) + 1 FROM employee_salary_profiles WHERE employee_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(versionSql)) {
                    stmt.setLong(1, employeeId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            nextVersion = rs.getInt(1);
                        }
                    }
                }

                // Create profile
                UUID profileId = UUID.randomUUID();
                String insertProfileSql = "INSERT INTO employee_salary_profiles (id, tenant_id, employee_id, version, monthly_ctc, currency, tax_regime, effective_from, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertProfileSql)) {
                    stmt.setObject(1, profileId);
                    stmt.setLong(2, tenantId);
                    stmt.setLong(3, employeeId);
                    stmt.setInt(4, nextVersion);
                    stmt.setBigDecimal(5, ctc);
                    stmt.setString(6, "INR");
                    stmt.setString(7, "NEW_REGIME");
                    stmt.setDate(8, java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
                    stmt.setBoolean(9, true);
                    stmt.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.executeUpdate();
                }
                System.out.println("Inserted salary profile for " + email + " (Version: " + nextVersion + ", Monthly CTC: " + ctc + ")");

                // Insert components
                String insertCompSql = "INSERT INTO employee_salary_components (id, tenant_id, salary_profile_id, component_code, override_type, override_value, is_enabled, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                for (ComponentDefinition cd : components) {
                    if (cd.isMandatory) {
                        UUID compId = UUID.randomUUID();
                        try (PreparedStatement stmt = conn.prepareStatement(insertCompSql)) {
                            stmt.setObject(1, compId);
                            stmt.setLong(2, tenantId);
                            stmt.setObject(3, profileId);
                            stmt.setString(4, cd.code);
                            stmt.setString(5, "VALUE");
                            stmt.setBigDecimal(6, cd.defaultValue != null ? cd.defaultValue : BigDecimal.ZERO);
                            stmt.setBoolean(7, true);
                            stmt.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                            stmt.executeUpdate();
                        }
                    }
                }

                // Insert history
                UUID historyId = UUID.randomUUID();
                String insertHistorySql = "INSERT INTO employee_salary_profile_history (id, profile_id, employee_id, version, monthly_ctc, effective_from, change_reason, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertHistorySql)) {
                    stmt.setObject(1, historyId);
                    stmt.setObject(2, profileId);
                    stmt.setLong(3, employeeId);
                    stmt.setInt(4, nextVersion);
                    stmt.setBigDecimal(5, ctc);
                    stmt.setDate(6, java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
                    stmt.setString(7, "NEW_VERSION_CREATED");
                    stmt.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.executeUpdate();
                }
            }

            System.out.println("All salary profiles configured successfully!");

        } catch (Exception e) {
            System.err.println("Salary configuration override failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static class ComponentDefinition {
        UUID id;
        String code;
        BigDecimal defaultValue;
        boolean isMandatory;
    }
}
