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
    public void afterCompletion(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull Object handler,
            @org.springframework.lang.Nullable Exception ex) throws Exception {

        // Skip logging for certain paths to reduce load
        String requestUri = request.getRequestURI();
        if (shouldSkipLogging(requestUri)) {
            return;
        }

        try {
            String httpMethod = request.getMethod();
            String ipAddress = resolveClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String customDeviceName = request.getHeader("X-Device-Name");

            // Extract employee/user authentication details
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long employeeId = null;
            String employeeEmail = "anonymous";
            Long tenantId = null;

            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof Employee employee) {
                    employeeId = employee.getId();
                    employeeEmail = employee.getEmail();
                    if (employee.getTenant() != null) {
                        tenantId = employee.getTenant().getId();
                    } else if (employee.getTenantId() != null) {
                        tenantId = employee.getTenantId();
                    }
                } else if (principal instanceof PlatformUser platformUser) {
                    employeeId = platformUser.getId();
                    employeeEmail = platformUser.getEmail() + " (Platform)";
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                    employeeEmail = userDetails.getUsername();
                } else if (principal instanceof String email) {
                    employeeEmail = email;
                }
            }

            // Derive Device Name
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
                    .hitTime(java.time.LocalDateTime.now())
                    .build();

            // Save asynchronously with circuit breaker protection
            apiHitLogService.saveLog(apiLog);

        } catch (Exception e) {
            // Don't let logging failures affect the main request
            log.debug("Error creating API hit log in interceptor: {}", e.getMessage());
        }
    }

    /**
     * Skip logging for certain paths to reduce load
     */
    private boolean shouldSkipLogging(String path) {
        // Skip health checks and static resources
        if (path == null) {
            return true;
        }

        return path.startsWith("/actuator/") ||
                path.startsWith("/api/health") ||
                path.startsWith("/swagger-ui/") ||
                path.startsWith("/v3/api-docs/") ||
                path.equals("/error") ||
                path.startsWith("/api/debug/") ||
                path.startsWith("/api/public/");
    }

    /**
     * Returns the real client IP, honouring X-Forwarded-For
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private String parseDeviceName(String customDeviceName, String userAgent) {
        if (customDeviceName != null && !customDeviceName.trim().isEmpty()) {
            return customDeviceName.trim();
        }
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }

        String ua = userAgent.toLowerCase();

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

        return os + " / " + browser;
    }
}