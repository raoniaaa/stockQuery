package com.stockquery.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class RagConfig {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Bean
    public ContentRetriever contentRetriever() {
        // 检查是否已有向量数据，避免重复 ingest
        boolean hasData = checkExistingData();
        if (hasData) {
            log.info("RAG: embedding_chunks 已有数据，跳过 ingest");
        } else {
            log.info("RAG: embedding_chunks 为空，开始 ingest 文档...");
            ingestDocuments();
        }

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.75)
                .build();
    }

    private boolean checkExistingData() {
        try {
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed("test").content())
                    .maxResults(1)
                    .build();
            List<EmbeddingMatch<TextSegment>> results = embeddingStore.search(request).matches();
            return !results.isEmpty();
        } catch (Exception e) {
            log.warn("RAG: 检查已有数据失败，将重新 ingest", e);
            return false;
        }
    }

    private void ingestDocuments() {
        List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively("src/main/resources/docs");
        log.info("RAG: 加载了 {} 个文档", documents.size());

        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(1000, 200);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(documents);
        log.info("RAG: ingest 完成");
    }
}
