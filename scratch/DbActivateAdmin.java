package com.sonixhr;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class DbActivateAdmin {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "jdbc:postgresql://localhost:5432/sonixhr_db";
        String user = args.length > 1 ? args[1] : "postgres";
        String password = args.length > 2 ? args[2] : "root";
        String adminEmail = args.length > 3 ? args[3] : "admin.89174@apexnexus.com";

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC driver not found.");
            return;
        }

        System.out.println("Encoding password...");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String passwordHash = encoder.encode("Admin@123");
        System.out.println("Encoded password hash successfully.");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to database successfully!");

            // Update employee
            String empSql = "UPDATE employees SET password_hash = ?, is_active = true, status = 'ACTIVE' WHERE email = ?";
            try (PreparedStatement ps = conn.prepareStatement(empSql)) {
                ps.setString(1, passwordHash);
                ps.setString(2, adminEmail);
                int rows = ps.executeUpdate();
                System.out.println("Updated employees rows: " + rows);
            }

            // Update tenant
            String tenantSql = "UPDATE tenants SET is_active = true, status = 'ACTIVE' WHERE admin_email = ?";
            try (PreparedStatement ps = conn.prepareStatement(tenantSql)) {
                ps.setString(1, adminEmail);
                int rows = ps.executeUpdate();
                System.out.println("Updated tenants rows: " + rows);
            }

            System.out.println("Activation completed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
