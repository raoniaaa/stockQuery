package com.stockquery.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KlineDataTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Tool(name = "getStockKline", value = "获取A股个股的历史K线数据（日线），返回每日的开盘价、最高价、最低价、收盘价、成交量。当用户需要查看某只股票的价格走势、技术分析时使用此工具。")
    public String getStockKline(@P("股票代码，如 600519") String stockCode,
                                @P("获取最近多少天的数据，如 30") int days) {
        try {
            String symbol = convertToSinaSymbol(stockCode);
            String url = String.format(
                    "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=%s&scale=240&ma=no&datalen=%d",
                    symbol, days
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            String body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            List<Map<String, Object>> klines = objectMapper.readValue(body, new TypeReference<>() {});

            if (klines.isEmpty()) {
                return "股票 " + stockCode + " 无K线数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("股票 %s 最近 %d 天K线数据:\n\n", stockCode, Math.min(days, klines.size())));
            sb.append("日期 | 开盘 | 收盘 | 最高 | 最低 | 成交量\n");
            sb.append("------|------|------|------|------|--------\n");

            for (Map<String, Object> kline : klines) {
                sb.append(String.format("%s | %s | %s | %s | %s | %s\n",
                        kline.get("day"),
                        kline.get("open"),
                        kline.get("close"),
                        kline.get("high"),
                        kline.get("low"),
                        kline.get("volume")
                ));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to get kline data for {}", stockCode, e);
            return "获取K线数据失败: " + e.getMessage();
        }
    }

    private String convertToSinaSymbol(String stockCode) {
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
