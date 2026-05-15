package com.sonixhr.controller;

import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.User;
import com.sonixhr.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
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
        User user = (User) auth.getPrincipal();
        var tokenPair = jwtService.generateTenantTokenPair(user, user.getTenantId());
        return ResponseEntity.ok(new LoginResponse(tokenPair.getAccessToken(), user.getEmail()));
    }
}