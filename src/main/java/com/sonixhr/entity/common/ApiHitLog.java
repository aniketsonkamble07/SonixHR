package com.sonixhr.entity.common;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_hit_logs", indexes = {
        @Index(name = "idx_api_log_employee", columnList = "employee_id"),
        @Index(name = "idx_api_log_tenant", columnList = "tenant_id"),
        @Index(name = "idx_api_log_time", columnList = "hit_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiHitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_email", length = 150)
    private String employeeEmail;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "request_uri", nullable = false, length = 255)
    private String requestUri;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "device_details", columnDefinition = "TEXT")
    private String deviceDetails;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @CreationTimestamp
    @Column(name = "hit_time", updatable = false)
    private LocalDateTime hitTime;
}
