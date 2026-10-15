package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes a free-form question to one of the agent tools. Uses a small LLM call
 * to pick the tool, with a deterministic keyword heuristic as fallback when the
 * model is unreachable or returns something unusable.
 */
@Service
public class AgentPlanner {
    private static final Logger log = LoggerFactory.getLogger(AgentPlanner.class);
    private static final Set<String> TOOL_IDS = Set.of("rag", "web", "sql");
    private static final Pattern TOOL_JSON = Pattern.compile("\"tool\"\\s*:\\s*\"(rag|web|sql)\"", Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String SYSTEM_PROMPT = """
            You are an AI agent router. Reply with ONLY a compact JSON object and no other text.
            The JSON shape is {"tool":"rag|web|sql","reason":"one short sentence"}.
            """;

    private final AiClient aiClient;

    public AgentPlanner(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public String route(String question, List<Long> selectedDocumentIds) {
        boolean hasDocuments = selectedDocumentIds != null && !selectedDocumentIds.isEmpty();
        try {
            String tool = llmRoute(question, hasDocuments);
            if (tool != null) {
                return tool;
            }
        } catch (RestClientException exception) {
            log.warn("Planner LLM call failed, using keyword fallback: {}", exception.getMessage());
        }
        return fallbackRoute(question, hasDocuments);
    }

    private String llmRoute(String question, boolean hasDocuments) {
        String prompt = """
                Pick exactly one tool:
                - rag: answer from the user's uploaded documents (policies, handbooks, notes, files)
                - web: general knowledge or current events that need the internet
                - sql: questions about structured tabular data (employees, departments, headcounts, salaries)
                The user currently has documents selected for context: %s.
                Question: %s
                """.formatted(hasDocuments ? "yes" : "no", question);

        AiClient.CompleteResponse response = aiClient.complete(
                new AiClient.CompleteRequest(SYSTEM_PROMPT, prompt, 120)
        );
        if (response == null || response.answer() == null || response.answer().isBlank()) {
            return null;
        }
        String answer = response.answer();

        Matcher matcher = TOOL_JSON.matcher(answer);
        if (matcher.find()) {
            String tool = matcher.group(1).toLowerCase(Locale.ROOT);
            if (TOOL_IDS.contains(tool)) {
                return tool;
            }
        }
        try {
            JsonNode node = MAPPER.readTree(answer);
            String tool = node.path("tool").asText("").toLowerCase(Locale.ROOT);
            if (TOOL_IDS.contains(tool)) {
                return tool;
            }
        } catch (Exception ignored) {
            // fall through to keyword routing
        }
        log.warn("Planner returned unusable output, using keyword fallback: {}", answer);
        return null;
    }

    private String fallbackRoute(String question, boolean hasDocuments) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (q.contains("sql") || q.contains("database") || q.contains("headcount")
                || q.contains("salary") || q.contains("table")) {
            return "sql";
        }
        if (q.contains("document") || q.contains("pdf") || q.contains("policy")
                || q.contains("handbook") || q.contains("upload") || q.contains("file")) {
            return "rag";
        }
        if (q.contains("search") || q.contains("internet") || q.contains("web") || q.contains("news")
                || q.contains("online") || q.contains("today")) {
            return "web";
        }
        if (q.contains("employee") || q.contains("department")) {
            return "sql";
        }
        if (hasDocuments) {
            return "rag";
        }
        return "rag";
    }
}
