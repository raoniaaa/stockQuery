package com.stockquery.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockquery.model.Analysis;
import com.stockquery.repository.AnalysisRepository;
import com.stockquery.service.RateLimitService;
import com.stockquery.service.SinaStockService;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SinaStockService sinaStockService;
    private final AnalysisRepository analysisRepository;
    private final ChatModel chatModel;
    private final RateLimitService rateLimitService;

    /**
     * LLM 分析结果的类型安全容器，对应 JSON 的 4 个必需字段。
     */
    private record AnalysisResult(String summary, String sentiment, String riskLevel, String detail) {

        /**
         * 尝试将 JSON 字符串解析为 AnalysisResult，返回 null 表示解析失败。
         */
        static AnalysisResult parse(String json) {
            try {
                String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                JsonNode node = objectMapper.readTree(cleaned);
                String summary = node.path("summary").asText(null);
                String sentiment = node.path("sentiment").asText(null);
                String riskLevel = node.path("risk_level").asText(null);
                String detail = node.path("detail").asText(null);
                if (summary == null || sentiment == null || riskLevel == null || detail == null) {
                    log.warn("分析结果缺少必需字段: summary={}, sentiment={}, risk_level={}, detail={}",
                            summary, sentiment, riskLevel, detail);
                    return null;
                }
                return new AnalysisResult(summary, sentiment, riskLevel, detail);
            } catch (Exception e) {
                log.warn("解析分析结果失败: {}", e.getMessage());
                return null;
            }
        }

        /** 序列化为 JSON 字符串存入数据库 */
        String toJson() {
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "summary", summary, "sentiment", sentiment,
                        "risk_level", riskLevel, "detail", detail));
            } catch (Exception e) {
                return "{\"summary\":\"" + summary + "\",\"sentiment\":\"" + sentiment
                        + "\",\"risk_level\":\"" + riskLevel + "\",\"detail\":\"" + detail + "\"}";
            }
        }
    }

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

    @GetMapping("/search")
    public ResponseEntity<Map<String, String>> searchStock(@RequestParam("keyword") String keyword) {
        String code = sinaStockService.searchStockCode(keyword);
        if (code != null) {
            return ResponseEntity.ok(Map.of("code", code));
        }
        return ResponseEntity.ok(Map.of("code", ""));
    }

    @GetMapping("/market-overview")
    public ResponseEntity<Map<String, Object>> getMarketOverview() {
        try {
            String url = "https://qt.gtimg.cn/q=sh000001";
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .build();
            java.net.http.HttpResponse<byte[]> response = sinaStockService.getHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            String body = new String(response.body(), "GBK");
            String[] parts = body.split("~");
            if (parts.length > 32) {
                String name = parts[1];
                String current = parts[3];
                String change = parts[31];
                String changePercent = parts[32];
                return ResponseEntity.ok(Map.of(
                        "name", name,
                        "current", current,
                        "change", change,
                        "changePercent", changePercent));
            }
        } catch (Exception e) {
            // ignore
        }
        return ResponseEntity.ok(Map.of("name", "上证指数", "current", "--", "change", "--", "changePercent", "--"));
    }

    @GetMapping("/analyses/all")
    public ResponseEntity<List<Analysis>> getAllAnalyses(HttpServletRequest request) {
        String ip = getClientIp(request);
        List<Analysis> analyses = analysisRepository.findByClientIpOrderByCreatedAtDesc(ip);
        return ResponseEntity.ok(analyses);
    }

    @GetMapping("/analysis/{stockCode}")
    public ResponseEntity<List<Analysis>> getAnalyses(
            @PathVariable("stockCode") String stockCode,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        List<Analysis> analyses = analysisRepository.findByStockCodeAndClientIpOrderByCreatedAtDesc(stockCode, ip);
        return ResponseEntity.ok(analyses);
    }

    @PostMapping("/analyze/{stockCode}")
    public ResponseEntity<Map<String, String>> analyzeStock(
            @PathVariable("stockCode") String stockCode,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {

        String ip = getClientIp(request);

        if (!rateLimitService.allowIp(ip)) {
            long wait = rateLimitService.getIpWaitTime(ip) / 1000 + 1;
            return ResponseEntity.status(429).body(Map.of("error", "请求太频繁，请" + wait + "秒后重试"));
        }

        String stockName = body != null ? body.getOrDefault("stockName", "") : "";

        // 当日缓存：同一股票+同一IP，当天内直接返回已有分析结果
        OffsetDateTime todayStart = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime todayEnd = todayStart.plusDays(1);
        var cached = analysisRepository.findTopByStockCodeAndClientIpAndCreatedAtBetweenOrderByCreatedAtDesc(
                stockCode, ip, todayStart, todayEnd);
        if (cached.isPresent()) {
            log.info("命中当日缓存，stockCode={}, ip={}", stockCode, ip);
            Analysis a = cached.get();
            return ResponseEntity.ok(Map.of(
                    "analysis", a.getContent(),
                    "detail", extractDetail(a.getContent()),
                    "sentiment", a.getSentiment() != null ? a.getSentiment() : "",
                    "risk_level", a.getRiskLevel() != null ? a.getRiskLevel() : "",
                    "summary", a.getSummary() != null ? a.getSummary() : ""));
        }

        List<Map<String, Object>> stockData = sinaStockService.getKLineData(stockCode, 30);
        if (stockData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无法获取股票数据"));
        }

        String quoteData = fetchStockQuote(stockCode);
        String prompt = buildPrompt(stockCode, stockName, stockData, quoteData);
        String rawInput;
        try {
            rawInput = chatModel.chat(prompt);
        } catch (Exception e) {
            log.error("LLM 调用失败，stockCode={}", stockCode, e);
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("访问量过大") || msg.contains("稍后再试")) {
                return ResponseEntity.status(429).body(Map.of("error", "模型当前访问量过大，请稍后再试。"));
            }
            return ResponseEntity.internalServerError().body(Map.of("error", "模型服务暂时不可用，请稍后再试。"));
        }

        AnalysisResult result = parseAnalysis(rawInput);

        // 解析失败时，发一次修正 prompt 让 LLM 重新输出
        if (result == null) {
            log.info("首次解析失败，发送修正 prompt，stockCode={}", stockCode);
            String retryPrompt = "你上次返回的内容格式不正确。请严格只返回以下JSON格式，不要输出任何其他内容：\n"
                    + "{\"summary\": \"一句话摘要\", \"sentiment\": \"Bullish或Neutral或Bearish\", "
                    + "\"risk_level\": \"low或mid或high\", \"detail\": \"200-400字详细分析\"}\n"
                    + "上次的原始输出：\n" + rawInput;
            String retryResponse = chatModel.chat(retryPrompt);
            result = parseAnalysis(retryResponse);
        }

        // 两次都失败，返回错误
        if (result == null) {
            log.error("分析结果解析失败（已重试），stockCode={}", stockCode);
            return ResponseEntity.internalServerError().body(Map.of("error", "分析失败，请稍后重试"));
        }

        Analysis savedAnalysis = Analysis.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .analysisType("ai_analysis")
                .content(result.toJson())
                .modelUsed("DeepSeek-V4-Flash")
                .summary(result.summary())
                .sentiment(result.sentiment())
                .riskLevel(result.riskLevel())
                .clientIp(ip)
                .build();
        analysisRepository.save(savedAnalysis);

        return ResponseEntity.ok(Map.of(
                "analysis", result.toJson(),
                "detail", result.detail(),
                "sentiment", result.sentiment(),
                "risk_level", result.riskLevel(),
                "summary", result.summary()));
    }

    /**
     * 解析 LLM 返回的文本为 AnalysisResult。
     * 剥离 markdown 包裹 → JSON 解析 → 关键字段校验。
     */
    private AnalysisResult parseAnalysis(String rawInput) {
        String cleaned = rawInput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        return AnalysisResult.parse(cleaned);
    }

    /**
     * 从存储的 JSON content 中提取 detail 字段。
     */
    private String extractDetail(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            return node.path("detail").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String buildPrompt(String stockCode, String stockName, List<Map<String, Object>> stockData, String quoteData) {
        StringBuilder sb = new StringBuilder();
        sb.append("系统指令：你只能返回JSON格式数据，不要输出任何其他内容。\n\n");
        sb.append("你是一位专业的A股股票分析师。请根据以下股票 ").append(stockCode);
        if (!stockName.isEmpty()) {
            sb.append("(").append(stockName).append(")");
        }
        sb.append(" 的数据进行综合分析。\n\n");
        if (!quoteData.isEmpty()) {
            sb.append("【实时行情】\n").append(quoteData).append("\n\n");
        }
        sb.append("【K线数据（近30日）】\n");
        for (Map<String, Object> row : stockData) {
            sb.append(String.format("日期:%s 开盘:%s 最高:%s 最低:%s 收盘:%s 成交量:%s\n",
                    row.get("day"), row.get("open"), row.get("high"),
                    row.get("low"), row.get("close"), row.get("volume")));
        }
        sb.append("\n请结合实时行情和K线数据综合分析，严格只返回以下4个字段的JSON：\n");
        sb.append("{\"summary\": \"一句话摘要\", \"sentiment\": \"Bullish或Neutral或Bearish\", \"risk_level\": \"low或mid或high\", \"detail\": \"200-400字的详细分析（包含趋势判断、支撑压力位、估值评价和操作建议）\"}\n");
        sb.append("注意：只输出这个JSON对象，不要输出markdown包裹或其他字段。分析时请综合考虑价格趋势和估值指标。");
        return sb.toString();
    }

    /**
     * 获取个股实时行情（腾讯财经API，GBK编码）
     */
    private String fetchStockQuote(String stockCode) {
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

            if (parts.length >= 47) {
                return String.format(
                        "当前价: %s 元 | 涨跌幅: %s%% | 昨收: %s | 今开: %s\n最高: %s | 最低: %s | 成交量: %s 手\nPE: %s | PB: %s | 总市值: %s 亿 | 流通市值: %s 亿",
                        parts[3], parts[32], parts[4], parts[5],
                        parts[33], parts[34], parts[6],
                        parts[39], parts[46], parts[45], parts[44]);
            }
        } catch (Exception e) {
            log.warn("获取实时行情失败, stockCode={}: {}", stockCode, e.getMessage());
        }
        return "";
    }
}
