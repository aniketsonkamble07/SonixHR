package com.sonixhr.entity.payroll;

import com.sonixhr.enums.payroll.DeclarationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tax_declarations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_tax_year", columnNames = {"employee_id", "financial_year"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeclarationStatus status;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "tax_declaration_id")
    private List<TaxDeclarationLineItem> lineItems;
}
