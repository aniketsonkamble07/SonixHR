package com.sonixhr.security;

import com.sonixhr.entity.common.ApiHitLog;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.common.ApiHitLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiHitLogInterceptor implements HandlerInterceptor {

    private final ApiHitLogService apiHitLogService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        try {
            String requestUri = request.getRequestURI();
            String httpMethod = request.getMethod();
            String ipAddress = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            String customDeviceName = request.getHeader("X-Device-Name");

            // Extract employee/user authentication details
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long employeeId = null;
            String employeeEmail = "anonymous";
            Long tenantId = null;

            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof Employee) {
                    Employee employee = (Employee) principal;
                    employeeId = employee.getId();
                    employeeEmail = employee.getEmail();
                    if (employee.getTenant() != null) {
                        tenantId = employee.getTenant().getId();
                    } else if (employee.getTenantId() != null) {
                        tenantId = employee.getTenantId();
                    }
                } else if (principal instanceof PlatformUser) {
                    PlatformUser platformUser = (PlatformUser) principal;
                    employeeId = platformUser.getId();
                    employeeEmail = platformUser.getEmail() + " (Platform)";
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    org.springframework.security.core.userdetails.UserDetails userDetails = (org.springframework.security.core.userdetails.UserDetails) principal;
                    employeeEmail = userDetails.getUsername();
                } else if (principal instanceof String) {
                    employeeEmail = (String) principal;
                }
            }

            // Derive Device Name (PC OS / Mobile model / Custom Header)
            String deviceName = parseDeviceName(customDeviceName, userAgent);
            String deviceDetails = parseDeviceDetails(userAgent);

            // Construct log entry
            ApiHitLog apiLog = ApiHitLog.builder()
                    .employeeId(employeeId)
                    .employeeEmail(employeeEmail)
                    .tenantId(tenantId)
                    .requestUri(requestUri)
                    .httpMethod(httpMethod)
                    .ipAddress(ipAddress)
                    .deviceDetails(deviceDetails)
                    .deviceName(deviceName)
                    .build();

            // Trigger async save
            apiHitLogService.saveLog(apiLog);

        } catch (Exception e) {
            log.error("Error creating API hit log in interceptor", e);
        }
    }

    private String parseDeviceName(String customDeviceName, String userAgent) {
        if (customDeviceName != null && !customDeviceName.trim().isEmpty()) {
            return customDeviceName.trim();
        }
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }

        String ua = userAgent.toLowerCase();

        // 1. Mobile & Tablet detection
        if (ua.contains("iphone")) return "Apple iPhone";
        if (ua.contains("ipad")) return "Apple iPad";
        if (ua.contains("android")) {
            try {
                int startIdx = ua.indexOf("android");
                String sub = userAgent.substring(startIdx);
                int endIdx = sub.indexOf(")");
                if (endIdx != -1) {
                    String androidSection = sub.substring(0, endIdx);
                    String[] parts = androidSection.split(";");
                    if (parts.length > 1) {
                        return "Android (" + parts[parts.length - 1].trim() + ")";
                    }
                }
            } catch (Exception e) {
                // fallback
            }
            return "Android Mobile";
        }

        // 2. Desktop OS detection
        if (ua.contains("windows")) return "Windows PC";
        if (ua.contains("macintosh") || ua.contains("mac os")) return "Mac Device";
        if (ua.contains("linux")) return "Linux PC";

        return "Generic Device";
    }

    private String parseDeviceDetails(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }

        String os = "Unknown OS";
        String browser = "Unknown Browser";
        String ua = userAgent.toLowerCase();

        // OS Detection
        if (ua.contains("windows")) {
            os = "Windows";
        } else if (ua.contains("macintosh") || ua.contains("mac os")) {
            os = "Mac OS";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad")) {
            os = "iOS";
        } else if (ua.contains("linux")) {
            os = "Linux";
        }

        // Browser Detection
        if (ua.contains("chrome") || ua.contains("crios")) {
            browser = "Chrome";
        } else if (ua.contains("firefox") || ua.contains("fxios")) {
            browser = "Firefox";
        } else if (ua.contains("safari") && !ua.contains("chrome") && !ua.contains("android")) {
            browser = "Safari";
        } else if (ua.contains("edge") || ua.contains("edg")) {
            browser = "Edge";
        } else if (ua.contains("msie") || ua.contains("trident")) {
            browser = "Internet Explorer";
        }

        return os + " / " + browser + " (Raw: " + userAgent + ")";
    }
}
