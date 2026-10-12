package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {
    private final AiClient aiClient;
    private final DocumentRepository documents;
    private final SemanticSearchService semanticSearchService;

    public VectorSearchService(
            AiClient aiClient,
            DocumentRepository documents,
            SemanticSearchService semanticSearchService
    ) {
        this.aiClient = aiClient;
        this.documents = documents;
        this.semanticSearchService = semanticSearchService;
    }

    public DocumentSearchDtos.SearchResponse search(AuthenticatedUser principal, DocumentSearchDtos.SearchRequest request) {
        List<Long> ownerDocumentIds = documents.findByOwnerIdOrderByUploadTimeDesc(principal.id())
                .stream()
                .map(Document::getId)
                .toList();
        List<Long> scopedIds = request.documentId() != null
                ? ownerDocumentIds.stream().filter(id -> id.equals(request.documentId())).toList()
                : ownerDocumentIds;

        if (scopedIds.isEmpty()) {
            return new DocumentSearchDtos.SearchResponse(List.of());
        }

        int limit = request.limit() == null ? 8 : request.limit();
        try {
            return aiSearch(request, scopedIds, limit);
        } catch (RestClientException exception) {
            return lexicalSearch(principal, request);
        }
    }

    private DocumentSearchDtos.SearchResponse aiSearch(DocumentSearchDtos.SearchRequest request, List<Long> scopedIds, int limit) {
        AiClient.SearchResponse response = aiClient.search(new AiClient.SearchRequest(
                request.query(),
                scopedIds,
                limit
        ));

        Map<Long, Document> documentById = documents.findAllById(scopedIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        List<DocumentSearchDtos.SearchResult> results = response.results().stream()
                .filter(hit -> documentById.containsKey(hit.documentId()))
                .filter(hit -> matchesContentType(documentById.get(hit.documentId()), request.contentType()))
                .filter(hit -> matchesUploadWindow(documentById.get(hit.documentId()), request))
                .map(hit -> new DocumentSearchDtos.SearchResult(
                        hit.documentId(),
                        hit.filename(),
                        documentById.get(hit.documentId()).getContentType(),
                        hit.page(),
                        hit.chunkIndex(),
                        hit.score(),
                        trim(hit.text(), 420)
                ))
                .toList();
        return new DocumentSearchDtos.SearchResponse(results);
    }

    private DocumentSearchDtos.SearchResponse lexicalSearch(AuthenticatedUser principal, DocumentSearchDtos.SearchRequest request) {
        return new DocumentSearchDtos.SearchResponse(
                semanticSearchService.search(principal.id(), SemanticSearchService.SearchFilters.from(request))
                        .stream()
                        .map(DocumentSearchDtos.SearchResult::from)
                        .toList()
        );
    }

    private boolean matchesContentType(Document document, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        return document.getContentType().toLowerCase(java.util.Locale.ROOT)
                .contains(contentType.toLowerCase(java.util.Locale.ROOT));
    }

    private boolean matchesUploadWindow(Document document, DocumentSearchDtos.SearchRequest request) {
        if (request.uploadedAfter() != null && document.getUploadTime().isBefore(request.uploadedAfter())) {
            return false;
        }
        if (request.uploadedBefore() != null && document.getUploadTime().isAfter(request.uploadedBefore())) {
            return false;
        }
        return true;
    }

    private String trim(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }
}
