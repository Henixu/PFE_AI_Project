package com.soprahr.interne.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.soprahr.interne.entity.enums.LeaveStatus;
import com.soprahr.interne.entity.enums.LeaveType;

/**
 * {@code durationDays} compte les jours calendaires, bornes incluses.
 * {@code canDecide} / {@code canCancel} reflètent les droits de l'utilisateur connecté sur cette demande.
 * La décision (auteur, date, commentaire) est conservée une fois la demande traitée.
 */
public record LeaveResponse(
        Long id,
        Long employeeId,
        String employeeName,
        String employeeNumber,
        String departmentName,
        LeaveType type,
        LocalDate startDate,
        LocalDate endDate,
        long durationDays,
        String reason,
        LeaveStatus status,
        Instant createdAt,
        String decidedBy,
        Instant decidedAt,
        String decisionComment,
        boolean canDecide,
        boolean canCancel) {
}
