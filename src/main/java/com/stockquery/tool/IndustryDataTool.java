package com.stockquery.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class IndustryDataTool {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Tool(name = "getIndustryRanking", value = "获取A股行业板块涨跌排行榜，包括各行业的涨跌幅、领涨股、上涨下跌家数等。当用户询问今日哪些行业表现好、行业轮动、板块行情时使用此工具。")
    public String getIndustryRanking() {
        try {
            String url = "https://vip.stock.finance.sina.com.cn/q/view/newSinaHy.php";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://finance.sina.com.cn/")
                    .GET()
                    .build();

            byte[] bytes = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            String body = new String(bytes, "GBK");

            // 解析格式: var S_Finance_bankuai_sinaindustry = {"key":"code,行业名,家数,均价,涨跌额,涨跌幅%,总量,总额,领涨代码,领涨涨幅,领涨价,领涨跌,领涨名",...}
            String jsonStr = body.substring(body.indexOf("{"), body.lastIndexOf("}") + 1);

            List<IndustryItem> items = new ArrayList<>();
            String[] entries = jsonStr.split(",\"new_");
            for (String entry : entries) {
                try {
                    // 提取行业数据部分
                    int colonIdx = entry.indexOf("\":\"");
                    if (colonIdx < 0) continue;
                    String dataStr = entry.substring(colonIdx + 3).replace("\"", "");
                    String[] fields = dataStr.split(",");

                    if (fields.length < 13) continue;

                    IndustryItem item = new IndustryItem();
                    item.name = fields[1];       // 行业名
                    item.count = parseIntSafe(fields[2]); // 家数
                    item.changePercent = parseDoubleSafe(fields[5]); // 涨跌幅%
                    item.leaderName = fields[12];  // 领涨股名
                    item.leaderChangePercent = parseDoubleSafe(fields[9]); // 领涨涨幅
                    items.add(item);
                } catch (Exception ignored) {}
            }

            // 按涨跌幅降序排列
            items.sort(Comparator.comparingDouble((IndustryItem i) -> i.changePercent).reversed());

            StringBuilder sb = new StringBuilder("A股行业板块涨跌排行 (TOP 15)\n\n");
            int limit = Math.min(15, items.size());
            for (int i = 0; i < limit; i++) {
                IndustryItem item = items.get(i);
                sb.append(String.format("%d. %s | 涨跌幅: %.2f%% | 家数: %d | 领涨: %s(%.2f%%)\n",
                        i + 1,
                        item.name,
                        item.changePercent,
                        item.count,
                        item.leaderName,
                        item.leaderChangePercent
                ));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to get industry ranking", e);
            return "获取行业板块数据失败: " + e.getMessage();
        }
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private double parseDoubleSafe(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private static class IndustryItem {
        String name;
        int count;
        double changePercent;
        String leaderName;
        double leaderChangePercent;
    }
}
