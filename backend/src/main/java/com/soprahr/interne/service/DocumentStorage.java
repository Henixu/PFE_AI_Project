package com.soprahr.interne.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Stockage des fichiers sur disque, dans un répertoire dédié hors de tout dossier servi par l'application.
 * Les fichiers y portent un nom généré (UUID + extension autorisée) : le nom fourni par l'utilisateur
 * n'entre jamais dans un chemin. La base ne garde que ce nom de stockage.
 */
@Component
public class DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorage.class);

    private final Path root;

    public DocumentStorage(@Value("${app.documents.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    /** Écrit le contenu sous un nouveau nom généré et renvoie ce nom (jamais d'écrasement d'un fichier existant). */
    public String store(InputStream content, String extension) throws IOException {
        Files.createDirectories(root);
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = resolve(storedName);
        try (content) {
            Files.copy(content, target);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(storedName); // pas de fichier tronqué laissé sur le disque
            throw e;
        }
        return storedName;
    }

    /** Ressource en lecture ; NoSuchFileException si le fichier a disparu du disque. */
    public Resource load(String storedName) throws IOException {
        Path path = resolve(storedName);
        if (!Files.isRegularFile(path)) {
            throw new NoSuchFileException(storedName);
        }
        return new FileSystemResource(path);
    }

    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(resolve(storedName));
    }

    /** Suppression sans exception : un fichier orphelin est journalisé, il ne doit pas annuler l'opération en cours. */
    public void deleteQuietly(String storedName) {
        try {
            delete(storedName);
        } catch (IOException | RuntimeException e) {
            log.warn("Impossible de supprimer le fichier de stockage {} : {}", storedName, e.toString());
        }
    }

    /** Chemin absolu du fichier, garanti à l'intérieur du répertoire de stockage. */
    Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Chemin de stockage invalide");
        }
        return path;
    }
}
