package com.sonixhr.service.platform;


import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.ActivationTokenRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final EmailService emailService;

    @Transactional
    public PlatformUserResponse createUser(PlatformUserRequest request) {

        // Check email already exists
        if (platformUserRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Create user without password
        PlatformUser user = PlatformUser.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(null)
                .isActive(false)
                .build();

        PlatformUser savedUser = platformUserRepository.save(user);

        // Generate token
        String activationToken = UUID.randomUUID().toString();

        // Save activation token
        UserActivationToken token = UserActivationToken.builder()
                .token(activationToken)
                .platformUser(savedUser)
                .expiryTime(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        activationTokenRepository.save(token);

        // Activation URL
        String activationLink =
                "http://localhost:3000/activate-account?token=" + activationToken;

        // Send activation email
        emailService.sendActivationEmail(
                savedUser.getEmail(),
                savedUser.getFirstName(),
                activationLink
        );

        return mapToResponse(savedUser);
    }

    private PlatformUserResponse mapToResponse(PlatformUser user) {

        return PlatformUserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}