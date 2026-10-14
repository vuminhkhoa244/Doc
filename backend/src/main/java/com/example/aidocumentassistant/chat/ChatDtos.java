package com.example.aidocumentassistant.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String question,
            String tool,
            List<Long> documentIds,
            Long conversationId
    ) {
    }

    public record CreateConversationRequest(String title) {
    }

    public record ConversationResponse(Long id, String title, java.time.Instant createdAt) {
    }

    public record ConversationMessageResponse(Long id, String question, String answer, String toolUsed, String timestamp) {
    }

    public record ConversationDetailResponse(Long id, String title, java.time.Instant createdAt, List<ConversationMessageResponse> messages) {
    }

    public record SourceResponse(Long documentId, String document, int page) {
    }

    public record ChatResponse(String answer, List<SourceResponse> sources, String toolUsed) {
    }

    public record ChatHistoryResponse(Long id, Long conversationId, String question, String answer, String timestamp) {
    }
}
