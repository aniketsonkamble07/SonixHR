package com.sonixhr.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.usertype.UserType;

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

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    private LocalDateTime expiryTime;

    private Boolean used;
}