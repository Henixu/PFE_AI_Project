package com.soprahr.interne.dto;

import java.time.LocalDate;

import com.soprahr.interne.entity.enums.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @NotNull TaskPriority priority,
        LocalDate dueDate,
        Long assigneeId) {
}
