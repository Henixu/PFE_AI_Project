package com.soprahr.interne.dto;

import java.time.Instant;

import com.soprahr.interne.entity.enums.DocumentVisibility;

/**
 * Métadonnées d'un document. {@code ownerName} est le nom de l'employé auteur (l'e-mail à défaut) ;
 * {@code canDelete} reflète les droits de l'utilisateur connecté.
 */
public record DocumentResponse(
        Long id,
        String title,
        String fileName,
        String contentType,
        long sizeBytes,
        DocumentVisibility visibility,
        String ownerEmail,
        String ownerName,
        Instant createdAt,
        boolean canDelete) {
}
