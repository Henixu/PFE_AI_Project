package com.soprahr.interne.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implémentation de développement : écrit le lien dans les logs faute de serveur SMTP.
 * À remplacer par un envoi d'e-mail avant toute mise en production (le lien donne accès au compte).
 */
@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    @Override
    public void sendResetLink(String email, String resetLink) {
        log.info("[DEV] Lien de réinitialisation pour {} : {}", email, resetLink);
    }
}
