package com.soprahr.interne.exception;

/** Règle métier violée (ex. se retirer soi-même le rôle ADMIN). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
