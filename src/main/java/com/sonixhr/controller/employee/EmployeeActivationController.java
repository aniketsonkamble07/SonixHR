package com.sonixhr.controller.employee;
 
import com.sonixhr.dto.ActivationRequest;
// Force re-indexing of imports
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.security.JwtService;
import com.sonixhr.service.ActivationTokenService;
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

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateEmployee(
            @Valid @RequestBody ActivationRequest request) {

        log.info("Employee activation request for token: {}", request.getToken());

        // Validate passwords match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new com.sonixhr.exceptions.ValidationException("confirmPassword", "Passwords do not match");
        }

        // Step 1: Activate employee and get Employee object directly
        Employee employee = activationTokenService.activateEmployee(
                request.getToken(),
                request.getPassword()
        );

        // Step 2: Generate JWT token for employee
        var tokenPair = jwtService.generateEmployeeTokenPair(employee);

        // Step 3: Build response
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

        log.info("Employee activated successfully: {}", employee.getEmail());

        return ResponseEntity.ok(response);
    }
}