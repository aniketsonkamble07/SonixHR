package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbCheckRows {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "jdbc:postgresql://localhost:5432/sonixhr_db";
        String user = args.length > 1 ? args[1] : "postgres";
        String password = args.length > 2 ? args[2] : "root";

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC driver not found.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            System.out.println("Connected to database successfully!");

            System.out.println("\n--- ROWS IN subscription_plans ---");
            try (ResultSet rs = stmt.executeQuery("SELECT id, code, name, is_trial FROM subscription_plans")) {
                while (rs.next()) {
                    System.out.printf("ID: %d | Code: %s | Name: %s | IsTrial: %b\n",
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBoolean("is_trial")
                    );
                }
            }

            System.out.println("\n--- ROWS IN tenant_permissions (First 5) ---");
            try (ResultSet rs = stmt.executeQuery("SELECT id, permission, category FROM tenant_permissions LIMIT 5")) {
                while (rs.next()) {
                    System.out.printf("ID: %d | Permission: %s | Category: %s\n",
                        rs.getLong("id"),
                        rs.getString("permission"),
                        rs.getString("category")
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
