package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class GetTenantId {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java com.sonixhr.GetTenantId <dbUrl> <dbUser> <dbPassword> <email>");
            System.exit(1);
        }
        String url = args[0];
        String user = args[1];
        String dbPassword = args[2];
        String email = args[3];

        System.err.println("DEBUG: Connecting to DB URL: " + url);
        System.err.println("DEBUG: User: " + user);
        System.err.println("DEBUG: Email: " + email);

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("JDBC driver not found");
            System.exit(1);
        }
        try (Connection conn = DriverManager.getConnection(url, user, dbPassword);
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM tenants WHERE admin_email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.print(rs.getLong("id"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
