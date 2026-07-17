package com.sonixhr;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ApiReferenceGenerator {

    static class FieldInfo {
        String name;
        String type;
        public FieldInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    static class ClassInfo {
        String name;
        String type; // "class", "enum", "record", "interface"
        String parentClassName;
        List<FieldInfo> fields = new ArrayList<>();
        List<String> enumConstants = new ArrayList<>();
    }

    static class EndpointInfo {
        String httpMethod;
        String path;
        String handlerMethod;
        String description;
        String requestBodyType;
        String responseType;
    }

    static class ControllerInfo {
        String name;
        List<EndpointInfo> endpoints = new ArrayList<>();
    }

    public static void main(String[] args) {
        String srcPath = "E:\\Viplora\\sonixhr\\src\\main\\java";
        File srcDir = new File(srcPath);
        if (!srcDir.exists()) {
            System.err.println("Source path does not exist: " + srcPath);
            return;
        }

        Map<String, ClassInfo> classMap = new HashMap<>();
        List<ControllerInfo> controllers = new ArrayList<>();

        // Walk source directory and parse all Java files
        walkAndParse(srcDir, classMap, controllers);

        // Sort controllers by name
        controllers.sort(Comparator.comparing(c -> c.name));

        // Generate the markdown content
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Exhaustive SonixHR API & Payload Reference\n\n");
        markdown.append("- **Production Base URL**: `https://sonixhr.onrender.com`\n");
        markdown.append("- **Local Base URL**: `http://localhost:8081`\n\n");
        markdown.append("This document lists all API endpoints across all controllers, including mapped request/response DTO structures.\n\n");

        for (ControllerInfo controller : controllers) {
            if (controller.endpoints.isEmpty()) {
                continue;
            }
            markdown.append("## ").append(controller.name).append("\n\n");

            // Sort endpoints: path first, then http method
            controller.endpoints.sort((e1, e2) -> {
                int pathCompare = e1.path.compareTo(e2.path);
                if (pathCompare != 0) return pathCompare;
                return e1.httpMethod.compareTo(e2.httpMethod);
            });

            for (EndpointInfo ep : controller.endpoints) {
                markdown.append("### ").append(ep.httpMethod).append(" `").append(ep.path).append("`\n");
                markdown.append("- **Handler Method**: `").append(ep.handlerMethod).append("`\n");
                markdown.append("- **Description**: ").append(ep.description != null ? ep.description : "No description provided").append("\n");

                if (ep.requestBodyType != null) {
                    markdown.append("- **Request Body Type**: `").append(ep.requestBodyType).append("`\n");
                    String reqJson = generateJsonExample(ep.requestBodyType, classMap, new HashSet<>());
                    markdown.append("#### Request JSON Example:\n```json\n").append(reqJson).append("\n```\n");
                } else {
                    markdown.append("- **Request Body**: None (Query parameters / Path variables only)\n");
                }

                markdown.append("- **Response Type**: `").append(ep.responseType).append("`\n");
                String respJson = generateJsonExample(ep.responseType, classMap, new HashSet<>());
                if (respJson != null && !respJson.equals("null") && !respJson.isEmpty()) {
                    markdown.append("#### Response JSON Example:\n```json\n").append(respJson).append("\n```\n");
                }
                markdown.append("\n---\n\n");
            }
        }

        // Write the output file
        File outputFile = new File("E:\\Viplora\\sonixhr\\all_present_apis_reference.md");
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.print(markdown.toString().trim() + "\n");
            System.out.println("Successfully generated API Reference at: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void walkAndParse(File file, Map<String, ClassInfo> classMap, List<ControllerInfo> controllers) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    walkAndParse(child, classMap, controllers);
                }
            }
        } else if (file.getName().endsWith(".java")) {
            parseJavaFile(file, classMap, controllers);
        }
    }

    private static void parseJavaFile(File file, Map<String, ClassInfo> classMap, List<ControllerInfo> controllers) {
        try {
            String content = Files.readString(file.toPath());
            String cleanContent = stripComments(content);

            // Check if it's a Controller
            boolean isRestController = cleanContent.contains("@RestController");

            // Parse Class/Enum/Record declaration
            Pattern classPattern = Pattern.compile(
                "(?:public|protected|private|static|final|\\s)*\\s*(class|enum|record|interface)\\s+([A-Za-z0-9_]+)(?:\\s+extends\\s+([A-Za-z0-9_.]+))?"
            );
            Matcher classMatcher = classPattern.matcher(cleanContent);
            if (classMatcher.find()) {
                String type = classMatcher.group(1);
                String className = classMatcher.group(2);
                String parentClass = classMatcher.group(3);

                ClassInfo info = new ClassInfo();
                info.name = className;
                info.type = type;
                if (parentClass != null) {
                    // Get short name of parent
                    int lastDot = parentClass.lastIndexOf('.');
                    info.parentClassName = lastDot != -1 ? parentClass.substring(lastDot + 1) : parentClass;
                }

                if (type.equals("enum")) {
                    parseEnumConstants(cleanContent, className, info);
                } else if (type.equals("record")) {
                    parseRecordFields(cleanContent, className, info);
                } else {
                    parseClassFields(cleanContent, info);
                }

                classMap.put(className, info);

                if (isRestController) {
                    ControllerInfo ctrl = parseController(content, className);
                    if (ctrl != null) {
                        controllers.add(ctrl);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + file.getAbsolutePath());
        }
    }

    private static String stripComments(String content) {
        // Strip block comments
        String noBlock = content.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        // Strip line comments but keep newlines
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(noBlock))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int commentIdx = line.indexOf("//");
                if (commentIdx != -1) {
                    line = line.substring(0, commentIdx);
                }
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            // Ignore
        }
        return sb.toString();
    }

    private static void parseEnumConstants(String content, String className, ClassInfo info) {
        // Extract enum constants (comma-separated at the beginning of the enum body)
        int bodyStart = content.indexOf(className) + className.length();
        bodyStart = content.indexOf('{', bodyStart) + 1;
        if (bodyStart == 0) return;

        int bodyEnd = content.indexOf(';', bodyStart);
        if (bodyEnd == -1) {
            bodyEnd = content.lastIndexOf('}');
        }
        if (bodyEnd == -1 || bodyEnd <= bodyStart) return;

        String enumBody = content.substring(bodyStart, bodyEnd);
        String[] parts = enumBody.split(",");
        for (String part : parts) {
            String term = part.replaceAll("@[A-Za-z0-9_]+(?:\\([^)]*\\))?", "").trim();
            if (term.isEmpty()) continue;
            // Parse identifier
            Matcher m = Pattern.compile("^[A-Za-z0-9_]+").matcher(term);
            if (m.find()) {
                info.enumConstants.add(m.group());
            }
        }
    }

    private static void parseRecordFields(String content, String className, ClassInfo info) {
        // Extract record parameters
        int bodyStart = content.indexOf(className) + className.length();
        int parenStart = content.indexOf('(', bodyStart);
        if (parenStart == -1) return;
        int parenEnd = findClosingParen(content, parenStart);
        if (parenEnd == -1) return;

        String paramsStr = content.substring(parenStart + 1, parenEnd);
        List<String> params = splitByCommaOutsideBrackets(paramsStr);
        for (String param : params) {
            String cleanParam = param.replaceAll("@[A-Za-z0-9_]+(?:\\([^)]*\\))?", "").trim();
            if (cleanParam.isEmpty()) continue;
            String[] tokens = cleanParam.split("\\s+");
            if (tokens.length >= 2) {
                String type = tokens[tokens.length - 2];
                String name = tokens[tokens.length - 1];
                info.fields.add(new FieldInfo(name, type));
            }
        }
    }

    private static List<String> splitByCommaOutsideBrackets(String s) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketCount = 0;
        int parenCount = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') bracketCount++;
            else if (c == '>') bracketCount--;
            else if (c == '(') parenCount++;
            else if (c == ')') parenCount--;
            
            if (c == ',' && bracketCount == 0 && parenCount == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private static int findClosingParen(String s, int openIndex) {
        int count = 0;
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') count++;
            else if (c == ')') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }

    private static void parseClassFields(String content, ClassInfo info) {
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = line.replaceAll("@[A-Za-z0-9_]+(?:\\([^)]*\\))?", "").trim();
                if (clean.startsWith("import ") || clean.startsWith("package ")) {
                    continue;
                }
                if (clean.endsWith(";") && !clean.contains("(")) {
                    FieldInfo f = parseFieldLine(clean);
                    if (f != null) {
                        info.fields.add(f);
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private static FieldInfo parseFieldLine(String line) {
        String clean = line.trim();
        if (!clean.endsWith(";")) return null;
        clean = clean.substring(0, clean.length() - 1).trim(); // remove ;
        
        int eqIdx = -1;
        int bracketCount = 0;
        int parenCount = 0;
        int braceCount = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '<') bracketCount++;
            else if (c == '>') bracketCount--;
            else if (c == '(') parenCount++;
            else if (c == ')') parenCount--;
            else if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            else if (c == '=' && bracketCount == 0 && parenCount == 0 && braceCount == 0) {
                eqIdx = i;
                break;
            }
        }
        if (eqIdx != -1) {
            clean = clean.substring(0, eqIdx).trim();
        }
        
        int idx = clean.length() - 1;
        while (idx >= 0 && Character.isWhitespace(clean.charAt(idx))) idx--;
        int nameEnd = idx + 1;
        while (idx >= 0 && (Character.isJavaIdentifierPart(clean.charAt(idx)) || clean.charAt(idx) == '_')) idx--;
        int nameStart = idx + 1;
        if (nameStart >= nameEnd) return null;
        String name = clean.substring(nameStart, nameEnd);
        
        boolean isStatic = false;
        String typePart = clean.substring(0, nameStart).trim();
        String[] modifiers = {"public", "private", "protected", "static", "final", "transient", "volatile"};
        boolean checkMore = true;
        while (checkMore) {
            checkMore = false;
            for (String mod : modifiers) {
                if (typePart.startsWith(mod + " ")) {
                    if (mod.equals("static")) {
                        isStatic = true;
                    }
                    typePart = typePart.substring(mod.length() + 1).trim();
                    checkMore = true;
                    break;
                }
            }
        }
        
        if (isStatic) return null;
        if (typePart.isEmpty()) return null;
        return new FieldInfo(name, typePart);
    }

    private static ControllerInfo parseController(String content, String className) {
        ControllerInfo ctrl = new ControllerInfo();
        ctrl.name = className;

        // Parse class-level path mapping
        String classPath = "";
        Pattern requestMappingPat = Pattern.compile("@RequestMapping\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]+)\"");
        Matcher classMapMatcher = requestMappingPat.matcher(content);
        if (classMapMatcher.find()) {
            classPath = classMapMatcher.group(1);
        }

        // Scan the file for endpoint methods
        String[] lines = content.split("\\r?\\n");
        int classDeclLineIdx = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.contains("class " + className) || l.contains("record " + className) || l.contains("interface " + className)) {
                classDeclLineIdx = i;
                break;
            }
        }
        for (int i = classDeclLineIdx + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("@GetMapping") || line.startsWith("@PostMapping") ||
                line.startsWith("@PutMapping") || line.startsWith("@DeleteMapping") ||
                line.startsWith("@PatchMapping") || line.startsWith("@RequestMapping")) {

                // Found a mapping annotation! Concatenate it if it spans multiple lines.
                StringBuilder annotationBuilder = new StringBuilder(line);
                int j = i;
                int parenCount = countChar(line, '(') - countChar(line, ')');
                while (parenCount > 0 && j + 1 < lines.length) {
                    j++;
                    String nextLine = lines[j].trim();
                    annotationBuilder.append(" ").append(nextLine);
                    parenCount += countChar(nextLine, '(') - countChar(nextLine, ')');
                }
                String fullAnnotation = annotationBuilder.toString();

                // Now look forward for the method signature line and description
                String description = null;
                // Walk backwards to find comments (up to 15 lines back)
                StringBuilder commentBuilder = new StringBuilder();
                int k = i - 1;
                boolean inComment = false;
                while (k >= 0 && k >= i - 15) {
                    String prevLine = lines[k].trim();
                    if (prevLine.endsWith("*/")) {
                        inComment = true;
                    }
                    if (inComment) {
                        commentBuilder.insert(0, prevLine + "\n");
                        if (prevLine.startsWith("/**") || prevLine.startsWith("/*")) {
                            break;
                        }
                    } else {
                        // If we see anything other than annotations, break
                        if (!prevLine.startsWith("@") && !prevLine.isEmpty()) {
                            break;
                        }
                    }
                    k--;
                }

                // Extract Swagger @Operation description if present in method annotations
                String opDesc = "";
                int methodSigLineIdx = j + 1;
                while (methodSigLineIdx < lines.length) {
                    String nextLine = lines[methodSigLineIdx].trim();
                    if (nextLine.startsWith("@Operation")) {
                        StringBuilder opBuilder = new StringBuilder(nextLine);
                        int opJ = methodSigLineIdx;
                        int opParenCount = countChar(nextLine, '(') - countChar(nextLine, ')');
                        while (opParenCount > 0 && opJ + 1 < lines.length) {
                            opJ++;
                            String opNextLine = lines[opJ].trim();
                            opBuilder.append(" ").append(opNextLine);
                            opParenCount += countChar(opNextLine, '(') - countChar(opNextLine, ')');
                        }
                        String fullOp = opBuilder.toString();
                        Matcher summaryMatcher = Pattern.compile("summary\\s*=\\s*\"([^\"]+)\"").matcher(fullOp);
                        if (summaryMatcher.find()) {
                            opDesc = summaryMatcher.group(1);
                        } else {
                            Matcher descMatcher = Pattern.compile("description\\s*=\\s*\"([^\"]+)\"").matcher(fullOp);
                            if (descMatcher.find()) {
                                opDesc = descMatcher.group(1);
                            }
                        }
                        break;
                    } else if (!nextLine.startsWith("@") && !nextLine.isEmpty()) {
                        break;
                    }
                    methodSigLineIdx++;
                }

                if (!opDesc.isEmpty()) {
                    description = opDesc;
                } else if (commentBuilder.length() > 0) {
                    description = cleanJavaDoc(commentBuilder.toString());
                }

                // Find the method signature line (starts after annotations)
                String sigLine = "";
                int sigIdx = j + 1;
                while (sigIdx < lines.length) {
                    String nextLine = lines[sigIdx].trim();
                    if (!nextLine.startsWith("@") && !nextLine.isEmpty()) {
                        sigLine = nextLine;
                        break;
                    }
                    sigIdx++;
                }

                if (sigLine.isEmpty()) {
                    continue; // Method signature not found
                }

                // If method signature spans multiple lines, concatenate
                StringBuilder sigBuilder = new StringBuilder(sigLine);
                int sigJ = sigIdx;
                while (!sigLine.contains(")") && sigJ + 1 < lines.length) {
                    sigJ++;
                    String nextLine = lines[sigJ].trim();
                    sigBuilder.append(" ").append(nextLine);
                    sigLine = nextLine;
                }
                String fullSig = sigBuilder.toString();

                // Extract Method Name and Return Type
                String returnType = "";
                String methodName = "";
                int parenStart = fullSig.indexOf('(');
                if (parenStart != -1) {
                    String beforeParen = fullSig.substring(0, parenStart).trim();
                    int lastIdx = beforeParen.length() - 1;
                    while (lastIdx >= 0 && Character.isWhitespace(beforeParen.charAt(lastIdx))) lastIdx--;
                    int nameEnd = lastIdx + 1;
                    while (lastIdx >= 0 && (Character.isJavaIdentifierPart(beforeParen.charAt(lastIdx)) || beforeParen.charAt(lastIdx) == '_')) lastIdx--;
                    int nameStart = lastIdx + 1;
                    if (nameStart < nameEnd) {
                        methodName = beforeParen.substring(nameStart, nameEnd);
                        // Return type is before method name
                        String returnTypePart = beforeParen.substring(0, nameStart).trim();
                        // Strip modifiers
                        String[] modifiers = {"public", "protected", "private", "static", "final", "synchronized", "native"};
                        for (String mod : modifiers) {
                            if (returnTypePart.startsWith(mod + " ")) {
                                returnTypePart = returnTypePart.substring(mod.length() + 1).trim();
                            }
                        }
                        returnType = returnTypePart;
                    }
                }

                if (methodName.isEmpty()) {
                    continue;
                }

                // Extract RequestBody parameter
                String requestBodyType = null;
                int parenEnd = findClosingParen(fullSig, parenStart);
                if (parenEnd != -1) {
                    String paramsStr = fullSig.substring(parenStart + 1, parenEnd);
                    // Match @RequestBody
                    Matcher reqBodyMatcher = Pattern.compile("@RequestBody\\s+(?:@[A-Za-z0-9_]+\\s+)*([A-Za-z0-9_<>?]+)").matcher(paramsStr);
                    if (reqBodyMatcher.find()) {
                        requestBodyType = reqBodyMatcher.group(1);
                    }
                }

                // Resolve path and http method from mapping annotation
                String httpMethod = "GET";
                String methodPath = "";

                if (fullAnnotation.startsWith("@GetMapping")) {
                    httpMethod = "GET";
                    methodPath = extractPathFromAnnotation(fullAnnotation);
                } else if (fullAnnotation.startsWith("@PostMapping")) {
                    httpMethod = "POST";
                    methodPath = extractPathFromAnnotation(fullAnnotation);
                } else if (fullAnnotation.startsWith("@PutMapping")) {
                    httpMethod = "PUT";
                    methodPath = extractPathFromAnnotation(fullAnnotation);
                } else if (fullAnnotation.startsWith("@DeleteMapping")) {
                    httpMethod = "DELETE";
                    methodPath = extractPathFromAnnotation(fullAnnotation);
                } else if (fullAnnotation.startsWith("@PatchMapping")) {
                    httpMethod = "PATCH";
                    methodPath = extractPathFromAnnotation(fullAnnotation);
                } else if (fullAnnotation.startsWith("@RequestMapping")) {
                    httpMethod = "GET"; // Default
                    Matcher methodMatcher = Pattern.compile("method\\s*=\\s*RequestMethod\\.(GET|POST|PUT|DELETE|PATCH)").matcher(fullAnnotation);
                    if (methodMatcher.find()) {
                        httpMethod = methodMatcher.group(1);
                    }
                    methodPath = extractPathFromAnnotation(fullAnnotation);
                }

                EndpointInfo ep = new EndpointInfo();
                ep.httpMethod = httpMethod;
                ep.path = combinePath(classPath, methodPath);
                ep.handlerMethod = methodName;
                ep.description = description;
                ep.requestBodyType = requestBodyType;
                ep.responseType = cleanResponseEntity(returnType);

                ctrl.endpoints.add(ep);

                // Skip scanning inner lines of the method
                i = sigJ;
            }
        }

        return ctrl;
    }

    private static String extractPathFromAnnotation(String annotation) {
        Pattern pathPat = Pattern.compile("(?:value|path)\\s*=\\s*\"([^\"]+)\"");
        Matcher m = pathPat.matcher(annotation);
        if (m.find()) {
            return m.group(1);
        }
        // Check for direct string argument e.g. @GetMapping("/path")
        Pattern directPat = Pattern.compile("@[A-Za-z0-9_]+\\(\\s*\"([^\"]+)\"\\s*\\)");
        Matcher m2 = directPat.matcher(annotation);
        if (m2.find()) {
            return m2.group(1);
        }
        return "";
    }

    private static String cleanResponseEntity(String type) {
        if (type == null) return "Void";
        if (type.startsWith("ResponseEntity<") && type.endsWith(">")) {
            String inner = type.substring(15, type.length() - 1);
            if (inner.equals("?") || inner.isEmpty()) {
                return "ResponseEntity<?>";
            }
            if (inner.startsWith("List<") && inner.endsWith(">")) {
                return inner.substring(5, inner.length() - 1) + "[]";
            }
            if (inner.startsWith("Set<") && inner.endsWith(">")) {
                return inner.substring(4, inner.length() - 1) + "[]";
            }
            if (inner.startsWith("Collection<") && inner.endsWith(">")) {
                return inner.substring(11, inner.length() - 1) + "[]";
            }
            return inner;
        }
        return type;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private static String cleanJavaDoc(String javadoc) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(javadoc))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("/**") || line.startsWith("/*") || line.endsWith("*/")) {
                    continue;
                }
                if (line.startsWith("*")) {
                    line = line.substring(1).trim();
                }
                if (!line.isEmpty()) {
                    sb.append(line).append(" ");
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return sb.toString().trim();
    }

    public static String combinePath(String classPath, String methodPath) {
        if (classPath == null) classPath = "";
        if (methodPath == null) methodPath = "";
        
        classPath = classPath.trim();
        methodPath = methodPath.trim();
        
        String path = classPath;
        if (!methodPath.isEmpty()) {
            if (!path.endsWith("/") && !methodPath.startsWith("/")) {
                path += "/";
            }
            if (path.endsWith("/") && methodPath.startsWith("/")) {
                path += methodPath.substring(1);
            } else {
                path += methodPath;
            }
        }
        
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        path = path.replaceAll("(?<!:)/{2,}", "/");
        return path;
    }

    public static String cleanTypeName(String typeName) {
        if (typeName == null) return "";
        String s = typeName;
        if (s.startsWith("List<")) {
            s = s.substring(5, s.length() - 1);
        } else if (s.startsWith("Set<")) {
            s = s.substring(4, s.length() - 1);
        } else if (s.startsWith("Collection<")) {
            s = s.substring(11, s.length() - 1);
        } else if (s.startsWith("Page<")) {
            s = s.substring(5, s.length() - 1);
        } else if (s.endsWith("[]")) {
            s = s.substring(0, s.length() - 2);
        }
        int lastDot = s.lastIndexOf('.');
        if (lastDot != -1) {
            s = s.substring(lastDot + 1);
        }
        return s;
    }

    public static void gatherFields(ClassInfo info, Map<String, ClassInfo> classMap, List<FieldInfo> fields) {
        if (info == null) return;
        if (info.parentClassName != null) {
            gatherFields(classMap.get(info.parentClassName), classMap, fields);
        }
        fields.addAll(info.fields);
    }

    public static String generateJsonExample(String typeName, Map<String, ClassInfo> classMap, Set<String> visited) {
        if (typeName == null) return "null";
        
        String cleanType = cleanTypeName(typeName);
        
        if (cleanType.equals("String")) return "\"\"";
        if (cleanType.equals("Integer") || cleanType.equals("int") ||
            cleanType.equals("Long") || cleanType.equals("long") ||
            cleanType.equals("Double") || cleanType.equals("double") ||
            cleanType.equals("Float") || cleanType.equals("float") ||
            cleanType.equals("Short") || cleanType.equals("short") ||
            cleanType.equals("Byte") || cleanType.equals("byte") ||
            cleanType.equals("BigDecimal")) {
            return "0";
        }
        if (cleanType.equals("Boolean") || cleanType.equals("boolean")) return "false";
        if (cleanType.equals("LocalDate") || cleanType.equals("LocalDateTime") || cleanType.equals("Date")) {
            return "\"2026-06-17\"";
        }
        if (cleanType.equals("UUID")) return "\"UUID\"";
        if (cleanType.equals("Void") || cleanType.equals("void") || cleanType.equals("ResponseEntity<?>")) return "null";
        if (cleanType.startsWith("Map<") || cleanType.equals("Map")) {
            return "{ \"key\": \"value\" }";
        }
        
        boolean isCollection = typeName.startsWith("List<") || typeName.startsWith("Set<") || 
                               typeName.startsWith("Collection<") || typeName.startsWith("Page<") ||
                               typeName.endsWith("[]");
        
        if (visited.contains(cleanType)) {
            if (isCollection) {
                return "[ \"[Recursive Reference to " + cleanType + "]\" ]";
            } else {
                return "\"[Recursive Reference to " + cleanType + "]\"";
            }
        }
        
        ClassInfo info = classMap.get(cleanType);
        if (info == null) {
            if (isCollection) {
                return "[ \"" + cleanType + "\" ]";
            } else {
                return "\"" + cleanType + "\"";
            }
        }
        
        if (info.type.equals("enum")) {
            if (isCollection) {
                return "[ \"" + cleanType + "\" ]";
            } else {
                return "\"" + cleanType + "\"";
            }
        }
        
        visited.add(cleanType);
        StringBuilder sb = new StringBuilder();
        if (isCollection) {
            sb.append("[\n");
        }
        sb.append("{\n");
        
        List<FieldInfo> allFields = new ArrayList<>();
        gatherFields(info, classMap, allFields);
        
        for (int i = 0; i < allFields.size(); i++) {
            FieldInfo f = allFields.get(i);
            String fieldExample = generateJsonExample(f.type, classMap, new HashSet<>(visited));
            String indented = indent(fieldExample, "  ");
            sb.append("  \"").append(f.name).append("\": ").append(indented);
            if (i < allFields.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");
        if (isCollection) {
            sb.append("\n]");
        }
        visited.remove(cleanType);
        return sb.toString();
    }

    private static String indent(String source, String indent) {
        if (!source.contains("\n")) return source;
        StringBuilder sb = new StringBuilder();
        String[] lines = source.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                sb.append(lines[i]);
            } else {
                sb.append("\n").append(indent).append(lines[i]);
            }
        }
        return sb.toString();
    }
}
