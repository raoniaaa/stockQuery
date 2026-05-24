package com.stockquery.memory;

import com.stockquery.model.ChatMessageEntity;
import com.stockquery.repository.ChatMessageRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class PgChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository chatMessageRepository;

    public PgChatMemoryStore(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Optional<ChatMessageEntity> entity = chatMessageRepository.findById(memoryId.toString());
        if (entity.isEmpty()) {
            return List.of();
        }
        return ChatMessageDeserializer.messagesFromJson(entity.get().getMessages());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        Optional<ChatMessageEntity> existing = chatMessageRepository.findById(memoryId.toString());
        if (existing.isPresent()) {
            ChatMessageEntity entity = existing.get();
            entity.setMessages(json);
            entity.setUpdatedAt(LocalDateTime.now());
            chatMessageRepository.save(entity);
        } else {
            ChatMessageEntity entity = ChatMessageEntity.builder()
                    .memoryId(memoryId.toString())
                    .messages(json)
                    .updatedAt(LocalDateTime.now())
                    .build();
            chatMessageRepository.save(entity);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMessageRepository.deleteById(memoryId.toString());
    }
}
