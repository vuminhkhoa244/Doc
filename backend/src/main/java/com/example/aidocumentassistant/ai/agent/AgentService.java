package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.chat.ChatDtos;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AgentService {
    private final List<Tool> tools;

    public AgentService(RagTool ragTool, WebSearchTool webSearchTool, SqlTool sqlTool) {
        this.tools = List.of(ragTool, webSearchTool, sqlTool);
    }

    public AgentResult run(
            String question,
            List<Long> documentIds,
            Long userId,
            String requestedTool,
            List<AiClient.HistoryTurn> history
    ) {
        Tool selectedTool = resolveTool(question, documentIds, requestedTool);
        ToolResult result = selectedTool.execute(new ToolContext(question, documentIds, userId, history));
        return new AgentResult(result.answer(), result.sources(), result.toolUsed());
    }

    private Tool resolveTool(String question, List<Long> documentIds, String requestedTool) {
        if (requestedTool != null && !requestedTool.isBlank()) {
            Optional<Tool> explicit = tools.stream().filter(tool -> tool.id().equals(requestedTool)).findFirst();
            if (explicit.isPresent()) {
                return explicit.get();
            }
        }
        return selectTool(question, documentIds);
    }

    private Tool selectTool(String question, List<Long> documentIds) {
        String q = question.toLowerCase();
        if ((documentIds != null && !documentIds.isEmpty()) || q.contains("document") || q.contains("pdf") || q.contains("policy") || q.contains("handbook")) {
            return tools.stream().filter(t -> "rag".equals(t.id())).findFirst().orElse(tools.get(0));
        }
        if (q.contains("search") || q.contains("internet") || q.contains("web")) {
            return tools.stream().filter(t -> "web".equals(t.id())).findFirst().orElse(tools.get(1));
        }
        if (q.contains("sql") || q.contains("database") || q.contains("query")) {
            return tools.stream().filter(t -> "sql".equals(t.id())).findFirst().orElse(tools.get(2));
        }
        return tools.get(0);
    }

    public record AgentResult(String answer, List<ChatDtos.SourceResponse> sources, String toolUsed) {
    }
}
