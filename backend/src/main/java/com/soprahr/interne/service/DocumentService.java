package com.soprahr.interne.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.soprahr.interne.dto.DocumentResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Document;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.enums.DocumentVisibility;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.DocumentRepository;
import com.soprahr.interne.repository.EmployeeRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * Gestion des documents. Chaque opération vérifie les droits côté serveur :
 * <ul>
 * <li>un document PRIVATE est visible de son propriétaire, des ADMIN et des HR ;</li>
 * <li>un document INTERNAL est visible de tout utilisateur connecté, mais seuls ADMIN et HR peuvent en publier ;</li>
 * <li>supprimer est réservé au propriétaire, aux ADMIN et aux HR ;</li>
 * <li>un document hors périmètre répond 404 (son existence n'est pas révélée).</li>
 * </ul>
 * Le fichier est écrit avant l'enregistrement en base et retiré si la transaction échoue ; à la suppression,
 * la ligne est retirée d'abord et le fichier seulement une fois la transaction validée.
 */
@Service
@PreAuthorize("isAuthenticated()")
@Transactional
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int TITLE_MAX = 200;
    private static final Set<String> SORTABLE = Set.of("createdAt", "title", "fileName", "sizeBytes");

    /** Contenu à envoyer au client lors d'un téléchargement. */
    public record DocumentContent(String fileName, String contentType, Resource resource) {
    }

    private final DocumentRepository documents;
    private final EmployeeRepository employees;
    private final ActorResolver actors;
    private final AuditService audit;
    private final DocumentStorage storage;
    private final long maxSizeBytes;

    public DocumentService(DocumentRepository documents, EmployeeRepository employees, ActorResolver actors,
            AuditService audit, DocumentStorage storage, @Value("${app.documents.max-size-mb:10}") long maxSizeMb) {
        this.documents = documents;
        this.employees = employees;
        this.actors = actors;
        this.audit = audit;
        this.storage = storage;
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    public DocumentResponse upload(MultipartFile file, String title, DocumentVisibility visibility, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Aucun fichier fourni ou fichier vide");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessRuleException("Fichier trop volumineux (" + maxSizeBytes / (1024 * 1024) + " Mo maximum)");
        }
        String fileName = DocumentFilePolicy.cleanFileName(file.getOriginalFilename());
        DocumentFilePolicy.FileType type = DocumentFilePolicy.typeOf(fileName);
        if (!type.matches(readHead(file))) {
            throw new BusinessRuleException("Le contenu du fichier ne correspond pas à son extension (." + type.extension() + ")");
        }
        DocumentVisibility scope = visibility == null ? DocumentVisibility.PRIVATE : visibility;
        if (scope == DocumentVisibility.INTERNAL && !actor.isAdminOrHr()) {
            throw new AccessDeniedException("Seuls les RH et les administrateurs peuvent publier un document pour tous");
        }
        String documentTitle = resolveTitle(title, fileName);

        String storedName;
        try (InputStream content = file.getInputStream()) {
            storedName = storage.store(content, type.extension());
        } catch (IOException e) {
            throw new IllegalStateException("Enregistrement du fichier impossible", e);
        }
        afterRollback(() -> storage.deleteQuietly(storedName));

        Document document = new Document();
        document.setTitle(documentTitle);
        document.setFileName(fileName);
        document.setContentType(type.contentType());
        document.setSizeBytes(file.getSize());
        document.setStoragePath(storedName);
        document.setVisibility(scope);
        document.setOwner(actor.user());
        document = documents.save(document);
        audit.record(actorEmail, "DOCUMENT_UPLOAD", "Document", document.getId(),
                "name=" + fileName + ", size=" + file.getSize() + ", visibility=" + scope);
        Employee author = actor.employee();
        return toResponse(document, actor, author == null ? Map.of()
                : Map.of(actor.user().getId(), author.getFirstName() + " " + author.getLastName()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(String search, Boolean mine, int page, int size, String sortBy,
            String direction, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(new Sort.Order(dir, SORTABLE.contains(sortBy) ? sortBy : "createdAt"),
                        new Sort.Order(Sort.Direction.DESC, "id")));
        String pattern = SqlLike.contains(search);
        Long userId = actor.user().getId();
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Predicate owned = cb.equal(root.get("owner").get("id"), userId);
            if (!actor.isAdminOrHr()) {
                predicates.add(cb.or(owned, cb.equal(root.get("visibility"), DocumentVisibility.INTERNAL)));
            }
            if (Boolean.TRUE.equals(mine)) {
                predicates.add(owned);
            }
            if (pattern != null) {
                predicates.add(cb.or(cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("fileName")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Document> result = documents.findAll(spec, pageable);
        Map<Long, String> names = ownerNames(result.getContent());
        return PageResponse.of(result, result.getContent().stream().map(d -> toResponse(d, actor, names)).toList());
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        Document document = findVisible(id, actor);
        return toResponse(document, actor, ownerNames(List.of(document)));
    }

    /** Vérifie le droit d'accès avant de rendre le fichier, et trace le téléchargement. */
    public DocumentContent download(Long id, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        Document document = findVisible(id, actor);
        audit.record(actorEmail, "DOCUMENT_DOWNLOAD", "Document", id, "name=" + document.getFileName());
        try {
            Resource resource = storage.load(document.getStoragePath());
            String contentType = document.getContentType() == null ? "application/octet-stream" : document.getContentType();
            return new DocumentContent(document.getFileName(), contentType, resource);
        } catch (NoSuchFileException e) {
            log.error("Fichier absent du stockage pour le document {} ({})", id, document.getStoragePath());
            throw new ResourceNotFoundException("Le fichier de ce document n'est plus disponible");
        } catch (IOException e) {
            throw new IllegalStateException("Lecture du fichier impossible", e);
        }
    }

    public void delete(Long id, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        Document document = findVisible(id, actor);
        if (!canDelete(document, actor)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à supprimer ce document");
        }
        String storedName = document.getStoragePath();
        documents.delete(document);
        audit.record(actorEmail, "DOCUMENT_DELETE", "Document", id, "name=" + document.getFileName());
        afterCommit(() -> storage.deleteQuietly(storedName));
    }

    // ---- droits ----

    private Document findVisible(Long id, CurrentActor actor) {
        Document document = documents.findWithOwnerById(id).orElseThrow(() -> notFound(id));
        if (!canView(document, actor)) {
            throw notFound(id);
        }
        return document;
    }

    private static boolean canView(Document document, CurrentActor actor) {
        return actor.isAdminOrHr() || isOwner(document, actor) || document.getVisibility() == DocumentVisibility.INTERNAL;
    }

    private static boolean canDelete(Document document, CurrentActor actor) {
        return actor.isAdminOrHr() || isOwner(document, actor);
    }

    private static boolean isOwner(Document document, CurrentActor actor) {
        return document.getOwner().getId().equals(actor.user().getId());
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Document introuvable: " + id);
    }

    // ---- fichiers et transaction ----

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(DocumentFilePolicy.HEAD_BYTES);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture du fichier téléversé impossible", e);
        }
    }

    /** Titre saisi, ou nom du fichier sans son extension à défaut. */
    private static String resolveTitle(String title, String fileName) {
        if (title != null && !title.isBlank()) {
            String trimmed = title.trim();
            if (trimmed.length() > TITLE_MAX) {
                throw new BusinessRuleException("Le titre ne doit pas dépasser " + TITLE_MAX + " caractères");
            }
            return trimmed;
        }
        String base = fileName.substring(0, fileName.lastIndexOf('.'));
        return base.length() <= TITLE_MAX ? base : base.substring(0, TITLE_MAX);
    }

    /** Exécute l'action si la transaction est annulée (hors transaction, ne fait rien). */
    private static void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        action.run();
                    }
                }
            });
        }
    }

    /** Exécute l'action une fois la transaction validée (immédiatement s'il n'y en a pas). */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ---- projection ----

    /** Nom de l'employé auteur de chaque document, en une requête ; l'e-mail du compte à défaut. */
    private Map<Long, String> ownerNames(List<Document> content) {
        List<Long> userIds = content.stream().map(d -> d.getOwner().getId()).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return employees.findByUserIdIn(userIds).stream().collect(Collectors.toMap(e -> e.getUser().getId(),
                e -> e.getFirstName() + " " + e.getLastName(), (a, b) -> a));
    }

    private DocumentResponse toResponse(Document d, CurrentActor actor, Map<Long, String> ownerNames) {
        String email = d.getOwner().getEmail();
        return new DocumentResponse(d.getId(), d.getTitle(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getVisibility(), email, ownerNames.getOrDefault(d.getOwner().getId(), email), d.getCreatedAt(),
                canDelete(d, actor));
    }
}
