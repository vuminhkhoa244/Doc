package com.example.aidocumentassistant.ai;

import com.example.aidocumentassistant.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class AiClient {
    private final RestClient restClient;

    public AiClient(AppProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
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

    public record ChunkPayload(int chunkIndex, int page, String text) {
    }

    public record IndexRequest(long documentId, String filename, String contentType, List<ChunkPayload> chunks) {
    }

    public record HistoryTurn(String question, String answer) {
    }

    public record QueryRequest(String question, List<Long> documentIds, List<HistoryTurn> history) {
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
}
