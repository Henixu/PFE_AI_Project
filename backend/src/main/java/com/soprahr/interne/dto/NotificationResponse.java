package com.soprahr.interne.dto;

import java.time.Instant;

import com.soprahr.interne.entity.enums.NotificationType;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt,
        Instant readAt) {
}
