package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.auth.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private Instant uploadTime = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.INDEXING;

    @Column(nullable = false)
    private int chunks;

    @Column(length = 2000)
    private String errorMessage;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentChunk> documentChunks = new ArrayList<>();

    protected Document() {
    }

    public Document(AppUser owner, String filename, long size, String contentType, String storagePath) {
        this.owner = owner;
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.storagePath = storagePath;
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getUploadTime() {
        return uploadTime;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getChunks() {
        return chunks;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<DocumentChunk> getDocumentChunks() {
        return documentChunks;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void markIndexed(int chunks) {
        this.status = DocumentStatus.INDEXED;
        this.chunks = chunks;
        this.errorMessage = null;
    }

    public void markIndexedWithoutVectors(int chunks) {
        this.status = DocumentStatus.INDEXED;
        this.chunks = chunks;
        this.errorMessage = "AI service unavailable; stored for keyword search. Use reindex to add AI vectors.";
    }

    public void markIndexing() {
        this.status = DocumentStatus.INDEXING;
        this.chunks = 0;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
