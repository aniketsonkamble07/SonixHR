package com.sonixhr.controller.platform;

import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/auth")
public class PlatformAuthController {

    private final AuthenticationManager platformAuthenticationManager;
    private final JwtService jwtService;
    private final PlatformUserRepository platformUserRepository;

    public PlatformAuthController(
            @Qualifier("platformAuthenticationManager") AuthenticationManager platformAuthenticationManager,
            JwtService jwtService,
            PlatformUserRepository platformUserRepository) {
        this.platformAuthenticationManager = platformAuthenticationManager;
        this.jwtService = jwtService;
        this.platformUserRepository = platformUserRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        Authentication auth = platformAuthenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        PlatformUser user = (PlatformUser) auth.getPrincipal();

        // Update last login details
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(httpRequest.getRemoteAddr());
        platformUserRepository.save(user);

        var tokenPair = jwtService.generatePlatformTokenPair(user);
        return ResponseEntity.ok(new LoginResponse(tokenPair.getAccessToken(), user.getEmail()));
    }

    @GetMapping("/test-auth")
    public ResponseEntity<?> testAuth(@AuthenticationPrincipal PlatformUser user) {
        return ResponseEntity.ok(Map.of(
                "authenticated", user != null,
                "email", user != null ? user.getEmail() : "null",
                "authorities", SecurityContextHolder.getContext().getAuthentication().getAuthorities()
        ));
    }
}