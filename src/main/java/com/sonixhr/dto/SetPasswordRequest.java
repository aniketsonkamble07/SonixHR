package com.sonixhr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SetPasswordRequest {
    @NotBlank
    private String token;
    @Pattern(regexp = "...") private String newPassword;

}
