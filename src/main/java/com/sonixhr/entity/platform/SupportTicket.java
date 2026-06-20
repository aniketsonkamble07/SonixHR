package com.sonixhr.entity.platform;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_tickets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_number", columnNames = "ticket_number")
}, indexes = {
        @Index(name = "idx_support_tickets_tenant", columnList = "tenant_id"),
        @Index(name = "idx_support_tickets_status", columnList = "status"),
        @Index(name = "idx_support_tickets_ticket_number", columnList = "ticket_number")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", unique = true, nullable = false, length = 50)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by_employee_id", nullable = false)
    private Employee raisedBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String category; // e.g. "Billing", "Technical", "General"

    @Column(nullable = false, length = 20)
    private String priority; // e.g. "LOW", "MEDIUM", "HIGH", "URGENT"

    @Column(nullable = false, length = 20)
    private String status; // e.g. "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_platform_user_id")
    private PlatformUser resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
