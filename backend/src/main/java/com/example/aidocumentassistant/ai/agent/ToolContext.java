package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.chat.ChatDtos;

import java.util.List;

public record ToolContext(
        String question,
        List<Long> documentIds,
        Long userId,
        List<AiClient.HistoryTurn> history
) {
}
