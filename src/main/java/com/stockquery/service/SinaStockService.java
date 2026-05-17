package com.stockquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
                    "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=%dd&interval=1d",
                    symbol, days
            );

            log.info("Fetching stock data from Yahoo: {}", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Yahoo status: {}, body length: {}", response.statusCode(), response.body().length());

            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                log.warn("Yahoo returned non-200 or empty for stock: {}", stockCode);
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode chart = root.path("chart").path("result").get(0);
            JsonNode timestamps = chart.path("timestamp");
            JsonNode ohlcv = chart.path("indicators").path("quote").get(0);

            JsonNode openArr = ohlcv.path("open");
            JsonNode highArr = ohlcv.path("high");
            JsonNode lowArr = ohlcv.path("low");
            JsonNode closeArr = ohlcv.path("close");
            JsonNode volumeArr = ohlcv.path("volume");

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            List<Map<String, Object>> result = new ArrayList<>();

            for (int i = 0; i < timestamps.size(); i++) {
                if (openArr.get(i).isNull()) continue;

                Map<String, Object> row = new HashMap<>();
                long ts = timestamps.get(i).asLong();
                row.put("day", Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).format(fmt));
                row.put("open", String.valueOf(Math.round(openArr.get(i).asDouble() * 100.0) / 100.0));
                row.put("high", String.valueOf(Math.round(highArr.get(i).asDouble() * 100.0) / 100.0));
                row.put("low", String.valueOf(Math.round(lowArr.get(i).asDouble() * 100.0) / 100.0));
                row.put("close", String.valueOf(Math.round(closeArr.get(i).asDouble() * 100.0) / 100.0));
                row.put("volume", String.valueOf(volumeArr.get(i).asLong()));
                result.add(row);
            }

            log.info("Parsed {} records from Yahoo", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch stock data for code: {}", stockCode, e);
            return List.of();
        }
    }

    private String convertStockCode(String stockCode) {
        String code = stockCode.replace(".SH", "").replace(".SZ", "");
        if (code.startsWith("6") || code.startsWith("9")) {
            return code + ".SS";
        }
        return code + ".SZ";
    }
}
