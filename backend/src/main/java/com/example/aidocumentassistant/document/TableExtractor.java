package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.document.DocumentTextExtractor.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class TableExtractor {
    private static final Logger log = LoggerFactory.getLogger(TableExtractor.class);

    private final DocxTableExtractor docxTableExtractor;
    private final MarkdownTableExtractor markdownTableExtractor;
    private final PdfRuledTableExtractor pdfRuledTableExtractor;

    public TableExtractor(
            DocxTableExtractor docxTableExtractor,
            MarkdownTableExtractor markdownTableExtractor,
            PdfRuledTableExtractor pdfRuledTableExtractor
    ) {
        this.docxTableExtractor = docxTableExtractor;
        this.markdownTableExtractor = markdownTableExtractor;
        this.pdfRuledTableExtractor = pdfRuledTableExtractor;
    }

    public List<TableData> extract(Path path, String filename, String contentType) {
        DocumentType type;
        try {
            type = DocumentType.from(filename, contentType);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
        try {
            return switch (type) {
                case PDF -> pdfRuledTableExtractor.extract(path);
                case DOCX -> docxTableExtractor.extract(path);
                case MARKDOWN, TEXT -> markdownTableExtractor.extract(Files.readString(path));
                case IMAGE -> List.of();
            };
        } catch (Exception exception) {
            log.warn("Table extraction failed for {}: {}", path.getFileName(), exception.getMessage());
            return List.of();
        }
    }

    public record TableData(int page, String markdown, int rowCount) {
    }
}
