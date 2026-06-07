package com.sonixhr.controller.employee;

import com.sonixhr.dto.employee.EmployeeProfileUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.service.employee.EmployeeSelfService;
import com.sonixhr.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;



@Slf4j
@RestController
@RequestMapping("/api/employee")
@RequiredArgsConstructor
public class EmployeeSelfServiceController {

    private final EmployeeSelfService employeeSelfService;

    // =====================================================
    // Get my own profile
    // =====================================================
    @GetMapping("/profile")
    public ResponseEntity<EmployeeResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        Long tenantId = TenantContext.getCurrentTenant();

        log.info("Employee {} viewing their profile", email);

        EmployeeResponse response = employeeSelfService.getEmployeeByEmail(tenantId, email);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // Update my own profile
    // =====================================================
    @PutMapping("/profile")
    public ResponseEntity<EmployeeResponse> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody EmployeeProfileUpdateRequest request) {

        String email = userDetails.getUsername();
        Long tenantId = TenantContext.getCurrentTenant();

        log.info("Employee {} updating their profile", email);

        EmployeeResponse response = employeeSelfService.updateEmployeeProfile(tenantId, email, request);
        return ResponseEntity.ok(response);
    }
}