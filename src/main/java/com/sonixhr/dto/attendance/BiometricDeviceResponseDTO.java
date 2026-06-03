package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricDeviceResponseDTO {
    private Long id;
    private UUID tenantId;
    private String deviceName;
    private String deviceModel;
    private String deviceType;
    private String serialNumber;
    private String ipAddress;
    private Integer port;
    private String protocol;
    private String firmwareVersion;
    private Boolean isConnected;
    private LocalDateTime lastConnectionTime;
    private LocalDateTime lastSyncTime;
    private Integer lastSyncCount;
    private Integer totalUsers;
    private Integer totalLogs;
    private Boolean isActive;
    private String location;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}