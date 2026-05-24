package com.stockquery.factory;

import com.stockquery.memory.PgChatMemoryStore;
import com.stockquery.service.StockAiService;
import com.stockquery.tool.FinancialDataTool;
import com.stockquery.tool.IndustryDataTool;
import com.stockquery.tool.KlineDataTool;
import com.stockquery.tool.StockDataTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StockAiServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private PgChatMemoryStore pgChatMemoryStore;

    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private StockDataTool stockDataTool;

    @Resource
    private FinancialDataTool financialDataTool;

    @Resource
    private IndustryDataTool industryDataTool;

    @Resource
    private KlineDataTool klineDataTool;

    @Bean
    public StockAiService stockAiService() {
        return AiServices.builder(StockAiService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(pgChatMemoryStore)
                        .build())
                .contentRetriever(contentRetriever)
                .tools(stockDataTool, financialDataTool, industryDataTool, klineDataTool)
                .build();
    }
}
