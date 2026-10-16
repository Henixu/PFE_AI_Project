package com.soprahr.interne.exception;

/** Opération impossible car elle entre en conflit avec l'état existant (doublon, association déjà faite...). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
