package com.soprahr.interne.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        // 72 octets : limite de BCrypt
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
