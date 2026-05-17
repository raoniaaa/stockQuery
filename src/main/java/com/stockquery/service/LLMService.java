package com.stockquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockquery.config.LLMConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public String analyzeStock(String stockCode, String stockName, List<Map<String, Object>> stockData) {
        try {
            StringBuilder dataSummary = new StringBuilder();
            dataSummary.append("股票代码: ").append(stockCode).append("\n");
            dataSummary.append("股票名称: ").append(stockName).append("\n\n");
            dataSummary.append("近期行情数据:\n");

            for (Map<String, Object> data : stockData) {
                dataSummary.append(String.format(
                        "日期: %s, 开盘: %s, 最高: %s, 最低: %s, 收盘: %s, 成交量: %s\n",
                        data.get("day"), data.get("open"), data.get("high"),
                        data.get("low"), data.get("close"), data.get("volume")
                ));
            }

            String prompt = """
                    【角色】你是一个专业的股票分析师。
                    【任务】根据提供的股票行情数据进行分析。

                    【输出要求】
                    - 必须只返回JSON，禁止返回任何其他文字、解释、markdown格式
                    - 不要加 ```json``` 标记
                    - 严格按照以下格式输出：

                    {"summary":"简要总结（50字以内）","sentiment":"Bullish或Neutral或Bearish","risk_level":"低或中或高","detail":"详细分析（200字以内）"}

                    【字段说明】
                    - summary: 对股票近期走势的一句话总结
                    - sentiment: Bullish(看涨)/Neutral(中性)/Bearish(看跌)
                    - risk_level: 低/中/高
                    - detail: 从技术面角度分析趋势、支撑位、阻力位等

                    【行情数据】
                    """ + dataSummary;

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", llmConfig.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", "你只能返回JSON格式数据，不要返回任何其他内容。禁止使用markdown格式。"),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.7,
                    "thinking", Map.of("type", "disabled")
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(llmConfig.getApiUrl()))
                    .header("Authorization", "Bearer " + llmConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode jsonNode = objectMapper.readTree(response.body());

            String content = jsonNode.get("choices").get(0).get("message").get("content").asText();

            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            objectMapper.readTree(content);

            return content;

        } catch (Exception e) {
            log.error("Failed to analyze stock with LLM", e);
            return "{\"summary\":\"分析失败\",\"sentiment\":\"Neutral\",\"risk_level\":\"高\",\"detail\":\"请稍后重试\"}";
        }
    }
}
