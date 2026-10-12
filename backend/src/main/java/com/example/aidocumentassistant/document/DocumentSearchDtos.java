package com.example.aidocumentassistant.document;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class DocumentSearchDtos {
    private DocumentSearchDtos() {
    }

    public record SearchRequest(
            @NotBlank @Size(max = 1000) String query,
            Long documentId,
            String contentType,
            Instant uploadedAfter,
            Instant uploadedBefore,
            @Min(1) @Max(20) Integer limit
    ) {
    }

    public record SearchResult(
            Long documentId,
            String document,
            String contentType,
            int page,
            int chunkIndex,
            double score,
            String snippet
    ) {
        public static SearchResult from(SemanticSearchService.RankedChunk rankedChunk) {
            DocumentChunk chunk = rankedChunk.chunk();
            Document document = chunk.getDocument();
            return new SearchResult(
                    document.getId(),
                    document.getFilename(),
                    document.getContentType(),
                    chunk.getPage(),
                    chunk.getChunkIndex(),
                    rankedChunk.score(),
                    trim(chunk.getText(), 420)
            );
        }

        private static String trim(String value, int maxLength) {
            if (value.length() <= maxLength) {
                return value;
            }
            return value.substring(0, maxLength - 3).trim() + "...";
        }
    }

    public record SearchResponse(List<SearchResult> results) {
    }
}
