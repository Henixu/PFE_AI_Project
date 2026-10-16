package com.soprahr.interne.dto;

import jakarta.validation.constraints.NotNull;

public record LinkEmployeeRequest(@NotNull Long employeeId) {
}
