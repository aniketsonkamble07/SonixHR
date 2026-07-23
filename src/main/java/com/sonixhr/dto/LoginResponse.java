package com.sonixhr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    // Success fields
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String email;
    private String fullName;
    private Long userId;
    private boolean success;

    // Error fields
    private String message;
    private String errorCode;

    // Password change required fields
    private boolean requiresPasswordChange;
    private String resetToken;
    private String changePasswordUrl;

    // Activation required fields
    private boolean requiresActivation;
    private String activationLink;
}