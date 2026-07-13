package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class QueryRenderDb {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://dpg-d8lqhms8aovs73dtigbg-a.oregon-postgres.render.com:5432/sonixhr_db_lder?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory";
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

            String sql = "UPDATE employees SET is_active = true";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int rows = ps.executeUpdate();
                System.out.println("SUCCESS: Activated " + rows + " employees on Render!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
