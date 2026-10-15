package com.soprahr.interne.service;

/** Canal d'envoi du lien de réinitialisation (e-mail à brancher en production). */
public interface PasswordResetNotifier {

    void sendResetLink(String email, String resetLink);
}
