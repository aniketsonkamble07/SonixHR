package com.sonixhr.dto.employee;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class EmployeeUpdateRequest extends EmployeeCreateRequest {
}
