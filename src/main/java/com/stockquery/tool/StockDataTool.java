package com.stockquery.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
public class StockDataTool {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Tool(name = "getStockQuote", value = "获取A股个股的实时行情数据，包括最新价、涨跌幅、成交量、总市值、流通市值等。当用户询问某只股票的行情、估值、基本面数据时使用此工具。")
    public String getStockQuote(@P("股票代码，如 600519、002594、300750") String stockCode) {
        try {
            String symbol = convertToTencentSymbol(stockCode);
            String url = "https://qt.gtimg.cn/q=" + symbol;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            byte[] bytes = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            String body = new String(bytes, "GBK");

            // 解析腾讯行情格式: v_sh600519="1~贵州茅台~600519~当前价~昨收~今开~..."
            String content = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
            String[] fields = content.split("~");

            if (fields.length < 50) {
                return "未找到股票 " + stockCode + " 的数据，返回字段不足";
            }

            String name = fields[1];
            String code = fields[2];
            String currentPrice = fields[3];
            String yesterdayClose = fields[4];
            String todayOpen = fields[5];
            String volume = fields[6]; // 成交量（手）
            String buyVolume = fields[7];
            String sellVolume = fields[8];
            String high = fields[33];
            String low = fields[34];
            String changeAmount = fields[31];
            String changePercent = fields[32];
            String turnover = fields[37]; // 成交额（万元）
            String pe = fields[39]; // 市盈率
            String amplitude = fields[43]; // 振幅
            String circulationMarketValue = fields[44]; // 流通市值（亿）
            String totalMarketValue = fields[45]; // 总市值（亿）
            String pb = fields[46]; // 市净率
            String turnoverRate = fields[38]; // 换手率

            return String.format("""
                    股票: %s (%s)
                    当前价: %s 元
                    昨收: %s | 今开: %s
                    最高: %s | 最低: %s
                    涨跌额: %s | 涨跌幅: %s%%
                    成交量: %s 手 | 成交额: %s 万元
                    换手率: %s%%
                    总市值: %s 亿 | 流通市值: %s 亿
                    市盈率(PE): %s | 市净率(PB): %s
                    振幅: %s%%
                    """,
                    name, code,
                    currentPrice,
                    yesterdayClose, todayOpen,
                    high, low,
                    changeAmount, changePercent,
                    volume, turnover,
                    turnoverRate,
                    totalMarketValue, circulationMarketValue,
                    pe, pb,
                    amplitude
            );
        } catch (Exception e) {
            log.error("Failed to get stock quote for {}", stockCode, e);
            return "获取股票行情失败: " + e.getMessage();
        }
    }

    private String convertToTencentSymbol(String stockCode) {
        String code = stockCode.replaceAll("\\.[A-Z]{2}$", "");
        if (code.startsWith("sh") || code.startsWith("sz")) {
            return code;
        }
        if (code.startsWith("6") || code.startsWith("5") || code.startsWith("9")) {
            return "sh" + code;
        }
        return "sz" + code;
    }
}
