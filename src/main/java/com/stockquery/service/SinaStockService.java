package com.stockquery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SinaStockService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public List<Map<String, Object>> getKLineData(String stockCode, int days) {
        try {
            String symbol = convertStockCode(stockCode);
            String url = String.format(
                    "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=%s&scale=240&ma=no&datalen=%d",
                    symbol, days
            );

            log.info("Fetching stock data from: {}", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Status: {}, Body length: {}", response.statusCode(), response.body().length());

            if (response.body() == null || response.body().isBlank()) {
                log.warn("Empty response for stock: {}", stockCode);
                return List.of();
            }

            List<Map<String, Object>> result = objectMapper.readValue(response.body(), new TypeReference<>() {});
            log.info("Parsed {} records", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch stock data from Sina for code: {}", stockCode, e);
            return List.of();
        }
    }

    /**
     * 根据关键词搜索A股股票，返回第一个匹配的代码（6位数字）
     * 使用新浪股票搜索建议接口
     */
    public String searchStockCode(String keyword) {
        try {
            String url = "https://suggest3.sinajs.cn/suggest/type=&key=" +
                    URLEncoder.encode(keyword, StandardCharsets.UTF_8) +
                    "&name=suggest";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Referer", "https://finance.sina.com.cn")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            // 新浪接口返回GBK编码
            byte[] raw = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            String body = new String(raw, "GBK");

            // 格式: var suggest="name,market,code,symbol,...;..."
            String data = body.substring(body.indexOf('"') + 1, body.lastIndexOf('"'));
            for (String entry : data.split(";")) {
                String[] f = entry.split(",");
                // f[1]=市场类型: 11=深市A股, 12=沪市A股, 41=港股等
                // f[2]=纯数字代码, f[4]=名称
                if (f.length > 4) {
                    String market = f[1];
                    String code = f[2];
                    // 只要深市(11)和沪市(12)的A股
                    if ((market.equals("11") || market.equals("12")) && code.matches("[036]\\d{5}")) {
                        log.info("Found stock: {} -> {}", keyword, code);
                        return code;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to search stock code for: {}", keyword, e);
        }
        return null;
    }

    private String convertStockCode(String stockCode) {
        String code = stockCode.replace(".SH", "").replace(".SZ", "");
        if (code.startsWith("sh") || code.startsWith("sz")) {
            return code;
        }
        if (code.startsWith("6") || code.startsWith("9")) {
            return "sh" + code;
        }
        return "sz" + code;
    }
}
