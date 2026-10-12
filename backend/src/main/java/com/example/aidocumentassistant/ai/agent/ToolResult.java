package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.chat.ChatDtos;

import java.util.List;

public record ToolResult(String answer, List<ChatDtos.SourceResponse> sources, String toolUsed) {
}
