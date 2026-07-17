package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckAndActivate {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/sonixhr_db?ssl=false";
        String user = "postgres";
        String password = "root";

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC driver not found.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to database successfully!");

            // Search tenants
            System.out.println("\n--- SEARCHING TENANTS LIKE 'trident%' or 'aditya%' ---");
            String tenantsSql = "SELECT id, company_name, admin_email, is_active, status FROM tenants WHERE company_name ILIKE ? OR admin_email ILIKE ?";
            try (PreparedStatement ps = conn.prepareStatement(tenantsSql)) {
                ps.setString(1, "%trident%");
                ps.setString(2, "%aditya%");
                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = false;
                    while (rs.next()) {
                        found = true;
                        System.out.printf("Tenant ID: %d | Company: %s | Admin: %s | Active: %b | Status: %s\n",
                            rs.getLong("id"),
                            rs.getString("company_name"),
                            rs.getString("admin_email"),
                            rs.getBoolean("is_active"),
                            rs.getString("status")
                        );
                    }
                    if (!found) {
                        System.out.println("No matching tenants found.");
                    }
                }
            }

            // Search employees
            System.out.println("\n--- SEARCHING EMPLOYEES LIKE 'aditya%' ---");
            String employeesSql = "SELECT id, email, is_active, status, tenant_id FROM employees WHERE email ILIKE ?";
            try (PreparedStatement ps = conn.prepareStatement(employeesSql)) {
                ps.setString(1, "%aditya%");
                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = false;
                    while (rs.next()) {
                        found = true;
                        System.out.printf("Employee ID: %d | Email: %s | Active: %b | Status: %s | TenantId: %d\n",
                            rs.getLong("id"),
                            rs.getString("email"),
                            rs.getBoolean("is_active"),
                            rs.getString("status"),
                            rs.getLong("tenant_id")
                        );
                    }
                    if (!found) {
                        System.out.println("No matching employees found.");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
