package com.stockquery.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "embedding_chunks")
public class EmbeddingChunkEntity {

    @Id
    @Column(name = "embedding_id")
    private String embeddingId;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;
}
