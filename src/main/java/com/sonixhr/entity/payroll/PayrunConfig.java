package com.sonixhr.entity.payroll;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payrun_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrunConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payrun_id", nullable = false, unique = true)
    private UUID payrunId;

    @Lob
    @Column(name = "statutory_rates_json", nullable = false, columnDefinition = "TEXT")
    private String statutoryRatesJson; // Frozen StatutoryRateConfig list

    @Lob
    @Column(name = "tenant_settings_json", nullable = false, columnDefinition = "TEXT")
    private String tenantSettingsJson; // Frozen TenantPayrollConfig values

    @Lob
    @Column(name = "tenant_structure_json", nullable = false, columnDefinition = "TEXT")
    private String tenantStructureJson; // Frozen TenantSalaryStructure components

    @Lob
    @Column(name = "pt_slabs_json", nullable = false, columnDefinition = "TEXT")
    private String ptSlabsJson; // Frozen StateProfessionalTaxConfig list

    @Column(name = "snapshot_created_at", nullable = false)
    private LocalDateTime snapshotCreatedAt;
}
