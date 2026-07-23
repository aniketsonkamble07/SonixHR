package com.sonixhr.controller.employee;

import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.TokenBlacklistService;
import com.sonixhr.service.ActivationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/employee/auth")
@RequiredArgsConstructor
public class EmployeeActivationController {

    private final ActivationTokenService activationTokenService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    // =====================================================
    // EMPLOYEE ACTIVATION - Same as Tenant/Platform
    // =====================================================

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateEmployee(
            @Valid @RequestBody SetPasswordRequest request,  // ✅ Use SetPasswordRequest
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            HttpServletRequest httpRequest) {  // ✅ Added HttpServletRequest

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Employee activation request from IP: {}, User-Agent: {}, Token: {}",
                clientIp, userAgent, request.getToken());

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new com.sonixhr.exceptions.ValidationException("confirmPassword", "Passwords do not match");
        }

        // ✅ Pass HttpServletRequest for IP tracking
        Employee employee = activationTokenService.activateEmployee(
                request.getToken(),
                request.getNewPassword(),
                httpRequest
        );

        // Generate JWT token for employee
        var tokenPair = jwtService.generateEmployeeTokenPair(employee);

        // Register active session
        String resolvedClientType = clientType != null ? clientType : "WEB";
        tokenBlacklistService.registerActiveSession(employee.getId(), resolvedClientType, tokenPair.getAccessToken());

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account activated successfully!");
        response.put("accessToken", tokenPair.getAccessToken());
        response.put("refreshToken", tokenPair.getRefreshToken());
        response.put("tokenType", "Bearer");
        response.put("expiresIn", tokenPair.getExpiresIn());
        response.put("userType", "EMPLOYEE");
        response.put("employeeId", employee.getId());
        response.put("email", employee.getEmail());
        response.put("firstName", employee.getFirstName());
        response.put("lastName", employee.getLastName());
        response.put("fullName", employee.getFullName());
        response.put("employeeCode", employee.getEmployeeCode());

        if (employee.getDepartment() != null) {
            response.put("department", Map.of(
                    "id", employee.getDepartment().getId(),
                    "name", employee.getDepartment().getName(),
                    "code", employee.getDepartment().getCode()
            ));
        } else {
            response.put("department", null);
        }

        response.put("position", employee.getPosition());
        response.put("tenantId", employee.getTenantId());

        log.info("Employee activated successfully: {}, IP: {}, User-Agent: {}",
                employee.getEmail(), clientIp, userAgent);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // HELPER METHOD - Get Client IP
    // =====================================================

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
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
}