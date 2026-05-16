package com.stockquery.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Analysis {
    private Long id;

    @JsonProperty("stock_code")
    private String stockCode;

    @JsonProperty("stock_name")
    private String stockName;

    @JsonProperty("analysis_type")
    private String analysisType;

    private String content;

    @JsonProperty("model_used")
    private String modelUsed;

    private String summary;
    private String sentiment;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("client_ip")
    private String clientIp;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
}
