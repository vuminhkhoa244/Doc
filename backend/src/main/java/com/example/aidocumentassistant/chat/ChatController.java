package com.example.aidocumentassistant.chat;

import com.example.aidocumentassistant.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatDtos.ChatResponse ask(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChatDtos.ChatRequest request
    ) {
        return chatService.ask(principal, request.question(), request.tool(), request.documentIds(), request.conversationId());
    }

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChatDtos.ChatRequest request
    ) {
        return chatService.askStream(principal, request.question(), request.tool(), request.documentIds(), request.conversationId());
    }

    @GetMapping("/history")
    public List<ChatDtos.ChatHistoryResponse> history(@AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.getConversationHistory(principal);
    }

    @GetMapping("/conversations")
    public List<ChatDtos.ConversationResponse> conversations(@AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.getConversations(principal);
    }

    @GetMapping("/conversations/{conversationId}")
    public ChatDtos.ConversationDetailResponse conversation(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long conversationId) {
        return chatService.getConversation(principal, conversationId);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDtos.ConversationResponse createConversation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChatDtos.CreateConversationRequest request
    ) {
        return chatService.createConversation(principal, request.title());
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long conversationId) {
        chatService.deleteConversation(principal, conversationId);
    }
}
