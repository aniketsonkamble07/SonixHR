package com.sonixhr.entity.payroll;

import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fnf_settlement_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FnfSettlementItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fnf_settlement_id", nullable = false)
    private FnfSettlement fnfSettlement;

    @Column(name = "component_code", nullable = false)
    private String componentCode;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(name = "type", nullable = false)
    private String type; // ALLOWANCE, DEDUCTION, REIMBURSEMENT, STATUTORY

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 500)
    private String description;
}
