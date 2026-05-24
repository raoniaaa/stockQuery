package com.stockquery.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class FinancialDataTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Tool(name = "getFinancialReport", value = "获取A股公司的详细财务报表数据，包括每股收益、毛利率、ROE、资产负债率等。当用户询问某公司的财务状况、盈利能力、运营效率时使用此工具。")
    public String getFinancialReport(@P("股票代码，如 600519") String stockCode) {
        try {
            String code = stockCode.replaceAll("\\.[A-Z]{2}$", "");
            String url = "https://datacenter-web.eastmoney.com/api/data/v1/get?" +
                    "reportName=RPT_F10_FINANCE_MAINFINADATA" +
                    "&columns=SECURITY_CODE,REPORT_DATE_NAME,EPSJB,BPS,ROEJQ,XSMLL,XSJLL,ZCFZL,TOTALOPERATEREVE,PARENTNETPROFIT" +
                    "&filter=(SECURITY_CODE=%22" + code + "%22)" +
                    "&pageSize=1&sortColumns=REPORT_DATE&sortTypes=-1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://data.eastmoney.com/")
                    .GET()
                    .build();

            String body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode json = objectMapper.readTree(body);

            if (!json.get("success").asBoolean() || json.get("result") == null) {
                return "未找到股票 " + stockCode + " 的财务数据";
            }

            JsonNode data = json.get("result").get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                return "股票 " + stockCode + " 的财务数据为空";
            }

            JsonNode latest = data.get(0);
            return String.format("""
                    财务报表数据 (股票: %s，报告期: %s)
                    每股收益(EPS): %s 元
                    每股净资产: %s 元
                    ROE(加权): %s%%
                    毛利率: %s%%
                    净利率: %s%%
                    资产负债率: %s%%
                    营业总收入: %s 亿元
                    归母净利润: %s 亿元
                    """,
                    safeGet(latest, "SECURITY_CODE"),
                    safeGet(latest, "REPORT_DATE_NAME"),
                    safeGet(latest, "EPSJB"),
                    safeGet(latest, "BPS"),
                    safeGet(latest, "ROEJQ"),
                    safeGet(latest, "XSMLL"),
                    safeGet(latest, "XSJLL"),
                    safeGet(latest, "ZCFZL"),
                    safeDiv(latest, "TOTALOPERATEREVE", 100000000),
                    safeDiv(latest, "PARENTNETPROFIT", 100000000)
            );
        } catch (Exception e) {
            log.error("Failed to get financial report for {}", stockCode, e);
            return "获取财务报表失败: " + e.getMessage();
        }
    }

    private String safeGet(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return "N/A";
    }

    private String safeDiv(JsonNode node, String field, double divisor) {
        if (node.has(field) && !node.get(field).isNull()) {
            return String.format("%.2f", node.get(field).asDouble() / divisor);
        }
        return "N/A";
    }
}
