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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final ExtractedTableRepository extractedTables;
    private final ImageAssetRepository imageAssets;
    private final UserRepository users;
    private final AppProperties properties;
    private final DocumentTextExtractor documentTextExtractor;
    private final TextChunker textChunker;
    private final TableExtractor tableExtractor;
    private final ImageAssetExtractor imageAssetExtractor;
    private final AiClient aiClient;

    public DocumentService(
            DocumentRepository documents,
            DocumentChunkRepository chunks,
            ExtractedTableRepository extractedTables,
            ImageAssetRepository imageAssets,
            UserRepository users,
            AppProperties properties,
            DocumentTextExtractor documentTextExtractor,
            TextChunker textChunker,
            TableExtractor tableExtractor,
            ImageAssetExtractor imageAssetExtractor,
            AiClient aiClient
    ) {
        this.documents = documents;
        this.chunks = chunks;
        this.extractedTables = extractedTables;
        this.imageAssets = imageAssets;
        this.users = users;
        this.properties = properties;
        this.documentTextExtractor = documentTextExtractor;
        this.textChunker = textChunker;
        this.tableExtractor = tableExtractor;
        this.imageAssetExtractor = imageAssetExtractor;
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
        deleteExtractedArtifacts(document);
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
        deleteExtractedArtifacts(document);
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

    @Transactional(readOnly = true)
    public List<DocumentDtos.TableResponse> tables(AuthenticatedUser principal, Long id) {
        Document document = documents.findByIdAndOwnerId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return extractedTables.findByDocumentIdOrderByPageAsc(document.getId())
                .stream()
                .map(table -> new DocumentDtos.TableResponse(
                        table.getId(), table.getPage(), table.getRowCount(), table.getMarkdown()))
                .toList();
    }

    public record DocumentFile(String filename, String contentType, Path path) {
    }

    private void deleteExtractedArtifacts(Document document) {
        for (ImageAsset asset : imageAssets.findByDocumentIdOrderByPageAsc(document.getId())) {
            try {
                Files.deleteIfExists(Path.of(asset.getStoragePath()));
            } catch (IOException ignored) {
            }
        }
        imageAssets.deleteAllByDocument(document);
        extractedTables.deleteAllByDocument(document);
    }

    private void index(Document document, Path storagePath) {
        int savedChunks = 0;
        try {
            List<ExtractedText> extracted = documentTextExtractor.extract(
                    storagePath, document.getFilename(), document.getContentType());
            List<TextChunker.ChunkText> textChunks = textChunker.split(extracted);

            List<TextChunker.ChunkText> allChunks = new java.util.ArrayList<>(textChunks);
            for (TableExtractor.TableData table : tableExtractor.extract(
                    storagePath, document.getFilename(), document.getContentType())) {
                extractedTables.save(new ExtractedTable(document, table.page(), table.markdown(), table.rowCount()));
                allChunks.addAll(textChunker.split(List.of(new ExtractedText(
                        table.page(), "Table (page " + table.page() + "):\n" + table.markdown()))));
            }

            saveImageAssets(document, storagePath, extracted);

            if (allChunks.isEmpty()) {
                document.markFailed("No extractable text found in document (scanned PDFs and images require Tesseract OCR)");
                return;
            }

            List<AiClient.ChunkPayload> payloads = new java.util.ArrayList<>();
            int index = 0;
            for (TextChunker.ChunkText splitChunk : allChunks) {
                chunks.save(new DocumentChunk(document, index, splitChunk.page(), splitChunk.text()));
                payloads.add(new AiClient.ChunkPayload(index, splitChunk.page(), splitChunk.text()));
                index++;
            }
            savedChunks = allChunks.size();
            aiClient.index(new AiClient.IndexRequest(
                    document.getId(),
                    document.getFilename(),
                    document.getContentType(),
                    payloads
            ));
            document.markIndexed(allChunks.size());
        } catch (RestClientException exception) {
            document.markIndexedWithoutVectors(savedChunks);
        } catch (Exception exception) {
            log.warn("Indexing failed for {}: {}", document.getFilename(), exception.getMessage());
            document.markFailed("Unable to index document");
        }
    }

    private void saveImageAssets(Document document, Path storagePath, List<ExtractedText> extracted) {
        try {
            Path assetsDir = properties.storage().uploadDir().resolve("assets");
            List<ImageAsset> assets;
            DocumentTextExtractor.DocumentType type =
                    DocumentTextExtractor.DocumentType.from(document.getFilename(), document.getContentType());
            if (type == DocumentTextExtractor.DocumentType.PDF) {
                assets = imageAssetExtractor.extractFromPdf(document, storagePath, assetsDir);
            } else if (type == DocumentTextExtractor.DocumentType.IMAGE) {
                String ocrText = extracted.stream()
                        .filter(page -> page.page() == 1)
                        .map(ExtractedText::text)
                        .findFirst()
                        .orElse("");
                assets = imageAssetExtractor.fromStandaloneUpload(document, storagePath, assetsDir, ocrText);
            } else {
                assets = List.of();
            }
            for (ImageAsset asset : assets) {
                imageAssets.save(asset);
            }
        } catch (Exception exception) {
            log.warn("Image asset extraction failed for {}: {}", document.getFilename(), exception.getMessage());
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
