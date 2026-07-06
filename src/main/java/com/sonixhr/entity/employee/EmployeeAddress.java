package com.sonixhr.entity.employee;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.IndianStateConverter;
import com.sonixhr.enums.employee.AddressType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "employee_addresses", indexes = {
        @Index(name = "idx_emp_addr_employee", columnList = "employee_id"),
        @Index(name = "idx_emp_addr_tenant", columnList = "tenant_id"),
        @Index(name = "idx_emp_addr_type", columnList = "address_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 30)
    private AddressType addressType;

    @Column(name = "address_line1", nullable = false, columnDefinition = "TEXT")
    private String addressLine1;

    @Column(name = "address_line2", columnDefinition = "TEXT")
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Convert(converter = IndianStateConverter.class)
    @Column(length = 100)
    private IndianState state;

    @Column(name = "state_text", length = 150)
    private String stateText;

    @Column(length = 50)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "is_primary")
    @Builder.Default
    private boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
