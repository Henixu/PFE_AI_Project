package com.soprahr.interne.exception;

/** Identifiants refusés ; le message reste volontairement générique (pas d'énumération de comptes). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Identifiants invalides");
    }
}
