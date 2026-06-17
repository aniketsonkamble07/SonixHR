package com.sonixhr.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.time.LocalDateTime;
 
@Entity
@Table(name = "tenant_setup_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSetupToken {
    @Id
    private Long id;
    private String token;
    private Long tenantId;
    private String userEmail;
    private LocalDateTime expiry;
    private boolean used;
    private LocalDateTime createdAt;
}