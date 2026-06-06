package com.sonixhr.controller;

import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.TokenPair;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/auth")
public class TenantAuthController {

    private final AuthenticationManager tenantAuthenticationManager;
    private final JwtService jwtService;

    public TenantAuthController(
            @Qualifier("tenantAuthenticationManager") AuthenticationManager tenantAuthenticationManager,
            JwtService jwtService) {
        this.tenantAuthenticationManager = tenantAuthenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = tenantAuthenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );


        Employee employee = (Employee) auth.getPrincipal();


        TokenPair tokenPair = jwtService.generateEmployeeTokenPair(employee);

        return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.getExpiresIn())
                .email(employee.getEmail())
                .fullName(employee.getFullName())
                .build());    }
}