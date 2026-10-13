package com.example.aidocumentassistant.ai;

import com.example.aidocumentassistant.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class AiClient {
    private final RestClient restClient;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiClient(AppProperties properties) {
        this.baseUrl = properties.ai().baseUrl();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .baseUrl(properties.ai().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public void index(IndexRequest request) {
        restClient.post()
                .uri("/index")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteIndex(long documentId) {
        restClient.delete()
                .uri("/index/{documentId}", documentId)
                .retrieve()
                .toBodilessEntity();
    }

    public QueryResponse query(QueryRequest request) {
        return restClient.post()
                .uri("/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(QueryResponse.class);
    }

    public SearchResponse search(SearchRequest request) {
        return restClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SearchResponse.class);
    }

    public GenerateResponse generate(GenerateRequest request) {
        return restClient.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GenerateResponse.class);
    }

    public StreamResult queryStream(QueryRequest request, Consumer<String> tokenConsumer) {
        return stream("/query_stream", request, tokenConsumer);
    }

    public StreamResult generateStream(GenerateRequest request, Consumer<String> tokenConsumer) {
        return stream("/generate_stream", request, tokenConsumer);
    }

    private StreamResult stream(String path, Object body, Consumer<String> tokenConsumer) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(4))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<InputStream> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String detail = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new RestClientException("Streaming request failed with status " + response.statusCode() + ": " + detail);
            }
            return consumeStream(response.body(), tokenConsumer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Streaming request was interrupted", exception);
        } catch (IOException exception) {
            throw new RestClientException("Streaming request failed", exception);
        }
    }

    private StreamResult consumeStream(InputStream inputStream, Consumer<String> tokenConsumer) throws IOException {
        String finalAnswer = null;
        List<SourcePayload> sources = List.of();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                JsonNode payload = objectMapper.readTree(line.substring("data:".length()).trim());
                String type = payload.path("type").asText("");
                switch (type) {
                    case "token" -> tokenConsumer.accept(payload.path("content").asText(""));
                    case "done" -> {
                        finalAnswer = payload.path("answer").asText(null);
                        sources = readSources(payload.path("sources"));
                    }
                    case "error" -> throw new RestClientException(
                            payload.has("detail") ? payload.path("detail").asText("Streaming failed") : "Streaming failed");
                    default -> { }
                }
            }
        }
        if (finalAnswer == null) {
            throw new RestClientException("Stream ended without a final answer");
        }
        return new StreamResult(finalAnswer, sources);
    }

    private List<SourcePayload> readSources(JsonNode sourcesNode) {
        if (sourcesNode == null || !sourcesNode.isArray()) {
            return List.of();
        }
        List<SourcePayload> sources = new ArrayList<>();
        for (JsonNode node : sourcesNode) {
            sources.add(new SourcePayload(
                    node.path("documentId").asLong(),
                    node.path("filename").asText(),
                    node.path("page").asInt()
            ));
        }
        return sources;
    }

    public record ChunkPayload(int chunkIndex, int page, String text) {
    }

    public record IndexRequest(long documentId, String filename, String contentType, List<ChunkPayload> chunks) {
    }

    public record HistoryTurn(String question, String answer) {
    }

    public record QueryRequest(String question, List<Long> documentIds, List<HistoryTurn> history) {
    }

    public record GenerateRequest(String question, String context, List<HistoryTurn> history) {
    }

    public record GenerateResponse(String answer) {
    }

    public record SourcePayload(long documentId, String filename, int page) {
    }

    public record QueryResponse(String answer, List<SourcePayload> sources) {
    }

    public record SearchRequest(String query, List<Long> documentIds, int limit) {
    }

    public record SearchHit(long documentId, String filename, int page, int chunkIndex, String text, double score) {
    }

    public record SearchResponse(List<SearchHit> results) {
    }

    public record StreamResult(String answer, List<SourcePayload> sources) {
    }
}
