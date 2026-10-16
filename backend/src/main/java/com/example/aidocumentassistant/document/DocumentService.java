package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.auth.AppUser;
import com.example.aidocumentassistant.auth.UserRepository;
import com.example.aidocumentassistant.config.AppProperties;
import com.example.aidocumentassistant.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final UserRepository users;
    private final AppProperties properties;
    private final DocumentTextExtractor documentTextExtractor;
    private final TextChunker textChunker;
    private final AiClient aiClient;

    public DocumentService(
            DocumentRepository documents,
            DocumentChunkRepository chunks,
            UserRepository users,
            AppProperties properties,
            DocumentTextExtractor documentTextExtractor,
            TextChunker textChunker,
            AiClient aiClient
    ) {
        this.documents = documents;
        this.chunks = chunks;
        this.users = users;
        this.properties = properties;
        this.documentTextExtractor = documentTextExtractor;
        this.textChunker = textChunker;
        this.aiClient = aiClient;
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.DocumentResponse> list(AuthenticatedUser principal) {
        return documents.findByOwnerIdOrderByUploadTimeDesc(principal.id())
                .stream()
                .map(DocumentDtos.DocumentResponse::from)
                .toList();
    }

    @Transactional
    public DocumentDtos.DocumentResponse upload(AuthenticatedUser principal, MultipartFile file) {
        validateSupportedDocument(file);
        AppUser owner = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        try {
            Files.createDirectories(properties.storage().uploadDir());
            String safeName = sanitizeFilename(file.getOriginalFilename());
            Path storagePath = properties.storage().uploadDir().resolve(UUID.randomUUID() + "-" + safeName);
            Files.copy(file.getInputStream(), storagePath, StandardCopyOption.REPLACE_EXISTING);

            Document document = documents.save(new Document(
                    owner,
                    safeName,
                    file.getSize(),
                    normalizedContentType(file),
                    storagePath.toAbsolutePath().normalize().toString()
            ));
            index(document, storagePath);
            return DocumentDtos.DocumentResponse.from(document);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store document");
        }
    }

    @Transactional
    public void delete(AuthenticatedUser principal, Long id) {
        Document document = documents.findByIdAndOwnerId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        documents.delete(document);
        try {
            Files.deleteIfExists(Path.of(document.getStoragePath()));
        } catch (IOException ignored) {
        }
        try {
            aiClient.deleteIndex(id);
        } catch (RestClientException ignored) {
        }
    }

    @Transactional
    public DocumentDtos.DocumentResponse reindex(AuthenticatedUser principal, Long id) {
        Document document = documents.findByIdAndOwnerId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        chunks.deleteAllByDocument(document);
        try {
            aiClient.deleteIndex(id);
        } catch (RestClientException ignored) {
        }
        Path storagePath = Path.of(document.getStoragePath());
        if (!Files.exists(storagePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file no longer exists");
        }
        document.markIndexing();
        index(document, storagePath);
        return DocumentDtos.DocumentResponse.from(document);
    }

    @Transactional
    public DocumentDtos.DocumentResponse rename(AuthenticatedUser principal, Long id, String filename) {
        Document document = documents.findByIdAndOwnerId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        String sanitized = sanitizeFilename(filename);
        document.setFilename(sanitized);
        documents.save(document);
        return DocumentDtos.DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public DocumentFile download(AuthenticatedUser principal, Long id) {
        Document document = documents.findByIdAndOwnerId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        Path path = Path.of(document.getStoragePath());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file no longer exists");
        }
        return new DocumentFile(document.getFilename(), document.getContentType(), path);
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.PageChunkResponse> pageChunks(AuthenticatedUser principal, Long id, int page) {
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 1 or greater");
        }
        Document document = documents.findByIdAndOwnerId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return chunks.findByDocumentIdAndPageOrderByChunkIndexAsc(document.getId(), page)
                .stream()
                .map(chunk -> new DocumentDtos.PageChunkResponse(chunk.getChunkIndex(), chunk.getPage(), chunk.getText()))
                .toList();
    }

    public record DocumentFile(String filename, String contentType, Path path) {
    }

    private void index(Document document, Path storagePath) {
        int savedChunks = 0;
        try {
            List<TextChunker.ChunkText> splitChunks = textChunker.split(
                    documentTextExtractor.extract(storagePath, document.getFilename(), document.getContentType())
            );
            if (splitChunks.isEmpty()) {
                document.markFailed("No extractable text found in document (scanned PDFs and images require Tesseract OCR)");
                return;
            }

            List<AiClient.ChunkPayload> payloads = new java.util.ArrayList<>();
            int index = 0;
            for (TextChunker.ChunkText splitChunk : splitChunks) {
                chunks.save(new DocumentChunk(document, index, splitChunk.page(), splitChunk.text()));
                payloads.add(new AiClient.ChunkPayload(index, splitChunk.page(), splitChunk.text()));
                index++;
            }
            savedChunks = splitChunks.size();
            aiClient.index(new AiClient.IndexRequest(
                    document.getId(),
                    document.getFilename(),
                    document.getContentType(),
                    payloads
            ));
            document.markIndexed(splitChunks.size());
        } catch (RestClientException exception) {
            document.markIndexedWithoutVectors(savedChunks);
        } catch (Exception exception) {
            document.markFailed("Unable to index document");
        }
    }

    private void validateSupportedDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document file is required");
        }

        try {
            DocumentTextExtractor.DocumentType.from(file.getOriginalFilename(), file.getContentType());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only PDF, DOCX, Markdown, TXT, and image (JPG/PNG/TIFF/BMP) uploads are supported"
            );
        }
    }

    private String normalizedContentType(MultipartFile file) {
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            return file.getContentType();
        }
        DocumentTextExtractor.DocumentType type =
                DocumentTextExtractor.DocumentType.from(file.getOriginalFilename(), file.getContentType());
        return switch (type) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case MARKDOWN -> "text/markdown";
            case TEXT -> "text/plain";
            case IMAGE -> imageContentType(file.getOriginalFilename());
        };
    }

    private String imageContentType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
            return "image/tiff";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "application/octet-stream";
    }

    private String sanitizeFilename(String filename) {
        String candidate = filename == null || filename.isBlank() ? "document.pdf" : filename;
        candidate = candidate.replace('\\', '/');
        candidate = Path.of(candidate).getFileName().toString();
        return candidate.replaceAll("[^a-zA-Z0-9._ -]", "_");
    }
}
