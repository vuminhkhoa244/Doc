package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.ai.websearch.WebSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
public class WebSearchTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final String ID = "web";
    private static final String DESCRIPTION = "Search the internet";
    private static final int FALLBACK_RESULTS = 3;
    private static final int FALLBACK_MAX_CHARS = 1800;

    private final WebSearchClient webSearchClient;
    private final AiClient aiClient;

    public WebSearchTool(WebSearchClient webSearchClient, AiClient aiClient) {
        this.webSearchClient = webSearchClient;
        this.aiClient = aiClient;
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
        List<WebSearchClient.WebResult> results;
        try {
            results = webSearchClient.search(context.question());
        } catch (RestClientException exception) {
            log.warn("Web search request failed: {}", exception.getMessage());
            return new ToolResult(
                    "Web search is currently unavailable. Please try again later.",
                    List.of(),
                    ID
            );
        }

        if (results.isEmpty()) {
            return new ToolResult(
                    "I could not find any web results for that question. Try rephrasing it.",
                    List.of(),
                    ID
            );
        }

        List<AiClient.HistoryTurn> history = context.history() == null ? List.of() : context.history();
        try {
            AiClient.GenerateResponse response = aiClient.generate(
                    new AiClient.GenerateRequest(context.question(), buildContext(results), history)
            );
            if (response == null || response.answer() == null || response.answer().isBlank()) {
                return new ToolResult(fallbackAnswer(results), List.of(), ID);
            }
            return new ToolResult(response.answer().trim(), List.of(), ID);
        } catch (RestClientException exception) {
            log.warn("Answer generation from web results failed, falling back to snippets: {}", exception.getMessage());
            return new ToolResult(fallbackAnswer(results), List.of(), ID);
        }
    }

    private String buildContext(List<WebSearchClient.WebResult> results) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < results.size(); index++) {
            WebSearchClient.WebResult result = results.get(index);
            if (index > 0) {
                builder.append("\n\n");
            }
            builder.append("[").append(index + 1).append("] ").append(result.title())
                    .append("\nURL: ").append(result.url())
                    .append("\n").append(result.snippet());
        }
        return builder.toString();
    }

    private String fallbackAnswer(List<WebSearchClient.WebResult> results) {
        StringBuilder builder = new StringBuilder("Here's what I found on the web:");
        int limit = Math.min(FALLBACK_RESULTS, results.size());
        for (int index = 0; index < limit; index++) {
            WebSearchClient.WebResult result = results.get(index);
            builder.append("\n").append(index + 1).append(". ").append(result.title())
                    .append(" — ").append(result.snippet())
                    .append("\n   ").append(result.url());
        }
        return trim(builder.toString(), FALLBACK_MAX_CHARS);
    }

    private String trim(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }
}
