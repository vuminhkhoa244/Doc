package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.chat.ChatDtos;
import com.example.aidocumentassistant.document.DocumentChunk;
import com.example.aidocumentassistant.document.DocumentRepository;
import com.example.aidocumentassistant.document.SemanticSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class RagTool implements Tool {
    private static final String ID = "rag";
    private static final String DESCRIPTION = "Answer questions from indexed documents using retrieval-augmented generation";
    private final SemanticSearchService semanticSearchService;
    private final AiClient aiClient;
    private final DocumentRepository documents;

    public RagTool(SemanticSearchService semanticSearchService, AiClient aiClient, DocumentRepository documents) {
        this.semanticSearchService = semanticSearchService;
        this.aiClient = aiClient;
        this.documents = documents;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public boolean supports(String question) {
        return true;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        List<String> questionTerms = semanticSearchService.tokenize(context.question());
        if (questionTerms.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must contain searchable text");
        }

        try {
            return aiAnswer(context);
        } catch (RestClientException exception) {
            return extractiveAnswer(context, questionTerms);
        }
    }

    @Override
    public ToolResult executeStreaming(ToolContext context, Consumer<String> tokenSink) {
        List<String> questionTerms = semanticSearchService.tokenize(context.question());
        if (questionTerms.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must contain searchable text");
        }

        try {
            return aiAnswerStreaming(context, tokenSink);
        } catch (RestClientException exception) {
            ToolResult fallback = extractiveAnswer(context, questionTerms);
            tokenSink.accept(fallback.answer());
            return fallback;
        }
    }

    private List<Long> ownerDocumentIds(ToolContext context) {
        return documents.findByOwnerIdOrderByUploadTimeDesc(context.userId())
                .stream()
                .map(document -> document.getId())
                .toList();
    }

    private List<Long> focusDocumentIds(ToolContext context, List<Long> ownerIds) {
        if (context.documentIds() == null || context.documentIds().isEmpty()) {
            return null;
        }
        List<Long> focus = context.documentIds().stream()
                .filter(ownerIds::contains)
                .toList();
        return focus.isEmpty() ? null : focus;
    }

    private ToolResult aiAnswer(ToolContext context) {
        List<Long> ownerIds = ownerDocumentIds(context);
        AiClient.QueryResponse response = aiClient.query(new AiClient.QueryRequest(
                context.question(),
                ownerIds,
                focusDocumentIds(context, ownerIds),
                context.history() == null ? List.of() : context.history()
        ));

        List<ChatDtos.SourceResponse> sources = response.sources() == null
                ? List.of()
                : response.sources().stream()
                        .map(source -> new ChatDtos.SourceResponse(source.documentId(), source.filename(), source.page()))
                        .toList();
        return new ToolResult(response.answer(), sources, ID);
    }

    private ToolResult aiAnswerStreaming(ToolContext context, Consumer<String> tokenSink) {
        List<Long> ownerIds = ownerDocumentIds(context);
        AiClient.StreamResult response = aiClient.queryStream(new AiClient.QueryRequest(
                context.question(),
                ownerIds,
                focusDocumentIds(context, ownerIds),
                context.history() == null ? List.of() : context.history()
        ), tokenSink);

        List<ChatDtos.SourceResponse> sources = response.sources() == null
                ? List.of()
                : response.sources().stream()
                        .map(source -> new ChatDtos.SourceResponse(source.documentId(), source.filename(), source.page()))
                        .toList();
        return new ToolResult(response.answer(), sources, ID);
    }

    private ToolResult extractiveAnswer(ToolContext context, List<String> questionTerms) {
        SemanticSearchService.SearchFilters filters = new SemanticSearchService.SearchFilters(
                context.question(),
                null,
                null,
                null,
                null,
                null,
                5
        );

        List<SemanticSearchService.RankedChunk> rankedChunks = semanticSearchService.search(context.userId(), filters);

        if (rankedChunks.isEmpty()) {
            return new ToolResult(
                    "I could not find a matching answer in your indexed documents. Upload and index a document or ask using related terms.",
                    List.of(),
                    ID
            );
        }

        List<String> snippets = new ArrayList<>();
        for (SemanticSearchService.RankedChunk rankedChunk : rankedChunks) {
            for (String sentence : bestSentences(rankedChunk.chunk().getText(), questionTerms)) {
                if (snippets.stream().noneMatch(existing -> existing.equalsIgnoreCase(sentence))) {
                    snippets.add(sentence);
                }
                if (snippets.size() >= 4) {
                    break;
                }
            }
            if (snippets.size() >= 4) {
                break;
            }
        }

        String answer = snippets.isEmpty()
                ? "I found relevant document text, but it does not contain a concise sentence-level answer. Relevant context: "
                + rankedChunks.get(0).chunk().getText()
                : String.join(" ", snippets);

        return new ToolResult(trim(answer, 1800), sources(rankedChunks), "rag-fallback");
    }

    private List<String> bestSentences(String text, List<String> questionTerms) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        return Arrays.stream(sentences)
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .sorted(Comparator.comparingDouble((String sentence) -> sentenceScore(sentence, questionTerms)).reversed())
                .limit(2)
                .collect(Collectors.toList());
    }

    private double sentenceScore(String sentence, List<String> questionTerms) {
        Set<String> sentenceTerms = new LinkedHashSet<>(semanticSearchService.tokenize(sentence));
        double score = 0;
        for (String term : questionTerms) {
            if (sentenceTerms.contains(term)) {
                score += 1;
            }
        }
        return score;
    }

    private List<ChatDtos.SourceResponse> sources(List<SemanticSearchService.RankedChunk> rankedChunks) {
        Map<String, ChatDtos.SourceResponse> unique = new LinkedHashMap<>();
        for (SemanticSearchService.RankedChunk rankedChunk : rankedChunks) {
            DocumentChunk chunk = rankedChunk.chunk();
            String key = chunk.getDocument().getId() + ":" + chunk.getPage();
            unique.putIfAbsent(key, new ChatDtos.SourceResponse(
                    chunk.getDocument().getId(), chunk.getDocument().getFilename(), chunk.getPage()));
            if (unique.size() >= 5) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private String trim(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }
}
