package com.sonixhr.dto.leave;

import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LeaveResponseDTO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private LeaveType leaveType;
    private String leaveTypeDisplay;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDays;
    private String reason;
    private LeaveStatus status;
    private String statusDisplay;
    private String rejectionReason;
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private Boolean isHalfDay;
}