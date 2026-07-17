package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbVerifyIndex {
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

            // Query indexes
            System.out.println("\n--- DATABASE INDEXES (pg_indexes) ---");
            String indexSql = "SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename, indexname";
            try (ResultSet rs = stmt.executeQuery(indexSql)) {
                int count = 0;
                while (rs.next()) {
                    System.out.printf("Table: %-30s | Index: %-30s | Definition: %s\n", 
                        rs.getString("tablename"), 
                        rs.getString("indexname"), 
                        rs.getString("indexdef")
                    );
                    count++;
                }
                System.out.println("Total Indexes Found: " + count);
            }

            // Query constraints
            System.out.println("\n--- DATABASE CONSTRAINTS (information_schema.table_constraints) ---");
            String constraintSql = "SELECT table_name, constraint_name, constraint_type " +
                                   "FROM information_schema.table_constraints " +
                                   "WHERE table_schema = 'public' " +
                                   "ORDER BY table_name, constraint_type";
            try (ResultSet rs = stmt.executeQuery(constraintSql)) {
                int count = 0;
                while (rs.next()) {
                    System.out.printf("Table: %-30s | Constraint: %-40s | Type: %s\n", 
                        rs.getString("table_name"), 
                        rs.getString("constraint_name"), 
                        rs.getString("constraint_type")
                    );
                    count++;
                }
                System.out.println("Total Constraints Found: " + count);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
