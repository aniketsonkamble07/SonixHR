package com.sonixhr;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class SearchLogs {
    public static void main(String[] args) {
        String logPath = "C:\\Users\\win-10\\.gemini\\antigravity-ide\\brain\\5d7d626d-5282-4495-a4ae-1fcb6a079329\\.system_generated\\logs\\transcript.jsonl";
        File file = new File(logPath);
        if (!file.exists()) {
            System.err.println("Transcript file does not exist: " + file.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Check if step_index is >= 201 and contains apexnexus or 89174
                if (line.contains("\"step_index\":") && (line.contains("89174") || line.contains("apexnexus"))) {
                    // Extract step_index
                    int stepIndex = -1;
                    Matcher idxMatcher = Pattern.compile("\"step_index\":(\\d+)").matcher(line);
                    if (idxMatcher.find()) {
                        stepIndex = Integer.parseInt(idxMatcher.group(1));
                    }

                    // Extract source
                    String source = "";
                    Matcher srcMatcher = Pattern.compile("\"source\":\"([^\"]+)\"").matcher(line);
                    if (srcMatcher.find()) {
                        source = srcMatcher.group(1);
                    }

                    if (stepIndex >= 390 && stepIndex <= 415) {
                        System.out.println("=== Step " + stepIndex + " (" + source + ") ===");
                        // Find content or code_content or tool_calls
                        System.out.println(line);
                        System.out.println("==================================\n");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
