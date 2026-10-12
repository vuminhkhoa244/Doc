package com.example.aidocumentassistant.ai.agent;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SqlTool implements Tool {
    private static final String ID = "sql";
    private static final String DESCRIPTION = "Query structured data";

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
                "SQL tool is not yet implemented. Add a database query client to enable this tool.",
                List.of(),
                ID
        );
    }
}
