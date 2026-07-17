package com.sonixhr;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class DbInspector {

    static class ExpectedTable {
        String tableName;
        String entityName;
        Set<String> columns = new HashSet<>();
    }

    public static void main(String[] args) {
        String srcPath = "E:\\Viplora\\sonixhr\\src\\main\\java";
        File entityDir = new File(srcPath + "\\com\\sonixhr\\entity");
        if (!entityDir.exists()) {
            System.err.println("Entity directory does not exist: " + entityDir.getAbsolutePath());
            return;
        }

        Map<String, ExpectedTable> expectedTables = new HashMap<>();

        // Walk and parse JPA Entities
        walkAndParseEntities(entityDir, expectedTables);

        // Connect to DB and inspect schema
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
            System.out.println("Successfully connected to the database: " + url);
            DatabaseMetaData metaData = conn.getMetaData();

            // Get all user tables in public schema
            List<String> dbTables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Exclude flyway / system tables
                    if (!tableName.equals("flyway_schema_history")) {
                        dbTables.add(tableName);
                    }
                }
            }

            System.out.println("\n--- DATABASE TABLES COMPARISON ---");
            System.out.println("Total tables in DB: " + dbTables.size());
            System.out.println("Total expected tables in code: " + expectedTables.size());

            List<String> unnecessaryTables = new ArrayList<>();
            for (String dbTable : dbTables) {
                if (!expectedTables.containsKey(dbTable)) {
                    unnecessaryTables.add(dbTable);
                }
            }

            System.out.println("\nUnnecessary tables found in DB (not present in code):");
            if (unnecessaryTables.isEmpty()) {
                System.out.println("None");
            } else {
                for (String t : unnecessaryTables) {
                    System.out.println("- " + t);
                }
            }

            System.out.println("\n--- DATABASE COLUMNS COMPARISON ---");
            Map<String, List<String>> unnecessaryColumnsMap = new LinkedHashMap<>();

            for (String dbTable : dbTables) {
                if (unnecessaryTables.contains(dbTable)) {
                    continue;
                }

                ExpectedTable expected = expectedTables.get(dbTable);
                Set<String> dbColumns = new HashSet<>();
                try (ResultSet rs = metaData.getColumns(null, "public", dbTable, "%")) {
                    while (rs.next()) {
                        dbColumns.add(rs.getString("COLUMN_NAME"));
                    }
                }

                List<String> unnecessaryColumns = new ArrayList<>();
                for (String dbCol : dbColumns) {
                    if (!expected.columns.contains(dbCol)) {
                        // Special handling: id column is always expected, and sometimes standard fields
                        if (dbCol.equals("id") || dbCol.equals("created_at") || dbCol.equals("updated_at") || dbCol.equals("tenant_id")) {
                            continue;
                        }
                        unnecessaryColumns.add(dbCol);
                    }
                }

                if (!unnecessaryColumns.isEmpty()) {
                    unnecessaryColumnsMap.put(dbTable, unnecessaryColumns);
                }
            }

            if (unnecessaryColumnsMap.isEmpty()) {
                System.out.println("No unnecessary columns found in active tables.");
            } else {
                for (Map.Entry<String, List<String>> entry : unnecessaryColumnsMap.entrySet()) {
                    System.out.println("Table: " + entry.getKey());
                    for (String col : entry.getValue()) {
                        System.out.println("  - " + col);
                    }
                }
            }

            // Perform SQL cleanup if needed
            if (!unnecessaryTables.isEmpty() || !unnecessaryColumnsMap.isEmpty()) {
                System.out.println("\n--- SQL CLEANUP SUITE SUGGESTED ---");
                try (Statement stmt = conn.createStatement()) {
                    // Cleanup unnecessary tables
                    for (String t : unnecessaryTables) {
                        String dropSql = "DROP TABLE IF EXISTS " + t + " CASCADE";
                        System.out.println("Executing: " + dropSql);
                        stmt.execute(dropSql);
                    }

                    // Cleanup unnecessary columns
                    for (Map.Entry<String, List<String>> entry : unnecessaryColumnsMap.entrySet()) {
                        String tableName = entry.getKey();
                        for (String col : entry.getValue()) {
                            String dropColSql = "ALTER TABLE " + tableName + " DROP COLUMN IF EXISTS " + col + " CASCADE";
                            System.out.println("Executing: " + dropColSql);
                            stmt.execute(dropColSql);
                        }
                    }
                    System.out.println("\nDatabase successfully cleaned up!");
                }
            } else {
                System.out.println("\nNo cleanup needed. Database is fully synchronized with the JPA entities.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void walkAndParseEntities(File file, Map<String, ExpectedTable> expectedTables) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    walkAndParseEntities(child, expectedTables);
                }
            }
        } else if (file.getName().endsWith(".java")) {
            parseEntityFile(file, expectedTables);
        }
    }

    private static void parseEntityFile(File file, Map<String, ExpectedTable> expectedTables) {
        try {
            String content = Files.readString(file.toPath());
            
            // Exclude non-entities
            if (!content.contains("@Entity") && !content.contains("@MappedSuperclass")) {
                return;
            }

            // Extract class name
            String className = "";
            Matcher classMatcher = Pattern.compile("class\\s+([A-Za-z0-9_]+)").matcher(content);
            if (classMatcher.find()) {
                className = classMatcher.group(1);
            } else {
                return;
            }

            final String finalClassName = className;

            // Extract table name
            String tableName = camelToSnake(className);
            Matcher tableMatcher = Pattern.compile("@Table\\(\\s*name\\s*=\\s*\"([^\"]+)\"").matcher(content);
            if (tableMatcher.find()) {
                tableName = tableMatcher.group(1);
            }

            ExpectedTable expected = expectedTables.computeIfAbsent(tableName, k -> {
                ExpectedTable t = new ExpectedTable();
                t.tableName = k;
                t.entityName = finalClassName;
                return t;
            });

            // Parse columns from fields
            String[] lines = content.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                
                // Skip comments and methods
                if (line.startsWith("//") || line.startsWith("/*") || line.contains("(") || line.isEmpty()) {
                    continue;
                }

                // If annotated with @Transient, skip next field
                if (line.startsWith("@Transient")) {
                    i = skipToNextField(lines, i);
                    continue;
                }

                // Check for JoinTable annotations
                if (line.startsWith("@JoinTable")) {
                    Matcher joinTableMatcher = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"").matcher(line);
                    if (joinTableMatcher.find()) {
                        String joinTableName = joinTableMatcher.group(1);
                        ExpectedTable joinTable = expectedTables.computeIfAbsent(joinTableName, k -> {
                            ExpectedTable t = new ExpectedTable();
                            t.tableName = k;
                            t.entityName = "JoinTable";
                            return t;
                        });
                        // Add join columns if any
                        Matcher joinColMatcher = Pattern.compile("joinColumns\\s*=\\s*@JoinColumn\\(\\s*name\\s*=\\s*\"([^\"]+)\"").matcher(line);
                        if (joinColMatcher.find()) {
                            joinTable.columns.add(joinColMatcher.group(1));
                        }
                        Matcher inverseColMatcher = Pattern.compile("inverseJoinColumns\\s*=\\s*@JoinColumn\\(\\s*name\\s*=\\s*\"([^\"]+)\"").matcher(line);
                        if (inverseColMatcher.find()) {
                            joinTable.columns.add(inverseColMatcher.group(1));
                        }
                    }
                    continue;
                }

                // Check for ElementCollection table
                if (line.startsWith("@CollectionTable")) {
                    Matcher collectionTableMatcher = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"").matcher(line);
                    if (collectionTableMatcher.find()) {
                        String collTableName = collectionTableMatcher.group(1);
                        ExpectedTable collTable = expectedTables.computeIfAbsent(collTableName, k -> {
                            ExpectedTable t = new ExpectedTable();
                            t.tableName = k;
                            t.entityName = "CollectionTable";
                            return t;
                        });
                        Matcher joinColMatcher = Pattern.compile("joinColumns\\s*=\\s*@JoinColumn\\(\\s*name\\s*=\\s*\"([^\"]+)\"").matcher(line);
                        if (joinColMatcher.find()) {
                            collTable.columns.add(joinColMatcher.group(1));
                        }
                    }
                    continue;
                }

                // Check standard column mapping
                if (line.startsWith("@Column")) {
                    Matcher colMatcher = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"").matcher(line);
                    if (colMatcher.find()) {
                        expected.columns.add(colMatcher.group(1));
                    }
                } else if (line.startsWith("@JoinColumn")) {
                    Matcher colMatcher = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"").matcher(line);
                    if (colMatcher.find()) {
                        expected.columns.add(colMatcher.group(1));
                    }
                } else if (line.contains("private ") || line.contains("protected ") || line.contains("public ")) {
                    // Check if it's a field declaration
                    String clean = line.replaceAll("@[A-Za-z0-9_]+(?:\\([^)]*\\))?", "").trim();
                    if (clean.endsWith(";")) {
                        clean = clean.substring(0, clean.length() - 1).trim();
                        String[] tokens = clean.split("\\s+");
                        if (tokens.length >= 2) {
                            String type = tokens[tokens.length - 2];
                            String name = tokens[tokens.length - 1];
                            
                            // Check static final constants
                            if (clean.contains("static ") && clean.contains("final ")) {
                                continue;
                            }

                            // If not transient, map to snake_case column by default
                            String colName = camelToSnake(name);
                            
                            // If it's a relationship field, standard naming is relationship_id
                            if (content.contains("@ManyToOne") || content.contains("@OneToOne")) {
                                // Search if the previous lines had @ManyToOne/@OneToOne for this field
                                // (simplistic check)
                                colName = colName + "_id";
                            }
                            
                            expected.columns.add(colName);
                        }
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading entity file: " + file.getAbsolutePath());
        }
    }

    private static int skipToNextField(String[] lines, int currentIdx) {
        while (currentIdx + 1 < lines.length) {
            currentIdx++;
            String nextLine = lines[currentIdx].trim();
            if (nextLine.contains("private ") || nextLine.contains("protected ") || nextLine.contains("public ")) {
                return currentIdx;
            }
        }
        return currentIdx;
    }

    public static String camelToSnake(String camel) {
        if (camel == null) return "";
        return camel.replaceAll("(?<!^)(?=[A-Z])", "_").toLowerCase();
    }
}
