package com.sonixhr;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class DbActivateEmployees {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java DbActivateEmployees <dbUrl> <dbUser> <dbPassword> <tenantId>");
            System.exit(1);
        }

        String url = args[0];
        String user = args[1];
        String dbPassword = args[2];
        long tenantId = Long.parseLong(args[3]);

        System.out.println("Encoding password Admin@123...");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String passwordHash = encoder.encode("Admin@123");
        System.out.println("Encoded password hash successfully.");

        try (Connection conn = DriverManager.getConnection(url, user, dbPassword)) {
            System.out.println("Connected to database successfully!");

            // Update all employees under the tenant to ACTIVE with set password
            String empSql = "UPDATE employees SET password_hash = ?, is_active = true, status = 'ACTIVE' WHERE tenant_id = ? AND email != ?";
            try (PreparedStatement ps = conn.prepareStatement(empSql)) {
                ps.setString(1, passwordHash);
                ps.setLong(2, tenantId);
                ps.setString(3, "admin.89174@apexnexus.com"); // exclude tenant admin which is already activated
                int rows = ps.executeUpdate();
                System.out.println("Activated employees count: " + rows);
            }

            System.out.println("Employees activation completed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
