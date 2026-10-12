package com.example.aidocumentassistant.document;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SemanticSearchService {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from",
            "how", "i", "in", "is", "it", "of", "on", "or", "that", "the", "this", "to", "what",
            "when", "where", "which", "who", "why", "with", "you", "your"
    );
    private static final Map<String, List<String>> SEMANTIC_EXPANSIONS = Map.ofEntries(
            Map.entry("vacation", List.of("annual", "leave", "pto", "holiday")),
            Map.entry("holiday", List.of("annual", "leave", "vacation", "pto")),
            Map.entry("pto", List.of("annual", "leave", "vacation", "holiday")),
            Map.entry("sick", List.of("illness", "medical", "leave")),
            Map.entry("salary", List.of("compensation", "pay", "wage")),
            Map.entry("pay", List.of("salary", "compensation", "wage")),
            Map.entry("termination", List.of("dismissal", "offboarding", "resignation")),
            Map.entry("security", List.of("auth", "authentication", "authorization", "jwt")),
            Map.entry("login", List.of("auth", "authentication", "signin", "jwt")),
            Map.entry("docker", List.of("container", "compose", "image")),
            Map.entry("database", List.of("postgresql", "postgres", "sql", "db"))
    );

    private final DocumentChunkRepository chunks;

    public SemanticSearchService(DocumentChunkRepository chunks) {
        this.chunks = chunks;
    }

    public List<RankedChunk> search(Long ownerId, SearchFilters filters) {
        List<String> queryTerms = expand(tokenize(filters.query()));
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        int limit = filters.limit() == null ? 5 : Math.max(1, Math.min(filters.limit(), 20));
        Map<String, Double> queryVector = vector(queryTerms);

        return chunks.findIndexedChunksForOwner(ownerId)
                .stream()
                .filter(chunk -> matchesFilters(chunk.getDocument(), filters))
                .map(chunk -> rank(chunk, queryVector, queryTerms))
                .filter(ranked -> ranked.score() > 0)
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    public List<String> tokenize(String text) {
        return TOKEN_SPLIT.splitAsStream(text.toLowerCase(Locale.ROOT))
                .map(String::trim)
                .filter(term -> term.length() > 1)
                .filter(term -> !STOP_WORDS.contains(term))
                .toList();
    }

    private List<String> expand(List<String> terms) {
        List<String> expanded = new ArrayList<>(terms);
        for (String term : terms) {
            expanded.addAll(SEMANTIC_EXPANSIONS.getOrDefault(term, List.of()));
        }
        return expanded;
    }

    private RankedChunk rank(DocumentChunk chunk, Map<String, Double> queryVector, List<String> queryTerms) {
        Map<String, Double> chunkVector = vector(tokenize(chunk.getText()));
        double score = cosine(queryVector, chunkVector);
        for (String term : queryTerms) {
            for (String chunkTerm : chunkVector.keySet()) {
                if ((chunkTerm.startsWith(term) || term.startsWith(chunkTerm)) && Math.min(chunkTerm.length(), term.length()) >= 5) {
                    score += 0.04;
                }
            }
        }
        return new RankedChunk(chunk, score);
    }

    private Map<String, Double> vector(List<String> terms) {
        Map<String, Long> frequencies = terms.stream()
                .collect(Collectors.groupingBy(term -> term, LinkedHashMap::new, Collectors.counting()));
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Long> entry : frequencies.entrySet()) {
            vector.put(entry.getKey(), 1 + Math.log(entry.getValue()));
        }
        return vector;
    }

    private double cosine(Map<String, Double> queryVector, Map<String, Double> chunkVector) {
        double dotProduct = 0;
        for (Map.Entry<String, Double> queryTerm : queryVector.entrySet()) {
            dotProduct += queryTerm.getValue() * chunkVector.getOrDefault(queryTerm.getKey(), 0.0);
        }
        double queryMagnitude = magnitude(queryVector);
        double chunkMagnitude = magnitude(chunkVector);
        if (queryMagnitude == 0 || chunkMagnitude == 0) {
            return 0;
        }
        return dotProduct / (queryMagnitude * chunkMagnitude);
    }

    private double magnitude(Map<String, Double> vector) {
        return Math.sqrt(vector.values().stream().mapToDouble(value -> value * value).sum());
    }

    private boolean matchesFilters(Document document, SearchFilters filters) {
        if (filters.documentId() != null && !filters.documentId().equals(document.getId())) {
            return false;
        }
        if (filters.documentIds() != null && !filters.documentIds().isEmpty() && !filters.documentIds().contains(document.getId())) {
            return false;
        }
        if (StringUtils.hasText(filters.contentType())
                && !document.getContentType().toLowerCase(Locale.ROOT).contains(filters.contentType().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (filters.uploadedAfter() != null && document.getUploadTime().isBefore(filters.uploadedAfter())) {
            return false;
        }
        if (filters.uploadedBefore() != null && document.getUploadTime().isAfter(filters.uploadedBefore())) {
            return false;
        }
        return true;
    }

    public record SearchFilters(
            String query,
            Long documentId,
            List<Long> documentIds,
            String contentType,
            Instant uploadedAfter,
            Instant uploadedBefore,
            Integer limit
    ) {
        public static SearchFilters from(DocumentSearchDtos.SearchRequest request) {
            return new SearchFilters(
                    request.query(),
                    request.documentId(),
                    null,
                    request.contentType(),
                    request.uploadedAfter(),
                    request.uploadedBefore(),
                    request.limit()
            );
        }
    }

    public record RankedChunk(DocumentChunk chunk, double score) {
    }
}
