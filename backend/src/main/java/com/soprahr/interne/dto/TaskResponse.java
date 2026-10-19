package com.soprahr.interne.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.soprahr.interne.entity.enums.TaskPriority;
import com.soprahr.interne.entity.enums.TaskStatus;

/**
 * {@code overdue} : échéance dépassée et tâche ni terminée ni annulée.
 * {@code canEdit} et {@code allowedStatuses} reflètent les droits de l'utilisateur connecté sur cette tâche,
 * pour que l'interface n'ait pas à recopier les règles d'accès.
 */
public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskPriority priority,
        TaskStatus status,
        LocalDate dueDate,
        boolean overdue,
        Long assigneeId,
        String assigneeName,
        String createdByEmail,
        Instant createdAt,
        Instant updatedAt,
        boolean canEdit,
        List<TaskStatus> allowedStatuses) {
}
