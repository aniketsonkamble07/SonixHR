package com.sonixhr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbVerifyData {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java DbVerifyData <dbUrl> <dbUser> <dbPassword>");
            System.exit(1);
        }

        String url = args[0];
        String user = args[1];
        String dbPassword = args[2];

        try (Connection conn = DriverManager.getConnection(url, user, dbPassword)) {
            System.out.println("Connected to database successfully!");

            // 1. Fetch attendance summary per employee
            System.out.println("\n--- Attendance Data Summary ---");
            String attSql = "SELECT e.first_name, e.last_name, COUNT(a.id) as att_count " +
                            "FROM employees e " +
                            "LEFT JOIN attendance_records a ON e.id = a.employee_id " +
                            "WHERE e.tenant_id = 1 " +
                            "GROUP BY e.first_name, e.last_name " +
                            "ORDER BY att_count DESC";
            try (PreparedStatement ps = conn.prepareStatement(attSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("Employee: " + rs.getString("first_name") + " " + rs.getString("last_name") + " | Attendance Records: " + rs.getInt("att_count"));
                }
            }

            // 2. Fetch payrun & payslips summary
            System.out.println("\n--- Payroll / Payrun Data Summary ---");
            String prSql = "SELECT id, month, year, status, processed_at FROM payruns WHERE tenant_id = 1";
            try (PreparedStatement ps = conn.prepareStatement(prSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("Payrun ID: " + rs.getObject("id") + " | Period: " + rs.getInt("month") + "/" + rs.getInt("year") + " | Status: " + rs.getString("status") + " | Processed At: " + rs.getTimestamp("processed_at"));
                }
            }

            System.out.println("\n--- Seeded Payslips Details ---");
            String psSql = "SELECT e.first_name, e.last_name, p.gross_earnings, p.total_deductions, p.net_pay " +
                           "FROM payslips p " +
                           "JOIN employees e ON p.employee_id = e.id " +
                           "WHERE p.tenant_id = 1";
            try (PreparedStatement ps = conn.prepareStatement(psSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("Employee: " + rs.getString("first_name") + " " + rs.getString("last_name") +
                                       " | Gross Earnings: ₹" + rs.getBigDecimal("gross_earnings") +
                                       " | Deductions: ₹" + rs.getBigDecimal("total_deductions") +
                                       " | Net Pay: ₹" + rs.getBigDecimal("net_pay"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
