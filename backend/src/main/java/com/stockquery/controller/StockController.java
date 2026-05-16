package com.stockquery.controller;

import com.stockquery.model.Analysis;
import com.stockquery.service.LLMService;
import com.stockquery.service.RateLimitService;
import com.stockquery.service.SinaStockService;
import com.stockquery.service.SupabaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StockController {

    private final SinaStockService sinaStockService;
    private final SupabaseService supabaseService;
    private final LLMService llmService;
    private final RateLimitService rateLimitService;

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/data/{stockCode}")
    public ResponseEntity<List<Map<String, Object>>> getStockData(
            @PathVariable("stockCode") String stockCode,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        List<Map<String, Object>> data = sinaStockService.getKLineData(stockCode, days);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/name/{stockCode}")
    public ResponseEntity<Map<String, String>> getStockName(@PathVariable("stockCode") String stockCode) {
        try {
            String symbol = stockCode.replace(".SH", "").replace(".SZ", "");
            String prefix = (symbol.startsWith("6") || symbol.startsWith("9")) ? "sh" : "sz";
            String url = "https://qt.gtimg.cn/q=" + prefix + symbol;

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .build();

            java.net.http.HttpResponse<byte[]> response = sinaStockService.getHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            String body = new String(response.body(), "GBK");
            String[] parts = body.split("~");
            if (parts.length > 1) {
                return ResponseEntity.ok(Map.of("name", parts[1]));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("name", ""));
        }
        return ResponseEntity.ok(Map.of("name", ""));
    }

    @GetMapping("/analyses/all")
    public ResponseEntity<List<Analysis>> getAllAnalyses(HttpServletRequest request) {
        String ip = getClientIp(request);
        List<Analysis> analyses = supabaseService.getAllAnalyses(ip);
        return ResponseEntity.ok(analyses);
    }

    @GetMapping("/analysis/{stockCode}")
    public ResponseEntity<List<Analysis>> getAnalyses(
            @PathVariable("stockCode") String stockCode,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        List<Analysis> analyses = supabaseService.getAnalyses(stockCode, ip);
        return ResponseEntity.ok(analyses);
    }

    @PostMapping("/analyze/{stockCode}")
    public ResponseEntity<Map<String, String>> analyzeStock(
            @PathVariable("stockCode") String stockCode,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {

        if (!rateLimitService.allowRequest("llm")) {
            long wait = rateLimitService.getWaitTime("llm") / 1000 + 1;
            return ResponseEntity.status(429).body(Map.of("error", "请求太频繁，请" + wait + "秒后重试"));
        }

        String ip = getClientIp(request);
        String stockName = body != null ? body.getOrDefault("stockName", "") : "";

        List<Map<String, Object>> stockData = sinaStockService.getKLineData(stockCode, 30);
        if (stockData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无法获取股票数据"));
        }

        String analysisJson = llmService.analyzeStock(stockCode, stockName, stockData);

        String summary = "", sentiment = "", riskLevel = "";
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(analysisJson);
            summary = node.path("summary").asText("");
            sentiment = node.path("sentiment").asText("");
            riskLevel = node.path("risk_level").asText("");
        } catch (Exception ignored) {}

        Analysis savedAnalysis = Analysis.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .analysisType("ai_analysis")
                .content(analysisJson)
                .modelUsed("GLM-4.7-Flash")
                .summary(summary)
                .sentiment(sentiment)
                .riskLevel(riskLevel)
                .clientIp(ip)
                .createdAt(OffsetDateTime.now().plusHours(8))
                .build();
        supabaseService.saveAnalysis(savedAnalysis);

        return ResponseEntity.ok(Map.of("analysis", analysisJson));
    }
}
