package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.chat.ChatDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
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
        Optional<Tool> routed = tools.stream()
                .filter(tool -> tool.id().equals(routedTool))
                .findFirst();
        // The planner routes on question text alone and can pick a tool that is
        // not configured (e.g. web search without a backend). Redirect to the
        // first tool that can actually answer rather than dead-ending.
        if (routed.isPresent() && routed.get().supports(question)) {
            return routed.get();
        }
        if (routed.isPresent()) {
            log.info("Planner routed to '{}' but that tool is unavailable; falling back", routedTool);
        }
        return tools.stream()
                .filter(tool -> tool.supports(question))
                .findFirst()
                .orElse(tools.get(0));
    }

    public record AgentResult(String answer, List<ChatDtos.SourceResponse> sources, String toolUsed) {
    }
}
