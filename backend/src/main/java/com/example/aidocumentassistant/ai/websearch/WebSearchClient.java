package com.example.aidocumentassistant.ai.websearch;

import com.example.aidocumentassistant.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebSearchClient {
    private final RestClient restClient;
    private final int maxResults;

    public WebSearchClient(AppProperties properties) {
        AppProperties.WebSearch config = properties.webSearch();
        if (config == null || config.baseUrl() == null || config.baseUrl().isBlank()) {
            this.restClient = null;
            this.maxResults = 0;
            return;
        }
        this.maxResults = config.maxResults();
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "ai-document-assistant/1.0")
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Whether a search backend is configured. When false (blank
     * {@code app.web-search.base-url}), the tool should not claim it can
     * answer questions, so auto-routing can fall back to RAG instead of
     * dead-ending on "no web results".
     */
    public boolean isEnabled() {
        return restClient != null;
    }

    public List<WebResult> search(String query) {
        if (restClient == null) {
            return List.of();
        }
        SearxResponse raw = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(SearxResponse.class);
        return normalize(raw, maxResults);
    }

    static List<WebResult> normalize(SearxResponse raw, int maxResults) {
        if (raw == null || raw.results() == null) {
            return List.of();
        }
        Map<String, WebResult> deduped = new LinkedHashMap<>();
        for (SearxResult result : raw.results()) {
            if (result == null || isBlank(result.url()) || isBlank(result.title()) || isBlank(result.content())) {
                continue;
            }
            deduped.putIfAbsent(
                    result.url().trim(),
                    new WebResult(result.title().trim(), result.url().trim(), result.content().trim())
            );
            if (deduped.size() >= maxResults) {
                break;
            }
        }
        return List.copyOf(deduped.values());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record WebResult(String title, String url, String snippet) {
    }

    record SearxResponse(List<SearxResult> results) {
    }

    record SearxResult(String title, String url, String content) {
    }
}
