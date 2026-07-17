package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class QueryRenderDb {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://dpg-d8lqhms8aovs73dtigbg.oregon-postgres.render.com:5432/sonixhr_db_lder?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory";
        String user = "sonixhr_db_lder_user";
        String password = "693FgNnsgnIF91M5tdz8Cvys1fG04Dx8";

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to Render database successfully!");

            // Check tenant
            String tenantSql = "SELECT id, company_name, admin_email, status, is_active FROM tenants WHERE admin_email = ?";
            try (PreparedStatement ps = conn.prepareStatement(tenantSql)) {
                ps.setString(1, "aditya.sharma@tridenttech.co.in");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("\n--- RENDER TENANT ---");
                        System.out.println("ID: " + rs.getLong("id"));
                        System.out.println("Company: " + rs.getString("company_name"));
                        System.out.println("Status: " + rs.getString("status"));
                        System.out.println("Is Active: " + rs.getBoolean("is_active"));
                    } else {
                        System.out.println("\nTenant not found on Render.");
                    }
                }
            }

            // Check employee
            String empSql = "SELECT id, first_name, last_name, email, is_active, status FROM employees WHERE email = ?";
            try (PreparedStatement ps = conn.prepareStatement(empSql)) {
                ps.setString(1, "aditya.sharma@tridenttech.co.in");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("\n--- RENDER EMPLOYEE ---");
                        System.out.println("ID: " + rs.getLong("id"));
                        System.out.println("Name: " + rs.getString("first_name") + " " + rs.getString("last_name"));
                        System.out.println("Is Active: " + rs.getBoolean("is_active"));
                        System.out.println("Status: " + rs.getString("status"));
                    } else {
                        System.out.println("\nEmployee not found on Render.");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
