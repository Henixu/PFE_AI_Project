package com.soprahr.interne.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Seule l'adresse e-mail est modifiable ici : rôle, statut et employé ont leurs propres opérations auditées. */
public record UpdateUserRequest(@NotBlank @Email @Size(max = 255) String email) {
}
