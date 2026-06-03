// =====================================================
// BIOMETRIC DEVICE ENTITY
// =====================================================

// entity/attendance/BiometricDevice.java
package com.sonixhr.entity.attendance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "device_type")
    private String deviceType; // ZKTECO, MANTRA, SECUGEN, etc.

    @Column(name = "serial_number", unique = true)
    private String serialNumber;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "port")
    private Integer port;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "api_secret")
    private String apiSecret;

    @Column(name = "protocol")
    private String protocol; // TCP/IP, USB, etc.

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "is_connected")
    @Builder.Default
    private Boolean isConnected = false;

    @Column(name = "last_connection_time")
    private LocalDateTime lastConnectionTime;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @Column(name = "last_sync_count")
    private Integer lastSyncCount;

    @Column(name = "total_users")
    private Integer totalUsers;

    @Column(name = "total_logs")
    private Integer totalLogs;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "location")
    private String location;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}