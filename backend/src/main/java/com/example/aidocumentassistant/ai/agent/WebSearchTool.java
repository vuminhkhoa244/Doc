package com.example.aidocumentassistant.ai.agent;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebSearchTool implements Tool {
    private static final String ID = "web";
    private static final String DESCRIPTION = "Search the internet";

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
        return new ToolResult(
                "Web search is not yet implemented. Add an external search API client to enable this tool.",
                List.of(),
                ID
        );
    }
}
