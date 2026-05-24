# StockAgent — AI 智能股票投研分析系统

## 项目简介

基于 LangChain4j 构建的全栈 AI 股票投研平台。系统以 **Agent + RAG + 记忆 + 工具调用** 为核心架构，用户通过自然语言对话即可获取实时行情、财务报表、K线分析、行业板块排行等专业级投研分析。AI Agent 自主决策调用哪些工具、是否检索知识库，并结合多轮对话记忆给出连贯的专业回答。

**线上地址**：http://124.221.126.161

## 技术栈

| 层级 | 技术 |
|------|------|
| **AI 框架** | LangChain4j（Agent / RAG / Memory / Tools 全链路） |
| **大模型** | 智谱 GLM-4-Flash（Chat + Streaming） |
| **向量模型** | SiliconFlow BAAI/bge-m3（Embedding） |
| **向量数据库** | Supabase pgvector（PostgreSQL 扩展） |
| **后端** | Spring Boot 3.5 + Java 25 |
| **前端** | Vue 3 + TypeScript + Vite + ECharts |
| **数据库** | Supabase PostgreSQL（对话记忆 + 分析记录） |
| **数据源** | 新浪财经 / 腾讯财经 / 东方财富 |
| **部署** | Docker 多阶段构建 + Docker Compose |

---

## 核心架构：Agent + RAG + Memory + Tools

```
用户自然语言输入
       |
       v
  ┌─────────────┐
  │  AI Agent    │  ← LangChain4j AiServices 驱动
  │  (GLM-4)     │
  └──────┬───────┘
         │ 自主决策
    ┌────┼────┬────────┐
    v    v    v        v
  工具   工具  工具    RAG 检索
  调用   调用  调用    知识库
    │    │    │        │
    v    v    v        v
  实时  财务  行业   专业研报
  行情  报表  排行   知识片段
    │    │    │        │
    └────┴────┴────────┘
              |
              v
       流式 SSE 输出
       (Streaming)
```

---

## 一、AI Agent 自主决策

系统的核心是一个 **自主决策的 AI Agent**，而非简单的问答机器人。

**实现方式**：基于 LangChain4j 的 `AiServices` 构建，Agent 拥有完整的 System Prompt 定义其角色和行为规范：

```java
@SystemMessage("""
    你是一位专业的A股股票分析师。你需要根据用户的问题，利用可用的工具获取实时数据，
    并结合你的专业知识进行分析。
    你可以使用以下工具：
    - getStockQuote: 获取个股实时行情和估值指标
    - getFinancialReport: 获取公司详细财务报表
    - getIndustryRanking: 获取行业板块涨跌排行
    - getStockKline: 获取个股历史K线数据
    当用户提到具体股票时，请先调用工具获取最新数据，再进行分析。
    相关的金融知识文档会通过 RAG 自动提供给你作为参考。
""")
```

**Agent 决策流程**：
1. 接收用户自然语言输入
2. Agent 自主判断需要调用哪些工具（可并行调用多个）
3. 工具返回的实时数据作为上下文注入对话
4. RAG 检索到的专业知识自动补充为参考资料
5. Agent 综合所有信息，流式输出专业分析

**关键特性**：
- **工具调用完全自主**：Agent 根据用户意图自行决定调用哪个工具、传什么参数，无需前端硬编码
- **多工具并行**：同一轮对话中可同时调用行情+财务+K线等多个工具
- **流式输出**：使用 `StreamingChatModel` 实现 SSE 流式响应，用户实时看到 AI 的分析过程

---

## 二、RAG 检索增强生成

系统集成了 **4 个垂直领域的金融知识库**，通过 RAG 机制为 Agent 提供专业知识支撑。

**知识库内容**：

| 知识库 | 内容 |
|--------|------|
| 估值方法论 | PE/PB/DCF 等估值模型的理论与应用 |
| 宏观策略研究 | 美联储政策、A股牛市规律、板块轮动规律 |
| 行业与个股研究 | 行业分析框架、个股研究方法论 |
| 财务分析方法论 | 三表分析、财务指标解读、杜邦分析 |

