package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.chat.ChatDtos;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class AgentService {
    private static final String AUTO_TOOL = "auto";
    private final List<Tool> tools;
    private final AgentPlanner planner;

    public AgentService(RagTool ragTool, WebSearchTool webSearchTool, SqlTool sqlTool, AgentPlanner planner) {
        this.tools = List.of(ragTool, webSearchTool, sqlTool);
        this.planner = planner;
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

    public AgentResult runStreaming(
            String question,
            List<Long> documentIds,
            Long userId,
            String requestedTool,
            List<AiClient.HistoryTurn> history,
            Consumer<String> tokenSink
    ) {
        Tool selectedTool = resolveTool(question, documentIds, requestedTool);
        ToolResult result = selectedTool.executeStreaming(
                new ToolContext(question, documentIds, userId, history),
                tokenSink
        );
        return new AgentResult(result.answer(), result.sources(), result.toolUsed());
    }

    private Tool resolveTool(String question, List<Long> documentIds, String requestedTool) {
        if (requestedTool != null && !requestedTool.isBlank() && !AUTO_TOOL.equalsIgnoreCase(requestedTool)) {
            Optional<Tool> explicit = tools.stream().filter(tool -> tool.id().equals(requestedTool)).findFirst();
            if (explicit.isPresent()) {
                return explicit.get();
            }
        }
        String routedTool = planner.route(question, documentIds);
        return tools.stream()
                .filter(tool -> tool.id().equals(routedTool))
                .findFirst()
                .orElse(tools.get(0));
    }

    public record AgentResult(String answer, List<ChatDtos.SourceResponse> sources, String toolUsed) {
    }
}
