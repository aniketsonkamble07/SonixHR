package com.sonixhr.dto.attendance;

import com.sonixhr.enums.attendance.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualTeamMemberAttendanceDTO {

    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String email;
    private String position;
    private String profilePicture;
    private LocalDate hireDate;
    private AttendanceStatus todayStatus;
    private Double todayOvertime;
    private String todayReason;
    private AttendanceStatus status;
    private Double overtime;
    private String reason;
    private boolean isMarked;
    private Long markedBy;
    private String markedByName;
}