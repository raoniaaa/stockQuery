package com.stockquery.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_memory")
public class ChatMessageEntity {

    @Id
    @Column(name = "memory_id")
    private String memoryId;

    @Column(name = "messages", nullable = false, columnDefinition = "TEXT")
    private String messages;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
