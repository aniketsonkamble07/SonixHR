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
public class BiometricAttendanceLogDTO {
    private String userId;
    private String employeeCode;
    private String employeeName;
    private LocalDateTime timestamp;
    private String eventType; // CHECK_IN, CHECK_OUT
    private String verificationMode; // FP, FACE, CARD, PW
    private Integer machineNumber;
    private Integer indRegId;
    private String deviceSerialNumber;
}
