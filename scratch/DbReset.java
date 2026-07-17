package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DbReset {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "jdbc:postgresql://localhost:5432/sonixhr_db";
        String user = args.length > 1 ? args[1] : "postgres";
        String password = args.length > 2 ? args[2] : "root";

        System.out.println("Target Database URL: " + url.replaceAll(":([^/]+)@", ":****@")); // Mask password if present in URL

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC driver not found.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            System.out.println("Connected to database successfully!");

            System.out.println("Dropping public schema...");
            stmt.execute("DROP SCHEMA public CASCADE");

            System.out.println("Recreating public schema...");
            stmt.execute("CREATE SCHEMA public");
            stmt.execute("GRANT ALL ON SCHEMA public TO postgres");
            stmt.execute("GRANT ALL ON SCHEMA public TO public");

            System.out.println("Database reset completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
