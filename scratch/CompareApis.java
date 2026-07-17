package com.sonixhr;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CompareApis {
    public static void main(String[] args) throws IOException {
        File fileNew = new File("E:\\Viplora\\sonixhr\\all_present_apis_reference.md");
        File fileComp = new File("E:\\Viplora\\sonixhr\\comprehensive_api_references.md");

        if (!fileNew.exists() || !fileComp.exists()) {
            System.out.println("Files do not exist.");
            return;
        }

        // Parse endpoints from all_present_apis_reference.md
        Set<String> newEndpoints = new TreeSet<>();
        String contentNew = Files.readString(fileNew.toPath());
        Matcher mNew = Pattern.compile("### (GET|POST|PUT|DELETE|PATCH) `([^`]+)`").matcher(contentNew);
        while (mNew.find()) {
            newEndpoints.add(mNew.group(1) + " " + mNew.group(2));
        }

        // Parse endpoints from comprehensive_api_references.md
        Set<String> compEndpoints = new TreeSet<>();
        String contentComp = Files.readString(fileComp.toPath());
        
        // Let's parse section prefixes and table rows
        String[] lines = contentComp.split("\\r?\\n");
        String currentSectionPrefix = "";
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("### ")) {
                // Find path prefix inside parentheses if any, e.g. "Authentication (/api/platform/auth)"
                Matcher mSec = Pattern.compile("\\((/[^)]+)\\)").matcher(line);
                if (mSec.find()) {
                    currentSectionPrefix = mSec.group(1).replace("`", "");
                } else {
                    currentSectionPrefix = "";
                }
            } else if (line.startsWith("|") && !line.contains("Method") && !line.contains("---")) {
                // Table row
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String method = parts[1].trim().replace("`", "");
                    String path = parts[2].trim().replace("`", "");
                    if ((method.equals("GET") || method.equals("POST") || method.equals("PUT") || method.equals("DELETE") || method.equals("PATCH")) && path.startsWith("/")) {
                        String fullPath = path.startsWith("/api/") ? path : currentSectionPrefix + path;
                        fullPath = fullPath.replaceAll("(?<!:)/{2,}", "/"); // remove double slashes
                        compEndpoints.add(method + " " + fullPath);
                    } else if (path.startsWith("/api/")) {
                        compEndpoints.add(method + " " + path);
                    }
                }
            }
        }

        System.out.println("Total endpoints in new reference: " + newEndpoints.size());
        System.out.println("Total endpoints in comprehensive reference: " + compEndpoints.size());

        System.out.println("\n--- Endpoints newly added/documented (present in all_present_apis but missing in comprehensive):");
        int addedCount = 0;
        for (String ep : newEndpoints) {
            if (!compEndpoints.contains(ep)) {
                System.out.println("- " + ep);
                addedCount++;
            }
        }
        System.out.println("Count: " + addedCount);

        System.out.println("\n--- Endpoints updated/synchronized:");
        int updatedCount = 0;
        for (String ep : newEndpoints) {
            if (compEndpoints.contains(ep)) {
                System.out.println("- " + ep);
                updatedCount++;
            }
        }
        System.out.println("Count: " + updatedCount);
    }
}