**技术实现**：

```java
// 文档加载 → 分段 → 向量化 → 存储
List<Document> documents = FileSystemDocumentLoader
    .loadDocumentsRecursively("src/main/resources/docs");

DocumentByParagraphSplitter splitter = 
    new DocumentByParagraphSplitter(1000, 200);  // 按段落切分，1000字上限，200字重叠

EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
    .documentSplitter(splitter)
    .textSegmentTransformer(segment -> TextSegment.from(
        segment.metadata().getString("file_name") + "\n" + segment.text(),
        segment.metadata()
    ))
    .embeddingModel(embeddingModel)   // BAAI/bge-m3
    .embeddingStore(embeddingStore)   // pgvector
    .build();

ingestor.ingest(documents);
```

**检索策略**：
- **Embedding 模型**：SiliconFlow BAAI/bge-m3，支持中英双语高质量向量化
- **向量存储**：Supabase pgvector 扩展，基于 PostgreSQL 的向量相似度搜索
- **检索参数**：`maxResults=5, minScore=0.75`，只返回最相关的 5 个片段，相似度低于 0.75 的自动过滤
- **自动 ingest**：启动时检测向量库是否已有数据，避免重复导入
- **文件名注入**：每个文本片段的元数据中包含源文件名，使 AI 回答时可以引用来源

---

## 三、持久化对话记忆

系统实现了 **基于 PostgreSQL 的多轮对话记忆**，支持用户级别的会话隔离。

**核心设计**：

```java
@Component
public class PgChatMemoryStore implements ChatMemoryStore {

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // 从 PostgreSQL 加载对话历史
        Optional<ChatMessageEntity> entity = 
            chatMessageRepository.findById(memoryId.toString());
        return entity.map(e -> 
            ChatMessageDeserializer.messagesFromJson(e.getMessages())
        ).orElse(List.of());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 序列化为 JSON 并持久化
        String json = ChatMessageSerializer.messagesToJson(messages);
        // upsert 逻辑...
    }
}
```

**特性**：
- **用户隔离**：以客户端 IP 作为 `memoryId`，不同用户拥有独立的对话上下文
- **滑动窗口**：`MessageWindowChatMemory(maxMessages=20)`，保留最近 20 条消息，避免上下文过长
- **持久化存储**：对话历史存储在 PostgreSQL `chat_memory` 表，服务重启后记忆不丢失
- **自动管理**：LangChain4j 的 `chatMemoryProvider` 自动在每轮对话中加载和更新记忆

**工厂装配**：

```java
@Bean
public StockAiService stockAiService() {
    return AiServices.builder(StockAiService.class)
        .chatModel(chatModel)
        .streamingChatModel(streamingChatModel)
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(20)
            .chatMemoryStore(pgChatMemoryStore)  // PostgreSQL 持久化
            .build())
        .contentRetriever(contentRetriever)       // RAG 检索器
        .tools(stockDataTool, financialDataTool,   // 4 个工具
               industryDataTool, klineDataTool)
        .build();
}
```

---

## 四、4 个自定义工具（Function Calling）

Agent 拥有 4 个专业金融数据工具，覆盖行情、财务、行业、K线四大维度：

### 4.1 getStockQuote — 实时行情

- **数据源**：腾讯财经实时行情 API（`qt.gtimg.cn`）
- **返回数据**：当前价、涨跌额/幅、成交量/额、换手率、总市值/流通市值、PE/PB、振幅
- **编码处理**：腾讯 API 返回 GBK 编码，使用 `ofByteArray()` + 手动 GBK 解码

### 4.2 getFinancialReport — 财务报表

- **数据源**：东方财富数据中心 API（`datacenter-web.eastmoney.com`）
- **返回数据**：每股收益(EPS)、每股净资产、ROE、毛利率、净利率、资产负债率、营业总收入、归母净利润
- **数据处理**：金额从元转换为亿元展示，空值安全处理

### 4.3 getIndustryRanking — 行业板块排行

