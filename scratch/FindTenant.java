package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FindTenant {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/sonixhr_db";
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

            String sql = "SELECT id, company_name, admin_email, status, is_active FROM tenants WHERE admin_email = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "aditya.sharma@tridenttech.co.in");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("\n--- FOUND TENANT ---");
                        System.out.println("ID: " + rs.getLong("id"));
                        System.out.println("Company Name: " + rs.getString("company_name"));
                        System.out.println("Admin Email: " + rs.getString("admin_email"));
                        System.out.println("Status: " + rs.getString("status"));
                        System.out.println("Is Active: " + rs.getBoolean("is_active"));
                    } else {
                        System.out.println("\nTenant not found with admin email: aditya.sharma@tridenttech.co.in");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
