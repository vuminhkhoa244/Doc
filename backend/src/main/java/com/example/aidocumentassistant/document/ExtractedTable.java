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
@Table(name = "document_tables")
public class ExtractedTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private int page;

    @Column(nullable = false, length = 20000)
    private String markdown;

    @Column(nullable = false)
    private int rowCount;

    protected ExtractedTable() {
    }

    public ExtractedTable(Document document, int page, String markdown, int rowCount) {
        this.document = document;
        this.page = page;
        this.markdown = markdown;
        this.rowCount = rowCount;
    }

    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public int getPage() {
        return page;
    }

    public String getMarkdown() {
        return markdown;
    }

    public int getRowCount() {
        return rowCount;
    }
}
