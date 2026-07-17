package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbCheckData {
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

            // 1. List all tables in public schema
            System.out.println("\n--- TABLES ---");
            String tablesSql = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name";
            try (ResultSet rs = stmt.executeQuery(tablesSql)) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    // Count rows
                    try (Statement countStmt = conn.createStatement();
                         ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM \"" + tableName + "\"")) {
                        if (countRs.next()) {
                            System.out.printf("Table: %-30s | Rows: %d\n", tableName, countRs.getInt(1));
                        }
                    } catch (Exception e) {
                        System.out.printf("Table: %-30s | Error counting rows: %s\n", tableName, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
