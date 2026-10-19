package com.soprahr.interne.dto;

import java.time.LocalDate;

import com.soprahr.interne.entity.enums.LeaveType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code type} est facultatif (congé annuel par défaut). */
public record LeaveSubmitRequest(
        LeaveType type,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(max = 500) String reason) {
}
