package com.example.aidocumentassistant.ai.agent;

import java.util.function.Consumer;

public interface Tool {
    String id();
    String description();
    boolean supports(String question);
    ToolResult execute(ToolContext context);

    /**
     * Streaming variant of {@link #execute}. Default implementation runs the
     * non-streaming path and pushes the whole answer to the sink at once.
     */
    default ToolResult executeStreaming(ToolContext context, Consumer<String> tokenSink) {
        ToolResult result = execute(context);
        if (result.answer() != null && !result.answer().isBlank()) {
            tokenSink.accept(result.answer());
        }
        return result;
    }
}