- **数据源**：新浪财经行业板块数据（`vip.stock.finance.sina.com.cn`）
- **返回数据**：TOP 15 行业涨跌幅排名、各行业领涨股、涨跌家数
- **排序逻辑**：按涨跌幅降序排列

### 4.4 getStockKline — K线历史数据

- **数据源**：新浪财经 K线 API（`money.finance.sina.com.cn`）
- **返回数据**：指定天数的日线数据（日期、开盘、收盘、最高、最低、成交量）
- **前端联动**：前端自动检测 AI 回复中的股票代码，调用此工具获取数据并渲染 ECharts K线图

---

## 五、前端工程

### 5.1 智能 K线触发

前端实现了 **股票名称自动识别 → 代码解析 → K线获取** 的完整链路：

```typescript
// 从自然语言中提取股票名称
function extractStockName(text: string): string | null {
  const patterns = [
    /(?:分析|看看|查一下|帮我分析)\s*([^\s]{2,8}?)(?:的|最近|走势|行情)/,
    /([^\s]{2,8}?)(?:的走势|的行情|怎么样|最近走势)/,
  ]
  // 匹配纯中文名称
}

// 发送前自动解析名称为代码
if (!stockCode) {
  const name = extractStockName(text)
  if (name) stockCode = await searchStock(name)  // 后端搜索接口
}
if (stockCode) klinePromise = fetchKline(stockCode)
```

### 5.2 ECharts K线图

- 使用 ECharts 渲染专业级蜡烛图（Candlestick），包含 K线 + 成交量柱状图
- 根据区间涨跌自动切换红绿配色
- 桌面端右侧滑出面板展示，移动端内联在消息气泡中

### 5.3 流式对话体验

- SSE（Server-Sent Events）流式接收 AI 回复，逐字显示
- 思考中动画（三点脉冲）提示 Agent 正在分析
- 响应式布局：桌面端双栏（聊天 + K线面板），移动端单栏自适应

---

## 六、部署架构

```
┌─────────────────────────────────────────────┐
│              Docker Compose                  │
│                                              │
│  ┌──────────────┐    ┌───────────────────┐  │
│  │   frontend    │    │    backend         │  │
│  │  nginx:alpine │───>│  spring-boot:3.5   │  │
│  │  :80          │    │  java:25           │  │
│  │  (Vue SPA)    │    │  :8080             │  │
│  └──────────────┘    └────────┬──────────┘  │
│                               │              │
└───────────────────────────────┼──────────────┘
                                │
                    ┌───────────┴───────────┐
                    │   Supabase PostgreSQL  │
                    │   - chat_memory 表     │
                    │   - analysis 表        │
                    │   - embedding_chunks   │
                    │   (pgvector 扩展)      │
                    └───────────────────────┘
```

- **前端**：Node 20 多阶段构建 → nginx:alpine 静态托管，反向代理 `/api/` 到后端
- **后端**：Maven 3.9 + JDK 25 多阶段构建 → Eclipse Temurin JRE 25 运行
- **环境变量注入**：API Key、数据库密码通过 `.env` 文件注入，不硬编码

---

## 七、技术亮点总结

| 亮点 | 说明 |
|------|------|
| **Agent 自主决策** | AI 自行判断调用哪些工具、传什么参数，非硬编码流程 |
| **RAG 知识增强** | 4 个金融领域知识库，BGE-M3 向量化，pgvector 相似度检索 |
| **持久化记忆** | PostgreSQL 存储对话历史，用户隔离，滑动窗口 20 条 |
| **多工具并行** | 4 个专业工具覆盖行情/财务/行业/K线，支持同时调用 |
| **流式输出** | SSE 流式响应，实时展示 AI 分析过程 |
| **自然语言 → K线** | 前端自动从文本提取股票名称，解析代码，获取并渲染 K线图 |
| **多数据源聚合** | 新浪 + 腾讯 + 东方财富三大数据源，GBK 编码兼容处理 |
| **容器化部署** | Docker 多阶段构建 + Compose 编排，一键部署 |
