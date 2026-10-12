package com.example.aidocumentassistant.chat;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.ai.agent.AgentService;
import com.example.aidocumentassistant.auth.AppUser;
import com.example.aidocumentassistant.auth.UserRepository;
import com.example.aidocumentassistant.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatService {
    private static final int HISTORY_TURNS = 4;

    private final UserRepository users;
    private final ChatHistoryRepository history;
    private final ConversationRepository conversations;
    private final AgentService agentService;

    public ChatService(
            UserRepository users,
            ChatHistoryRepository history,
            ConversationRepository conversations,
            AgentService agentService
    ) {
        this.users = users;
        this.history = history;
        this.conversations = conversations;
        this.agentService = agentService;
    }

    @Transactional
    public ChatDtos.ChatResponse ask(AuthenticatedUser principal, String question, String tool, List<Long> documentIds, Long conversationId) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Conversation conversation = null;
        if (conversationId != null) {
            conversation = conversations.findById(conversationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
            if (!conversation.getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation does not belong to user");
            }
        }

        List<AiClient.HistoryTurn> historyTurns = conversation == null
                ? List.of()
                : conversation.getMessages().stream()
                        .sorted(Comparator.comparing(ChatHistory::getTimestamp))
                        .map(message -> new AiClient.HistoryTurn(message.getQuestion(), message.getAnswer()))
                        .toList();
        if (historyTurns.size() > HISTORY_TURNS) {
            historyTurns = historyTurns.subList(historyTurns.size() - HISTORY_TURNS, historyTurns.size());
        }

        AgentService.AgentResult result = agentService.run(question, documentIds, principal.id(), tool, historyTurns);

        String toolUsed = result.toolUsed() != null ? result.toolUsed() : "rag";
        ChatHistory chatHistory = new ChatHistory(user, question, result.answer(), toolUsed);
        if (conversation != null) {
            chatHistory.setConversation(conversation);
            if (conversation.getMessages().isEmpty()) {
                conversation.setTitle(question.length() > 60 ? question.substring(0, 60) : question);
            }
        }
        history.save(chatHistory);

        return new ChatDtos.ChatResponse(result.answer(), result.sources(), toolUsed);
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.ChatHistoryResponse> getConversationHistory(AuthenticatedUser principal) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return history.findByUserOrderByTimestampDesc(user)
                .stream()
                .map(chat -> new ChatDtos.ChatHistoryResponse(
                        chat.getId(),
                        chat.getConversation() != null ? chat.getConversation().getId() : null,
                        chat.getQuestion(),
                        chat.getAnswer(),
                        chat.getTimestamp().toString()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.ConversationResponse> getConversations(AuthenticatedUser principal) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return conversations.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(c -> new ChatDtos.ConversationResponse(
                        c.getId(),
                        c.getTitle(),
                        c.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatDtos.ConversationDetailResponse getConversation(AuthenticatedUser principal, Long conversationId) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation does not belong to user");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        List<ChatDtos.ConversationMessageResponse> messages = conversation.getMessages()
                .stream()
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .map(chat -> new ChatDtos.ConversationMessageResponse(
                        chat.getId(),
                        chat.getQuestion(),
                        chat.getAnswer(),
                        chat.getToolUsed(),
                        chat.getTimestamp().toString()
                ))
                .toList();

        return new ChatDtos.ConversationDetailResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                messages
        );
    }

    @Transactional
    public ChatDtos.ConversationResponse createConversation(AuthenticatedUser principal, String title) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Conversation conversation = new Conversation(user, title == null || title.isBlank() ? "New conversation" : title);
        return toResponse(conversations.save(conversation));
    }

    @Transactional
    public void deleteConversation(AuthenticatedUser principal, Long conversationId) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation does not belong to user");
        }
        conversations.delete(conversation);
    }

    private ChatDtos.ConversationResponse toResponse(Conversation conversation) {
        return new ChatDtos.ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt()
        );
    }
}
