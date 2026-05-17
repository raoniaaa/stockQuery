# StockPulse - AI 智能股票分析系统

基于 Spring Boot + Vue3 + Supabase + 智谱GLM-4.7-Flash 的股票智能分析平台。

用户输入股票代码，系统自动拉取近30日K线行情数据，调用大模型进行技术面分析，输出市场情绪、风险等级、趋势判断等结构化结果，并持久化存储至云端数据库。

## 线上地址

- **前端**: http://124.221.126.161:8082
- **后端 API**: http://124.221.126.161:8081/api

## 项目结构

```
stockQuery/
├── backend/                        # Spring Boot 后端 (Java 17)
│   └── src/main/java/com/stockquery/
│       ├── config/                 # 配置类 (HttpClient, Supabase, LLM)
│       ├── controller/             # REST API 接口
│       ├── model/                  # 数据模型 (Analysis)
│       └── service/                # 业务逻辑 (SinaStock, LLM, Supabase, RateLimit)
├── frontend/                       # Vue3 + TypeScript + Vite 前端
│   └── src/
│       ├── api/                    # Axios API 封装
│       ├── components/             # Vue 组件
│       ├── types/                  # TypeScript 类型定义
│       └── assets/                 # 全局样式 (暗色金融终端风格)
└── README.md
```

## 功能特性

- **股票查询** - 输入股票代码，自动填充股票名称（腾讯财经API）
- **行情数据** - 近30日K线数据表格，支持涨跌幅、成交量展示
- **价格走势** - Chart.js 绘制收盘价/最高价/最低价走势图
- **AI 分析** - 智谱GLM-4.7-Flash 进行技术面分析，输出结构化JSON
- **历史记录** - Supabase 持久化存储，支持IP隔离查看
- **速率限制** - 内存级限流，每分钟最多5次LLM调用

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Chart.js + Axios |
| 后端 | Spring Boot 3.2.5 + Java 17 |
| 数据库 | Supabase (PostgreSQL) |
| AI模型 | 智谱GLM-4.7-Flash (免费额度) |
| 行情数据 | 新浪财经 K线 API |
| 股票名称 | 腾讯财经行情 API (GBK编码) |

## Prompt 工程：强制 LLM 只返回 JSON

这是本项目最关键的 Prompt 设计。为了让大模型严格输出结构化 JSON 而不夹带任何多余文字，采用了**系统消息约束 + 用户提示词双重锁定**的策略：

### System Message（系统消息）

```
你只能返回JSON格式数据，不要返回任何其他内容。禁止使用markdown格式。
```

> 作用：从根本上禁止模型输出任何非 JSON 内容，包括 markdown 代码块、解释文字、"好的，以下是分析结果"等废话。

### User Prompt（用户提示词）

```
【角色】你是一个专业的股票分析师。
【任务】根据提供的股票行情数据进行分析。

【输出要求】
- 必须只返回JSON，禁止返回任何其他文字、解释、markdown格式
- 不要加 ```json``` 标记
- 严格按照以下格式输出：

{"summary":"简要总结（50字以内）","sentiment":"Bullish或Neutral或Bearish","risk_level":"低或中或高","detail":"详细分析（200字以内）"}

【字段说明】
- summary: 对股票近期走势的一句话总结
- sentiment: Bullish(看涨)/Neutral(中性)/Bearish(看跌)
- risk_level: 低/中/高
- detail: 从技术面角度分析趋势、支撑位、阻力位等
```

### 为什么这样设计？

1. **System Message 先行约束**：在对话最开始就用最严格的语气限制输出格式，模型在生成时会持续受到这个约束的影响
2. **JSON 示例直接嵌入 Prompt**：不使用 `{...}` 占位符，而是给出完整的 JSON 结构示例，让模型"照抄"格式
3. **禁止 markdown 标记**：LLM 默认喜欢用 ````json ... ``` ` 包裹 JSON，显式禁止这一行为
4. **双重保险 - 后端兜底清理**：即使模型偶尔违反规则返回了 markdown 包裹的 JSON，后端仍有正则清理逻辑：

```java
content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
objectMapper.readTree(content); // 验证是否为合法 JSON
```

---

## Debug 记录：股票名称乱码 "????" —— GBK 编码陷阱

### 问题描述

输入股票代码后，股票名称字段始终显示为 `????`，但用浏览器直接访问腾讯财经 API 接口，返回的中文名称完全正常。

### 排查过程

**第一步：确认 API 本身没问题**

用 curl 直接请求腾讯财经接口：
```bash
curl "https://qt.gtimg.cn/q=sz002423"
```
返回内容中包含完整的中文股票名称「中原内配」，说明 API 数据源正常。

**第二步：对比 Java 代码的请求方式**

后端使用 Java 原生 `HttpClient` 发请求，最初写法是：
```java
HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
String body = response.body();
```
`BodyHandlers.ofString()` 默认使用 **UTF-8** 解码，但腾讯财经接口返回的数据编码是 **GBK**，UTF-8 解码 GBK 字节流后就变成了乱码 `????`。

**第三步：定位根因**

用 Wireshark 抓包或在 Java 中打印原始字节数组的长度与字符串长度对比，发现字节数量远多于字符串字符数——这是典型的编码不匹配特征。腾讯财经 API 返回的 `Content-Type` 也没有明确声明 charset，导致 `HttpClient` 默认用 UTF-8 解码。

### 解决方案

改用 `BodyHandlers.ofByteArray()` 接收原始字节，再手动用 GBK 编码解码：

```java
// 修复前（UTF-8 解码 GBK 数据 → 乱码）
HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
String body = response.body();

// 修复后（手动指定 GBK 解码）
HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
String body = new String(response.body(), "GBK");
```

