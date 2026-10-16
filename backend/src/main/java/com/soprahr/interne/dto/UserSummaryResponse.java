package com.soprahr.interne.dto;

import java.time.Instant;

import com.soprahr.interne.entity.enums.Role;

/** Vue administrateur d'un compte (jamais le hash du mot de passe). */
public record UserSummaryResponse(
        Long id,
        String email,
        Role role,
        boolean enabled,
        Instant lastLoginAt,
        Instant createdAt,
        Long employeeId,
        String employeeName) {
}
