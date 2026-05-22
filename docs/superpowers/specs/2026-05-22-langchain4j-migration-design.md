# stockQuery LangChain4j 改造设计文档

## 概述

将 stockQuery 项目从手写 HttpClient + Supabase REST API 架构，全面升级为 LangChain4j 驱动的 AI 股票分析助手，支持 RAG 知识检索、对话记忆、SSE 流式输出和工具调用。

## 技术栈升级

| 组件 | 当前 | 升级后 |
|---|---|---|
| Java | 17 | 25 |
| Spring Boot | 3.2.5 | 3.5.3 |
| LLM 调用 | 手写 HttpClient | LangChain4j AiServices |
| 数据库访问 | Supabase REST API | Supabase PostgreSQL JDBC |
| 向量存储 | 无 | pgvector |
| 对话记忆 | 无 | Supabase PostgreSQL (chat_memory 表) |
| Embedding | 无 | SiliconFlow BAAI/bge-m3 |
| 流式输出 | 无 | SSE (Flux<String>) |

## 架构

```
用户请求 → AiController (SSE Flux<String>)
                ↓
         StockAiService (LangChain4j AiServices 代理)
                ↓
    ┌───────────┼───────────────────┐
    │           │                   │
  @Tool      ChatMemory         RAG Retriever
 获取数据    (Supabase PG)     EmbeddingStore(pgvector)
    │           │                   │
 东方财富API  chat_memory表    embedding_chunks表
              (JSON存储)       (向量+文档片段)
```

## 决策记录

- **向量存储**: pgvector (Supabase PostgreSQL 已内置 pgvector 扩展)
- **对话记忆**: Supabase PostgreSQL (不使用 MySQL，统一数据库)
- **数据库访问**: 全部切 JDBC (JPA)，移除 Supabase REST API 方式
- **Embedding 模型**: SiliconFlow BAAI/bge-m3 (API 调用，与 aicoderhelper 一致)
- **前端**: 不改动，只提供后端 API
- **流式输出**: SSE (Flux<String>)，参考 aicoderhelper 实现
- **@Tool 工具**: 4 个东方财富 API 封装为 LangChain4j Tool

## 新增依赖

```xml
<!-- LangChain4j 核心 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- OpenAI 兼容接口 (智谱GLM + SiliconFlow Embedding 共用) -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- pgvector 向量存储 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Markdown 文档解析 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-markdown</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- MyBatis-Plus (ChatMemoryStore 实现) -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>
```

## 数据库表设计

### stock_analyses (替代 REST API 存储)

```sql
CREATE TABLE stock_analyses (
    id BIGSERIAL PRIMARY KEY,
    stock_code TEXT NOT NULL,
    stock_name TEXT,
    analysis_type TEXT,
    content TEXT,
    summary TEXT,
    sentiment TEXT,
    risk_level TEXT,
    model_used TEXT,
    client_ip TEXT,
    memory_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### chat_memory (对话记忆)

```sql
CREATE TABLE chat_memory (
    memory_id VARCHAR(255) PRIMARY KEY,
    messages TEXT NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### embedding_chunks (pgvector 向量存储)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE embedding_chunks (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    metadata_json TEXT,
    embedding vector(1024)  -- BGE-M3 输出维度
);

CREATE INDEX ON embedding_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);
```

## 项目结构变更

### 新增文件

```
config/
  LangChain4jConfig.java          # ChatModel / EmbeddingModel / EmbeddingStore beans
  CorsConfig.java                 # CORS 配置 (开发环境)

controller/
  AiController.java               # SSE 聊天端点 GET /api/ai/chat

model/
  ChatMessageEntity.java          # JPA @Entity: chat_memory 表
  EmbeddingChunkEntity.java       # JPA @Entity: embedding_chunks 表
  Analysis.java                   # [重写] 改为 JPA @Entity

repository/
  AnalysisRepository.java         # Spring Data JPA
  ChatMessageRepository.java      # Spring Data JPA
  EmbeddingChunkRepository.java   # Spring Data JPA + 自定义向量检索

memory/
  PgChatMemoryStore.java          # 实现 LangChain4j ChatMemoryStore 接口

rag/
  RagConfig.java                  # 文档加载 → 分块 → 嵌入 → pgvector 存储

tool/
  StockDataTool.java              # @Tool: 东方财富个股实时行情+估值
  FinancialDataTool.java          # @Tool: 东方财富财务报表数据
  IndustryDataTool.java           # @Tool: 东方财富行业板块排行
  KlineDataTool.java              # @Tool: K线数据 (替代 SinaStockService)

