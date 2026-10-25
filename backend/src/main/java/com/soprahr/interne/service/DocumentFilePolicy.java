package com.soprahr.interne.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.soprahr.interne.exception.BusinessRuleException;

/**
 * Règles sur les fichiers téléversés : nom nettoyé, type autorisé par liste blanche et contenu cohérent avec
 * l'extension (signature des premiers octets). Le type de contenu renvoyé au téléchargement vient d'ici,
 * jamais de l'en-tête fourni par le client.
 */
final class DocumentFilePolicy {

    /** Nombre d'octets lus au début du fichier pour vérifier sa signature. */
    static final int HEAD_BYTES = 8192;
    static final int MAX_NAME_LENGTH = 255;

    record FileType(String extension, String contentType, Predicate<byte[]> signature) {
        boolean matches(byte[] head) {
            return signature.test(head);
        }
    }

    private static final Predicate<byte[]> ZIP = head -> startsWith(head, 0x50, 0x4B, 0x03, 0x04);

    private static final Map<String, FileType> TYPES = new LinkedHashMap<>();

    static {
        register("pdf", "application/pdf", head -> startsWith(head, 0x25, 0x50, 0x44, 0x46, 0x2D));
        register("png", "image/png", head -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A));
        register("jpg", "image/jpeg", head -> startsWith(head, 0xFF, 0xD8, 0xFF));
        register("jpeg", "image/jpeg", head -> startsWith(head, 0xFF, 0xD8, 0xFF));
        register("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP);
        register("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP);
        register("txt", "text/plain", head -> {
            for (byte b : head) {
                if (b == 0) {
                    return false; // un fichier texte ne contient pas d'octet nul
                }
            }
            return true;
        });
    }

    private DocumentFilePolicy() {
    }

    private static void register(String extension, String contentType, Predicate<byte[]> signature) {
        TYPES.put(extension, new FileType(extension, contentType, signature));
    }

    static String allowedExtensions() {
        return TYPES.keySet().stream().collect(Collectors.joining(", "));
    }

    /**
     * Nom d'affichage sûr : sans chemin, sans caractères de contrôle ni de mise en forme Unicode (qui permettent
     * de maquiller une extension), sans caractères réservés, sans point initial. Le nom n'est jamais utilisé
     * pour stocker le fichier.
     */
    static String cleanFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new BusinessRuleException("Nom de fichier manquant");
        }
        String name = Normalizer.normalize(original, Normalizer.Form.NFC);
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        StringBuilder clean = new StringBuilder(name.length());
        name.codePoints()
                .filter(cp -> !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT)
                .forEach(cp -> clean.appendCodePoint("<>:\"|?*".indexOf(cp) >= 0 ? '_' : cp));
        name = clean.toString().strip();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            throw new BusinessRuleException("Nom de fichier invalide");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException("Nom de fichier trop long (" + MAX_NAME_LENGTH + " caractères maximum)");
        }
        return name;
    }

    /** Type autorisé correspondant à l'extension du nom nettoyé ; refuse l'absence d'extension. */
    static FileType typeOf(String cleanName) {
        int dot = cleanName.lastIndexOf('.');
        FileType type = dot < 0 ? null : TYPES.get(cleanName.substring(dot + 1).toLowerCase(Locale.ROOT));
        if (type == null) {
            throw new BusinessRuleException("Type de fichier non autorisé. Formats acceptés : " + allowedExtensions());
        }
        return type;
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
