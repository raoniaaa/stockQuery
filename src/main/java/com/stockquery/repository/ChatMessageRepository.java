package com.stockquery.repository;

import com.stockquery.model.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {
}
