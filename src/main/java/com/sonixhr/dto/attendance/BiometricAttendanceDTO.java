package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricAttendanceDTO {
    private String employeeCode;
    private LocalDateTime timestamp;
    private String eventType;  // CHECK_IN or CHECK_OUT
    private String deviceId;
    private String deviceUserId;
}