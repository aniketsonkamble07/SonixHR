package com.sonixhr.service;

import com.sonixhr.entity.ActivationToken;
import com.sonixhr.repository.ActivationTokenRepository;
import lombok.RequiredArgsConstructor;
import com.sonixhr.enums.UserType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationTokenService {

    private final ActivationTokenRepository activationTokenRepository;

    public String generateToken(UUID userId, UserType userType) {

        String token = UUID.randomUUID().toString();

        ActivationToken activationToken =
                ActivationToken.builder()
                        .token(token)
                        .userId(userId)
                        .userType(userType)
                        .expiryTime(LocalDateTime.now().plusHours(24))
                        .used(false)
                        .build();

        activationTokenRepository.save(activationToken);

        return token;
    }
}
