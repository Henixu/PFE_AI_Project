package com.soprahr.interne.exception;

public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("Lien de réinitialisation invalide ou expiré");
    }
}
