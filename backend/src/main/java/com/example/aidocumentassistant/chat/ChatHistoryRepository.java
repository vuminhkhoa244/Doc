package com.example.aidocumentassistant.chat;

import com.example.aidocumentassistant.auth.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    List<ChatHistory> findByUserOrderByTimestampDesc(AppUser user);
}
