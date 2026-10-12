package com.example.aidocumentassistant.chat;

import com.example.aidocumentassistant.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_history")
public class ChatHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(nullable = false, length = 8000)
    private String answer;

    @Column(nullable = false, length = 50)
    private String toolUsed = "rag";

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    protected ChatHistory() {
    }

    public ChatHistory(AppUser user, String question, String answer) {
        this.user = user;
        this.question = question;
        this.answer = answer;
        this.toolUsed = "rag";
    }

    public ChatHistory(AppUser user, String question, String answer, String toolUsed) {
        this.user = user;
        this.question = question;
        this.answer = answer;
        this.toolUsed = toolUsed;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getToolUsed() {
        return toolUsed;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
