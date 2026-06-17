package com.sonixhr.entity.tenant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
 
@Entity
@Table(name = "tenant_billing_info", indexes = {
        @Index(name = "idx_billing_tenant", columnList = "tenant_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBillingInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    @Column(name = "billing_phone", length = 20)
    private String billingPhone;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "vat_number", length = 50)
    private String vatNumber;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_provider_id", length = 255)
    private String paymentProviderId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}