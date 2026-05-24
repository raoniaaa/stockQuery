package com.stockquery.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "analysis_type")
    private String analysisType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String sentiment;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "memory_id")
    private String memoryId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
