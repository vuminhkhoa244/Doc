package com.example.aidocumentassistant.ai.agent;

public interface Tool {
    String id();
    String description();
    boolean supports(String question);
    ToolResult execute(ToolContext context);
}
