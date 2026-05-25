package com.sonixhr.service;

import com.sonixhr.entity.ActivationToken;
import com.sonixhr.entity.User;
import com.sonixhr.enums.UserType;
import com.sonixhr.repository.ActivationTokenRepository;
import com.sonixhr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationTokenService {

    private final ActivationTokenRepository activationTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public String generateToken(UUID userId, UserType userType) {
        String token = UUID.randomUUID().toString();

        ActivationToken activationToken = ActivationToken.builder()
                .token(token)
                .userId(userId)
                .userType(userType)
                .expiryTime(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        activationTokenRepository.save(activationToken);
        return token;
    }

    /**
     * Set password for user using activation token (for platform users)
     */
    @Transactional
    public void setPassword(String token, String newPassword) {
        log.info("Setting password for token: {}", token);

        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiryTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Invalid or expired activation token"));

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        User user = userRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setActive(true);
        userRepository.save(user);

        log.info("Password set successfully for user: {}", user.getEmail());
    }

    /**
     * Set password and return user (for auto-login after activation)
     */
    @Transactional
    public User setPasswordAndGetUser(String token, String newPassword) {
        log.info("Setting password for token: {}", token);

        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiryTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Invalid or expired activation token"));

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        User user = userRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setActive(true);
        userRepository.save(user);

        log.info("Password set successfully for user: {}", user.getEmail());
        return user;
    }
}