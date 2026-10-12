package com.example.aidocumentassistant.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "chunks")
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private int page;

    @Column(nullable = false, length = 8000)
    private String text;

    protected DocumentChunk() {
    }

    public DocumentChunk(Document document, int chunkIndex, int page, String text) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.page = page;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getPage() {
        return page;
    }

    public String getText() {
        return text;
    }
}
