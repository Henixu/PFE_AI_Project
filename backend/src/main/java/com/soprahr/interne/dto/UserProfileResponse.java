package com.soprahr.interne.dto;

import com.soprahr.interne.entity.enums.Role;

/** Informations du compte connecté : jamais de hash de mot de passe ni de données internes. */
public record UserProfileResponse(
        Long id,
        String email,
        Role role,
        String firstName,
        String lastName,
        String jobTitle,
        String department) {
}
