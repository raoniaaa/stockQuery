package com.stockquery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private String convertStockCode(String stockCode) {
        String code = stockCode.replace(".SH", "").replace(".SZ", "");
        if (code.startsWith("6") || code.startsWith("9")) {
            return "sh" + code;
        }
        return "sz" + code;
    }
}
