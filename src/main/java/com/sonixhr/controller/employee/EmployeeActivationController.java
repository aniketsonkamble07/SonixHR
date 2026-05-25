package com.sonixhr.controller.employee;

import com.sonixhr.dto.ActivationRequest;
import com.sonixhr.entity.User;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.security.JwtService;
import com.sonixhr.service.ActivationTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/employee/auth")
@RequiredArgsConstructor
public class EmployeeActivationController {

    private final ActivationTokenService activationTokenService;
    private final EmployeeRepository employeeRepository;
    private final JwtService jwtService;

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateEmployee(
            @Valid @RequestBody ActivationRequest request) {

        log.info("Employee activation request for token: {}", request.getToken());

        // Validate passwords match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        // Step 1: Activate user and get User object
        User user = activationTokenService.setPasswordAndGetUser(
                request.getToken(),
                request.getPassword()
        );

        // Step 2: Find employee by email
        Employee employee = employeeRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Step 3: Update employee status to ACTIVE
        employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE);
        employee.setConfirmationDate(java.time.LocalDate.now());
        employeeRepository.save(employee);

        // Step 4: Create UserDetails for token generation
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password("")
                .authorities(Collections.emptyList())
                .build();

        // Step 5: Generate JWT access token using the Employee method
        String accessToken = jwtService.generateEmployeeToken(
                userDetails,
                user.getTenant().getId(),
                employee.getId(),
                employee.getEmployeeCode()
        );

        // Step 6: Return response with access token
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account activated successfully!");
        response.put("accessToken", accessToken);
        response.put("tokenType", "Bearer");
        response.put("userType", "EMPLOYEE");
        response.put("employeeId", employee.getId());
        response.put("email", employee.getEmail());
        response.put("firstName", employee.getFirstName());
        response.put("lastName", employee.getLastName());
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

        log.info("Employee activated successfully: {}", employee.getEmail());

        return ResponseEntity.ok(response);
    }
}