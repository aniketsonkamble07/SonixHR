package com.sonixhr.entity.payroll;

import com.sonixhr.enums.payroll.TaxRegime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tax_regime_slab_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tax_regime_year", columnNames = {"financial_year", "regime"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRegimeSlabConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false)
    private TaxRegime regime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tax_slab_rows", joinColumns = @JoinColumn(name = "config_id"))
    @OrderColumn(name = "row_order")
    private List<TaxSlabRow> slabs;

    @Column(name = "standard_deduction", precision = 12, scale = 2)
    private BigDecimal standardDeduction;

    @Column(name = "rebate_limit", precision = 12, scale = 2)
    private BigDecimal rebateLimit;

    @Column(name = "rebate_max_amount", precision = 12, scale = 2)
    private BigDecimal rebateMaxAmount;

    @Column(name = "cess_percent", precision = 5, scale = 2)
    private BigDecimal cessPercent;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tax_surcharge_slabs", joinColumns = @JoinColumn(name = "config_id"))
    @OrderColumn(name = "row_order")
    private List<SurchargeSlab> surchargeSlabs;
}