修复后股票名称正常显示为「中原内配」「贵州茅台」等中文名称。

### 经验总结

- 调用国内财经数据接口时，**GBK 是最常见的编码陷阱**，尤其是腾讯、新浪等老牌接口
- `HttpResponse.BodyHandlers.ofString()` 默认 UTF-8，在遇到非 UTF-8 接口时必须切换为 `ofByteArray()` + 手动指定编码
- 遇到乱码问题时，优先检查：① 响应的 Content-Type charset 声明 ② 实际字节编码 ③ 解码端使用的字符集是否匹配

---

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.8+

### 后端启动

```bash
cd backend

# 配置 application.yml 中的 Supabase 和 LLM 密钥
# 或设置环境变量：
# export SUPABASE_URL=https://xxx.supabase.co
# export SUPABASE_KEY=sb_secret_xxx
# export GLM_API_KEY=your-api-key

mvn spring-boot:run
# 启动在 http://localhost:8080
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:3000
```

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/stock/data/{stockCode}` | GET | 获取股票近30日K线数据 |
| `/api/stock/name/{stockCode}` | GET | 获取股票名称（腾讯财经API） |
| `/api/stock/analysis/{stockCode}` | GET | 获取该股票的历史分析记录 |
| `/api/stock/analyses/all` | GET | 获取当前IP下的所有分析记录 |
| `/api/stock/analyze/{stockCode}` | POST | 执行AI分析（限流5次/分钟） |

## 数据库表结构（Supabase）

### analyses 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial | 主键 |
| stock_code | text | 股票代码 |
| stock_name | text | 股票名称 |
| analysis_type | text | 分析类型（ai_analysis） |
| content | text | LLM 原始返回的 JSON |
| summary | text | 分析摘要（从 content 解析） |
| sentiment | text | 市场情绪（Bullish/Neutral/Bearish） |
| risk_level | text | 风险等级（低/中/高） |
| model_used | text | 使用的模型 |
| client_ip | text | 客户端IP（用于数据隔离） |
| created_at | timestamptz | 分析时间 |

## 环境变量

| 变量 | 说明 |
|------|------|
| `SUPABASE_URL` | Supabase 项目 URL |
| `SUPABASE_KEY` | Supabase anon/service key |
| `GLM_API_KEY` | 智谱 GLM API Key |

---

## Debug 记录：部署踩坑合集

### 1. Render 部署前端 — `frontend/dist: Is a directory`

**现象**：Render 日志显示 `Running 'frontend/dist'` → `bash: line 1: frontend/dist: Is a directory`

**原因**：选择了 "Web" 服务类型，但 `render.yaml` 配置了 `runtime: static`，两者冲突。Render 把 `staticPublishPath` 当成了启动命令去执行。

**解决**：去掉 `runtime: static`，改用 `startCommand: cd frontend && npx serve dist -l $PORT`，让 Node.js 启动静态文件服务器。

---

### 2. Render 只执行 `yarn install` 不构建

**现象**：Build 日志显示 `Running build command 'yarn'`，没有执行 `npm run build`，`dist` 目录不存在。

**原因**：Render 自动检测到 `package.json` 后直接运行 `yarn`（只装依赖），忽略了 `render.yaml` 的 `buildCommand`。

**解决**：在根 `package.json` 中添加 `prestart` 脚本，确保构建在启动前执行：
```json
"prestart": "cd frontend && npm install --include=dev && npm run build"
```

---

### 3. `vue-tsc: not found`

**现象**：构建时报 `sh: 1: vue-tsc: not found`，exit code 127。

**原因**：`vue-tsc` 在 `devDependencies` 中，Render 环境可能设置了 `NODE_ENV=production` 导致 `npm install` 跳过 devDependencies。

**解决**：安装命令加 `--include=dev`：
```
npm install --include=dev
```

---

### 4. Docker 环境变量名不匹配 — 启动失败

**现象**：后端 Docker 容器启动后 `No open ports detected`，Spring Boot 无法启动。

**原因**：`application.yml` 中用 `${SUPABASE_URL}` 等大写变量名，但 Docker `run -e` 传入的是小写变量名（如 `supabase`），Spring Boot 无法解析占位符。

**解决**：统一变量名。`application.yml.example` 改为读取与 Docker 环境变量一致的小写名称：
```yaml
supabase:
  url: ${supabase}
  key: ${key}
llm:
  api-url: ${llm}
  api-key: ${api-key}
  model: ${model}
```

---

### 5. 海外服务器无法访问国内金融 API

**现象**：后端部署在 Render（海外），调用新浪财经 API 返回空数据，分析失败。

**原因**：新浪财经、腾讯财经等国内数据接口在海外服务器无法访问（被墙或限制境外 IP）。

**解决**：将后端迁移到国内服务器（腾讯云），直接使用新浪财经 API。

---

### 6. 股票代码重复添加前缀 — `szsh600519`

**现象**：日志显示请求 `symbol=szsh600519`，API 返回空数据。

**原因**：前端传入 `sh600519`（已带前缀），`convertStockCode` 方法未判断已有前缀，又加了一次 `sz`，变成 `szsh600519`。

**解决**：`convertStockCode` 方法增加前缀判断：
```java
if (code.startsWith("sh") || code.startsWith("sz")) {
    return code;  // 已有前缀，直接返回
}
```

---

### 7. 智谱 GLM API 认证失败

**现象**：AI 分析返回 `{"error":{"code":"1000","message":"身份验证失败。"}}`

**原因**：API Key 过期或输入错误。

**解决**：去智谱AI开放平台重新获取有效 API Key，更新 Docker 环境变量中的 `api-key`。

---