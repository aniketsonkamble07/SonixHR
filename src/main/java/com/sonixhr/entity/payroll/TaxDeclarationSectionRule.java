package com.sonixhr.entity.payroll;

import com.sonixhr.enums.payroll.TaxRegime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tax_declaration_section_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_section_regime_year", columnNames = {"section", "regime", "financial_year"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxDeclarationSectionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section", nullable = false)
    private String section;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false)
    private TaxRegime regime;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Column(name = "cap_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal capAmount;
}
