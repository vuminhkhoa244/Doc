package com.example.aidocumentassistant.document;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentTextExtractor {
    private final PdfTextExtractor pdfTextExtractor;
    private final DocxTextExtractor docxTextExtractor;

    public DocumentTextExtractor(PdfTextExtractor pdfTextExtractor, DocxTextExtractor docxTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.docxTextExtractor = docxTextExtractor;
    }

    public List<ExtractedText> extract(Path path, String filename, String contentType) throws IOException {
        DocumentType type = DocumentType.from(filename, contentType);
        return switch (type) {
            case PDF -> pdfTextExtractor.extract(path);
            case DOCX -> docxTextExtractor.extract(path);
            case MARKDOWN, TEXT -> extractPlainText(path);
        };
    }

    private List<ExtractedText> extractPlainText(Path path) throws IOException {
        String text = normalize(Files.readString(path, StandardCharsets.UTF_8));
        return text.isBlank() ? List.of() : List.of(new ExtractedText(1, text));
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    public enum DocumentType {
        PDF,
        DOCX,
        MARKDOWN,
        TEXT;

        public static DocumentType from(String filename, String contentType) {
            String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            if (lowerFilename.endsWith(".pdf") || lowerContentType.equals("application/pdf")) {
                return PDF;
            }
            if (lowerFilename.endsWith(".docx")
                    || lowerContentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                return DOCX;
            }
            if (lowerFilename.endsWith(".md") || lowerFilename.endsWith(".markdown")
                    || lowerContentType.equals("text/markdown")) {
                return MARKDOWN;
            }
            if (lowerFilename.endsWith(".txt") || lowerContentType.startsWith("text/plain")) {
                return TEXT;
            }
            throw new IllegalArgumentException("Unsupported document type");
        }
    }
}
