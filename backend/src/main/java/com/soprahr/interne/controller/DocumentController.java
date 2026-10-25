package com.soprahr.interne.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.soprahr.interne.dto.DocumentResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.enums.DocumentVisibility;
import com.soprahr.interne.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean mine, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction, Authentication auth) {
        return service.list(search, mine, page, size, sortBy, direction, auth.getName());
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth.getName());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) DocumentVisibility visibility, Authentication auth) {
        return service.upload(file, title, visibility, auth.getName());
    }

    /** Toujours en pièce jointe, avec le type déterminé à l'enregistrement : le navigateur n'exécute ni n'interprète le fichier. */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id, Authentication auth) {
        DocumentService.DocumentContent content = service.download(id, auth.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.resource());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth.getName());
    }
}
