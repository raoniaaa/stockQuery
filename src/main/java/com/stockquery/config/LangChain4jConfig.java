package com.stockquery.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties({
        LangChain4jConfig.OpenAiProperties.class,
        LangChain4jConfig.StreamingChatProperties.class,
        LangChain4jConfig.EmbeddingProperties.class
})
public class LangChain4jConfig {

    @Bean
    public ChatModel chatModel(OpenAiProperties props) {
        return OpenAiChatModel.builder()
                .apiKey(props.apiKey())
                .baseUrl(props.baseUrl())
                .modelName(props.modelName())
                .temperature(0.7)
                .maxTokens(4096)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel(StreamingChatProperties props) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(props.apiKey())
                .baseUrl(props.baseUrl())
                .modelName(props.modelName())
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(EmbeddingProperties props) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(props.apiKey())
                .baseUrl(props.baseUrl())
                .modelName(props.modelName())
                .maxSegmentsPerBatch(32)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embedding_chunks")
                .dimension(1024)
                .build();
    }

    @ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
    public record OpenAiProperties(String apiKey, String baseUrl, String modelName) {}

    @ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
    public record StreamingChatProperties(String apiKey, String baseUrl, String modelName) {}

    @ConfigurationProperties(prefix = "langchain4j.open-ai.embedding-model")
    public record EmbeddingProperties(String apiKey, String baseUrl, String modelName) {}
}
