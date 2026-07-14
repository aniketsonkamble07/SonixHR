package com.sonixhr.entity.platform;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_deletion_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_admin_id")
    private Long deletedByAdminId;

    @Column(name = "deleted_by_admin_email")
    private String deletedByAdminEmail;

    @Column(name = "data_manifest_hash", nullable = false, length = 64)
    private String dataManifestHash;

    @Column(name = "data_manifest_json", nullable = false, columnDefinition = "TEXT")
    private String dataManifestJson;
}
