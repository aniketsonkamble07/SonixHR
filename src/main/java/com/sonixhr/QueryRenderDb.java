package com.sonixhr;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;

public class QueryRenderDb {
    public static void main(String[] args) {
        Properties props = new Properties();
        File envFile = new File(".env");
        if (!envFile.exists()) {
            envFile = new File("../.env");
        }
        if (!envFile.exists()) {
            envFile = new File("../../.env");
        }
        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int equalsIdx = line.indexOf('=');
                    if (equalsIdx > 0) {
                        String key = line.substring(0, equalsIdx).trim();
                        String val = line.substring(equalsIdx + 1).trim();
                        props.setProperty(key, val);
                    }
                }
            } catch (IOException e) {
                System.err.println("Warning: Failed to load .env file from " + envFile.getAbsolutePath());
            }
        }

        String url = getEnvOrProperty("RENDER_DB_URL", props);
        if (url == null || url.trim().isEmpty()) {
            System.err.println("ERROR: Database URL (RENDER_DB_URL) is not configured in environment or .env file.");
            System.exit(1);
        }
        String user = getEnvOrProperty("RENDER_DB_USER", props);
        if (user == null || user.trim().isEmpty()) {
            System.err.println("ERROR: Database user (RENDER_DB_USER) is not configured in environment or .env file.");
            System.exit(1);
        }
        String password = getEnvOrProperty("RENDER_DB_PASSWORD", props);
        if (password == null || password.trim().isEmpty()) {
            System.err.println("ERROR: Database password (RENDER_DB_PASSWORD) is not configured in environment or .env file.");
            System.exit(1);
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

    private static String getEnvOrProperty(String key, Properties props) {
        String val = System.getenv(key);
        if (val == null && props != null) {
            val = props.getProperty(key);
        }
        if (val != null) {
            val = val.trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
        }
        return val;
    }
}
