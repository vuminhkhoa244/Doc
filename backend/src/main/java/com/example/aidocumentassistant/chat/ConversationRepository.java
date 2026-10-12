package com.example.aidocumentassistant.chat;

import com.example.aidocumentassistant.auth.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserOrderByCreatedAtDesc(AppUser user);
}
