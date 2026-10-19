package com.soprahr.interne.dto;

import com.soprahr.interne.entity.enums.TaskStatus;

import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {
}
