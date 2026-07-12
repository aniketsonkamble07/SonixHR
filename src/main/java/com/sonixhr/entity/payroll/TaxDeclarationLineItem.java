package com.sonixhr.entity.payroll;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tax_declaration_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxDeclarationLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section", nullable = false)
    private String section;

    @Column(name = "sub_category")
    private String subCategory;

    @Column(name = "declared_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal declaredAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "proof_document_url")
    private String proofDocumentUrl;

    @Column(name = "proof_status")
    private String proofStatus; // e.g. PENDING, APPROVED, REJECTED

    @Column(name = "remarks")
    private String remarks;
}
