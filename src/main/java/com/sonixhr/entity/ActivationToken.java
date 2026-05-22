package com.sonixhr.entity;

import com.sonixhr.enums.UserType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "activation_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;


    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    @Builder.Default
    private Boolean used = false;
}