package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.soprahr.interne.dto.DocumentResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Document;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.DocumentVisibility;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.DocumentRepository;
import com.soprahr.interne.repository.EmployeeRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceTest {

    private static final byte[] PDF = "%PDF-1.7 contenu".getBytes(StandardCharsets.US_ASCII);

    @Mock DocumentRepository documents;
    @Mock EmployeeRepository employees;
    @Mock ActorResolver actors;
    @Mock AuditService audit;

    @TempDir Path storageDir;

    private DocumentStorage storage;
    private DocumentService service;

    private User hrUser;
    private User aliceUser;
    private User bobUser;

    @BeforeEach
    void setUp() {
        storage = new DocumentStorage(storageDir.toString());
        service = new DocumentService(documents, employees, actors, audit, storage, 1); // 1 Mo
        hrUser = TestData.user(1L, "hr@test.local", Role.HR);
        aliceUser = TestData.user(3L, "alice@test.local", Role.EMPLOYEE);
        bobUser = TestData.user(4L, "bob@test.local", Role.EMPLOYEE);
        Employee hr = TestData.employee(11L, "Rania", "Rh", hrUser, null);
        Employee alice = TestData.employee(13L, "Alice", "Dupont", aliceUser, null);

        when(actors.resolve("hr@test.local")).thenReturn(new CurrentActor(hrUser, hr));
        when(actors.resolve("alice@test.local")).thenReturn(new CurrentActor(aliceUser, alice));
        when(actors.resolve("bob@test.local")).thenReturn(new CurrentActor(bobUser, null));
        when(employees.findByUserIdIn(anyCollection())).thenReturn(List.of(alice, hr));
        when(documents.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 7L);
            return d;
        });
    }

    // ---- téléversement ----

    @Test
    void uploadStoresTheFileUnderAGeneratedNameAndRecordsMetadata() throws IOException {
        DocumentResponse r = service.upload(file("Contrat Alice.PDF", PDF), "  Mon contrat ", null, "alice@test.local");

        assertEquals("Mon contrat", r.title());
        assertEquals("Contrat Alice.PDF", r.fileName());
        assertEquals("application/pdf", r.contentType());
        assertEquals(PDF.length, r.sizeBytes());
        assertEquals(DocumentVisibility.PRIVATE, r.visibility());
        assertEquals("Alice Dupont", r.ownerName());
        assertTrue(r.canDelete());

        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documents).save(saved.capture());
        String storedName = saved.getValue().getStoragePath();
        assertTrue(storedName.matches("[0-9a-f-]{36}\\.pdf"), storedName);
        assertArrayEquals(PDF, Files.readAllBytes(storageDir.resolve(storedName)));
        verify(audit).record(eq("alice@test.local"), eq("DOCUMENT_UPLOAD"), eq("Document"), eq(7L), any());
    }

    @Test
    void titleDefaultsToTheFileNameWithoutExtension() {
        assertEquals("rapport annuel", service.upload(file("rapport annuel.pdf", PDF), "  ", null, "alice@test.local").title());
    }

    @Test
    void uploadedNameCannotEscapeTheStorageDirectory() throws IOException {
        DocumentResponse r = service.upload(file("../../evil.pdf", PDF), null, null, "alice@test.local");

        assertEquals("evil.pdf", r.fileName());
        try (Stream<Path> files = Files.walk(storageDir)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
        assertFalse(Files.exists(storageDir.resolveSibling("evil.pdf")));
    }

    @Test
    void uploadRejectsEmptyOversizedAndUnlistedFilesWithoutWritingAnything() throws IOException {
        assertThrows(BusinessRuleException.class,
                () -> service.upload(file("vide.pdf", new byte[0]), null, null, "alice@test.local"));
        assertThrows(BusinessRuleException.class,
                () -> service.upload(null, null, null, "alice@test.local"));
        byte[] big = new byte[1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThrows(BusinessRuleException.class, () -> service.upload(file("gros.pdf", big), null, null, "alice@test.local"));
        assertThrows(BusinessRuleException.class,
                () -> service.upload(file("virus.exe", "MZ".getBytes(StandardCharsets.US_ASCII)), null, null, "alice@test.local"));
        assertThrows(BusinessRuleException.class,
                () -> service.upload(file("noext", PDF), null, null, "alice@test.local"));

        verify(documents, never()).save(any());
        try (Stream<Path> files = Files.list(storageDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void uploadRejectsContentThatDoesNotMatchTheExtension() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> service.upload(
                file("faux.pdf", "MZ... un exécutable".getBytes(StandardCharsets.US_ASCII)), null, null, "alice@test.local"));

        assertTrue(e.getMessage().contains("ne correspond pas"));
        verify(documents, never()).save(any());
    }

    @Test
    void uploadRejectsTooLongTitle() {
        assertThrows(BusinessRuleException.class,
                () -> service.upload(file("a.pdf", PDF), "t".repeat(201), null, "alice@test.local"));
    }

    @Test
    void onlyHrOrAdminCanPublishForEveryone() {
        assertThrows(AccessDeniedException.class,
                () -> service.upload(file("a.pdf", PDF), null, DocumentVisibility.INTERNAL, "alice@test.local"));
        verify(documents, never()).save(any());

        assertEquals(DocumentVisibility.INTERNAL,
                service.upload(file("a.pdf", PDF), null, DocumentVisibility.INTERNAL, "hr@test.local").visibility());
    }

    @Test
    void storedFileIsRemovedWhenTheTransactionRollsBack() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.upload(file("a.pdf", PDF), null, null, "alice@test.local");
            try (Stream<Path> files = Files.list(storageDir)) {
                assertEquals(1, files.count());
            }

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        try (Stream<Path> files = Files.list(storageDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void storedFileIsKeptWhenTheTransactionCommits() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.upload(file("a.pdf", PDF), null, null, "alice@test.local");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        try (Stream<Path> files = Files.list(storageDir)) {
            assertEquals(1, files.count());
        }
    }

    // ---- liste et métadonnées ----

    @Test
    @SuppressWarnings("unchecked")
    void listRestrictsNonPrivilegedUsersToOwnAndInternalDocuments() {
        when(documents.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Document>(List.of(), PageRequest.of(0, 10), 0));
        ArgumentCaptor<Specification<Document>> spec = ArgumentCaptor.forClass(Specification.class);

        service.list(null, null, 0, 10, "createdAt", "desc", "alice@test.local");
        verify(documents).findAll(spec.capture(), any(Pageable.class));
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        spec.getValue().toPredicate(mock(Root.class, RETURNS_DEEP_STUBS), mock(CriteriaQuery.class), cb);
        verify(cb).equal(any(), eq(DocumentVisibility.INTERNAL));
        // « propriétaire OU INTERNAL » : les prédicats simulés valent null, d'où le any() typé qui l'accepte
        verify(cb).or(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Predicate>any(),
                org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Predicate>any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listDoesNotRestrictHr() {
        when(documents.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Document>(List.of(), PageRequest.of(0, 10), 0));
        ArgumentCaptor<Specification<Document>> spec = ArgumentCaptor.forClass(Specification.class);

        service.list(null, null, 0, 10, "createdAt", "desc", "hr@test.local");
        verify(documents).findAll(spec.capture(), any(Pageable.class));
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        spec.getValue().toPredicate(mock(Root.class, RETURNS_DEEP_STUBS), mock(CriteriaQuery.class), cb);
        verify(cb, never()).equal(any(), eq(DocumentVisibility.INTERNAL));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listProjectsAuthorNamesAndDeletePermission() throws IOException {
        Document own = stored(1L, aliceUser, DocumentVisibility.PRIVATE);
        Document shared = stored(2L, hrUser, DocumentVisibility.INTERNAL);
        Page<Document> page = new PageImpl<>(List.of(own, shared), PageRequest.of(0, 10), 2);
        when(documents.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<DocumentResponse> r = service.list(null, null, 0, 10, "createdAt", "desc", "alice@test.local");

        assertEquals(2, r.totalElements());
        assertEquals("Alice Dupont", r.content().get(0).ownerName());
        assertTrue(r.content().get(0).canDelete());
        assertEquals("Rania Rh", r.content().get(1).ownerName());
        assertFalse(r.content().get(1).canDelete()); // publié par les RH : visible mais pas supprimable par Alice
    }

    @Test
    void getReturnsMetadataToOwnerAndHrButHidesPrivateDocumentsFromOthers() throws IOException {
        Document doc = stored(1L, aliceUser, DocumentVisibility.PRIVATE);

        DocumentResponse r = service.get(1L, "alice@test.local");
        assertEquals("alice@test.local", r.ownerEmail());
        assertEquals("contrat.pdf", r.fileName());
        assertEquals(doc.getCreatedAt(), r.createdAt());
        assertEquals(1L, service.get(1L, "hr@test.local").id());
        assertThrows(ResourceNotFoundException.class, () -> service.get(1L, "bob@test.local"));
        assertThrows(ResourceNotFoundException.class, () -> service.get(99L, "alice@test.local"));
    }

    @Test
    void internalDocumentsAreVisibleToEveryone() throws IOException {
        stored(1L, hrUser, DocumentVisibility.INTERNAL);

        assertEquals(1L, service.get(1L, "bob@test.local").id());
    }

    // ---- téléchargement ----

    @Test
    void downloadChecksPermissionBeforeReturningTheFile() throws IOException {
        stored(1L, aliceUser, DocumentVisibility.PRIVATE);

        DocumentService.DocumentContent content = service.download(1L, "alice@test.local");
        assertEquals("contrat.pdf", content.fileName());
        assertEquals("application/pdf", content.contentType());
        assertArrayEquals(PDF, content.resource().getContentAsByteArray());
        verify(audit).record(eq("alice@test.local"), eq("DOCUMENT_DOWNLOAD"), eq("Document"), eq(1L), any());

        assertThrows(ResourceNotFoundException.class, () -> service.download(1L, "bob@test.local"));
        verify(audit, org.mockito.Mockito.times(1)).record(any(), eq("DOCUMENT_DOWNLOAD"), any(), any(), any());
    }

    @Test
    void downloadReportsAMissingFileAsNotFound() throws IOException {
        Document doc = stored(1L, aliceUser, DocumentVisibility.PRIVATE);
        Files.delete(storageDir.resolve(doc.getStoragePath()));

        assertThrows(ResourceNotFoundException.class, () -> service.download(1L, "alice@test.local"));
    }

    // ---- suppression ----

    @Test
    void ownerDeletesTheirDocumentAndItsFile() throws IOException {
        Document doc = stored(1L, aliceUser, DocumentVisibility.PRIVATE);

        service.delete(1L, "alice@test.local");

        verify(documents).delete(doc);
        verify(audit).record(eq("alice@test.local"), eq("DOCUMENT_DELETE"), eq("Document"), eq(1L), any());
        assertFalse(Files.exists(storageDir.resolve(doc.getStoragePath())));
    }

    @Test
    void fileIsDeletedOnlyAfterTheTransactionCommits() throws IOException {
        Document doc = stored(1L, aliceUser, DocumentVisibility.PRIVATE);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(1L, "alice@test.local");
            assertTrue(Files.exists(storageDir.resolve(doc.getStoragePath())), "pas avant la validation");

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertFalse(Files.exists(storageDir.resolve(doc.getStoragePath())));
    }

    @Test
    void fileIsKeptWhenTheDeletionTransactionRollsBack() throws IOException {
        Document doc = stored(1L, aliceUser, DocumentVisibility.PRIVATE);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(1L, "alice@test.local");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertTrue(Files.exists(storageDir.resolve(doc.getStoragePath())));
    }

    @Test
    void hrDeletesAnyDocumentButOthersOnlyTheirOwn() throws IOException {
        Document doc = stored(1L, aliceUser, DocumentVisibility.INTERNAL);

        // Bob voit le document (INTERNAL) mais n'en est pas propriétaire : refus 403, fichier intact
        assertThrows(AccessDeniedException.class, () -> service.delete(1L, "bob@test.local"));
        verify(documents, never()).delete(any(Document.class));
        assertTrue(Files.exists(storageDir.resolve(doc.getStoragePath())));

        service.delete(1L, "hr@test.local");
        verify(documents).delete(doc);
    }

    @Test
    void deletingAnInvisibleDocumentIsReportedAsNotFound() throws IOException {
        stored(1L, aliceUser, DocumentVisibility.PRIVATE);

        assertThrows(ResourceNotFoundException.class, () -> service.delete(1L, "bob@test.local"));
        verify(documents, never()).delete(any(Document.class));
    }

    @Test
    void storageRefusesPathsOutsideItsRoot() {
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve(".."));
        assertNotEquals(storageDir.toAbsolutePath().normalize(), storage.resolve("demo/a.pdf"));
    }

    // ---- fabriques ----

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    /** Document enregistré en base (simulée) avec un vrai fichier dans le stockage temporaire. */
    private Document stored(long id, User owner, DocumentVisibility visibility) throws IOException {
        String storedName = storage.store(new java.io.ByteArrayInputStream(PDF), "pdf");
        Document doc = new Document();
        ReflectionTestUtils.setField(doc, "id", id);
        ReflectionTestUtils.setField(doc, "createdAt", java.time.Instant.parse("2026-10-01T10:00:00Z"));
        doc.setTitle("Contrat");
        doc.setFileName("contrat.pdf");
        doc.setContentType("application/pdf");
        doc.setSizeBytes(PDF.length);
        doc.setStoragePath(storedName);
        doc.setVisibility(visibility);
        doc.setOwner(owner);
        when(documents.findWithOwnerById(id)).thenReturn(Optional.of(doc));
        return doc;
    }
}
