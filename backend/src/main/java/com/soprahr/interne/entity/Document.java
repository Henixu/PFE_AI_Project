package com.soprahr.interne.entity;

import jakarta.persistence.Column;
import com.soprahr.interne.entity.enums.DocumentVisibility;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Métadonnées d'un fichier ; le contenu est stocké hors base, à storagePath. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documents")
public class Document extends AuditableEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false, unique = true, length = 500)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
