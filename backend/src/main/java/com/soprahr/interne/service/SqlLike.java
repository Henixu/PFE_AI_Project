package com.soprahr.interne.service;

import java.util.Locale;

/** Construit un motif LIKE « contient » en échappant les jokers saisis par l'utilisateur (escape = '\'). */
final class SqlLike {

    private SqlLike() {
    }

    /** Motif en minuscules, ou null si la recherche est vide. */
    static String contains(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        return "%" + term.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