service/
  StockAiService.java             # AiServices 接口定义 (流式+RAG+Tool)

factory/
  StockAiServiceFactory.java      # AiServices.builder() 工厂 Bean
```

### 修改文件

- `pom.xml` — 升级 Java 25、Spring Boot 3.5.3，新增依赖
- `application.yml` — 新增 JPA/LangChain4j 配置
- `StockQueryApplication.java` — 保留，可能需要调整包扫描
- `StockController.java` — 保留股票数据端点，分析端点改用 JPA Repository

### 删除文件

- `SupabaseService.java` — REST API 方式废弃
- `LLMService.java` — 被 LangChain4j AiServices 替代
- `LLMConfig.java` — 被 LangChain4jConfig 替代
- `SupabaseConfig.java` — 被 Spring Boot JPA 自动配置替代
- `SinaStockService.java` — 被 KlineDataTool 替代

## @Tool 工具定义

基于已验证的 4 个东方财富公开 API：

| Tool | 方法名 | API 地址 | 返回数据 |
|---|---|---|---|
| getStockQuote | 获取个股实时行情+估值 | push2.eastmoney.com/api/qt/stock/get | 价格/PE/PB/ROE/市值/营收/利润率 |
| getFinancialReport | 获取财务报表数据 | emweb.securities.eastmoney.com/.../ZYZBAjaxNew | EPS/毛利率/ROE/现金流/周转率 |
| getIndustryRanking | 获取行业板块排行 | push2.eastmoney.com/api/qt/clist/get | 行业涨跌幅排行/领涨股 |
| getStockKline | 获取K线数据 | push2his.eastmoney.com | OHLCV 日线数据 |

LLM 根据用户问题自动判断调用哪些工具，不再像现在固定传入 K 线数据。

## RAG 流程

```
启动时:
  src/main/resources/docs/**/*.md
    → DocumentParser (Markdown)
    → TextSplitter (max=1000字, overlap=200)
    → EmbeddingModel (BGE-M3 via SiliconFlow)
    → EmbeddingStore (pgvector embedding_chunks 表)

查询时:
  用户问题
    → EmbeddingModel 生成查询向量
    → pgvector 余弦相似度检索 top-5 (minScore=0.75)
    → 相关文档片段注入 System Message
    → LLM 结合 RAG 上下文 + @Tool 实时数据生成回答
```

## SSE 流式输出

- Controller 返回 `Flux<String>` 类型
- LangChain4j `StreamingChatModel` 驱动流式生成
- 前端通过 `fetch() + ReadableStream` 消费 SSE（与 aicoderhelper 前端一致）

## application.yml 配置

```yaml
server:
  port: ${PORT:8080}

spring:
  datasource:
    url: jdbc:postgresql://${SUPABASE_DB_HOST}:6543/postgres
    username: ${SUPABASE_DB_USER:postgres}
    password: ${SUPABASE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

langchain4j:
  open-ai:
    chat-model:
      api-key: ${GLM_API_KEY}
      base-url: https://open.bigmodel.cn/api/paas/v4
      model-name: GLM-4-Flash
      temperature: 0.7
      log-requests: true
    streaming-chat-model:
      api-key: ${GLM_API_KEY}
      base-url: https://open.bigmodel.cn/api/paas/v4
      model-name: GLM-4-Flash
      temperature: 0.7
    embedding-model:
      api-key: ${SILICONFLOW_API_KEY}
      base-url: https://api.siliconflow.cn/v1
      model-name: BAAI/bge-m3
```

## 需要的环境变量

```bash
# Supabase PostgreSQL (直连)
SUPABASE_DB_HOST=aws-0-cn-north-1.pooler.supabase.com
SUPABASE_DB_USER=postgres.xxxx
SUPABASE_DB_PASSWORD=xxxx

# 智谱 GLM
GLM_API_KEY=xxxx

# SiliconFlow (Embedding)
SILICONFLOW_API_KEY=sk-xxxx
```

## 风险与注意事项

1. **Supabase 连接数限制**: 免费版有并发连接限制，HikariCP 连接池设为 5
2. **pgvector 索引**: 8 篇文档数据量小，ivfflat lists=10 足够，后续文档增多可调
3. **Spring Boot 版本**: 3.5.3 + Java 25 已验证兼容（参考 aicoderhelper 项目）
4. **LangChain4j 版本**: 使用 1.0.0 稳定版，非 beta
5. **BGE-M3 向量维度**: 1024 维，pgvector 表需对应 `vector(1024)`
6. **DDL 管理**: 开发用 `ddl-auto: update`，生产环境应改为 `validate` + 手动 migration
