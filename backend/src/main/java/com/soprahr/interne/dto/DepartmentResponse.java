package com.soprahr.interne.dto;

import java.time.Instant;
import java.util.List;

/** {@code employees} n'est renseigné que pour le détail d'un département (null dans les listes). */
public record DepartmentResponse(
        Long id,
        String name,
        String description,
        long employeeCount,
        Instant createdAt,
        Instant updatedAt,
        List<DepartmentEmployeeResponse> employees) {
}
