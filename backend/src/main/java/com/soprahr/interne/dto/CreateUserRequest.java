package com.soprahr.interne.dto;

import com.soprahr.interne.entity.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // 72 octets : limite de BCrypt
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role,
        /** Compte actif dès la création si absent. */
        Boolean enabled,
        /** Employé à associer au compte (optionnel). */
        Long employeeId) {
}
