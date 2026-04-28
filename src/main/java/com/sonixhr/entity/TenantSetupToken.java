package com.sonixhr.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_setup_tokens")
public class TenantSetupToken {
    @Id
    private Long id;
    private String token;
    private UUID tenantId;
    private String userEmail;
    private LocalDateTime expiry;
    private boolean used;
    private LocalDateTime createdAt;
}