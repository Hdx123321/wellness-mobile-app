# AI Advisor RAG 知识库架构设计

## 概述

为 AI Advisor 功能升级混合检索（语义 + 结构化）RAG 知识库，让 AI 能检索用户历史数据和健康趋势，提供更个性化的建议。

## 技术栈选型总结

| 层级 | 选型 | 说明 |
|------|------|------|
| Chat LLM | DeepSeek V4 Pro（现有） | 不变 |
| Embedding | OpenAI `text-embedding-3-small` 512维 | DeepSeek 不支持 embedding |
| 向量存储 | MySQL JSON 列 + Java 内存余弦相似度 | 几千条文档，暴力计算即可 |
| 文档生成 | LLM 生成自然语言摘要 | 用户触发，增量生成 |
| 降级策略 | 静默降级（RAG 失败不影响对话） | embedding 挂了走纯结构化 |

## 数据库

### 新表：`rag_documents`（Flyway V13）

```sql
CREATE TABLE rag_documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id BIGINT NULL,
  doc_type VARCHAR(30) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  embedding JSON NOT NULL,
  metadata JSON NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_rag_documents_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_rag_documents_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE SET NULL,
  CONSTRAINT ck_rag_documents_type CHECK (doc_type IN ('WEEKLY_REPORT', 'MONTHLY_REPORT', 'CONVERSATION_SUMMARY'))
);

CREATE INDEX idx_rag_documents_user_type_created
  ON rag_documents (user_id, doc_type, created_at DESC);
```

## 三类文档

| 文档类型 | 触发时机 | 输入数据 |
|----------|----------|----------|
| `WEEKLY_REPORT` | 用户打开 AI Advisor，距上次周报 ≥7 天 | 本周 tracker 聚合（count/avg/min/max）+ 对比上周 △ + 食物总览 |
| `MONTHLY_REPORT` | 用户打开 AI Advisor，距上次月报 ≥30 天 | 30 天 tracker 趋势 + 目标体重进展 |
| `CONVERSATION_SUMMARY` | 对话结束时 `onComplete` 回调生成，同 session 10 分钟内去重 | 本次对话内容 |

## 后端组件

### 新增组件

```
AiAdvisorService （修改：prompt() 升级为三段式）
  ├── DocumentGenerationService  （新增：写——生成文档）
  │     ├── AiAdvisorClient       （现有：调 LLM 写摘要）
  │     ├── EmbeddingClient       （新增：给摘要做 embedding）
  │     └── RagDocumentRepository（新增）
  ├── RagRetrievalService        （新增：读——语义+结构化检索）
  │     ├── EmbeddingClient       （新增：查询问题的 embedding）
  │     ├── RagDocumentRepository（新增）
  │     └── TrackerService       （现有：拉 30 天聚合数据）
  └── AiAdvisorClient             （现有：最终回答问题）
```

### 新增配置（application.yml / .env）

```yaml
embedding:
  base-url: ${EMBEDDING_API_BASE_URL:https://api.openai.com/v1}
  api-key: ${EMBEDDING_API_KEY:}
  model: ${EMBEDDING_MODEL:text-embedding-3-small}
```

### EmbeddingClient

```java
@Service
public class EmbeddingClient {
    // embed(String text) → float[512]
    // embedBatch(List<String> texts) → List<float[512]>
    // 调用 POST /v1/embeddings
}
```

### RagRetrievalService

```java
@Service
public class RagRetrievalService {
    // retrieve(userId, questionEmbedding) → RagContext
    // 语义：余弦相似度匹配 rag_documents，Top-K=5
    // 结构化：30 天 tracker 聚合 + 异常点（均值 ±2σ / 创 30 天极值）
    // 降级：embedding 失败 → 仅结构化；结构化失败 → 空上下文
}
```

### DocumentGenerationService

```java
@Service
public class DocumentGenerationService {
    // ensureUpToDate(userId) → void
    // 检查各类型文档是否过期 → 拉聚合数据 → LLM 生成摘要 → embedding → 写入
    // 首次用户自动从空白开始（无历史数据不回填）
    // 失败静默，下次再试
}
```

## Prompt 三段式结构

从现有的 2 条消息升级为 3 条：

```java
payload.put("messages", List.of(
    Map.of("role", "system", "content", SYSTEM_PROMPT),   // 角色定位
    Map.of("role", "user", "content", ragContext),         // RAG 检索结果
    Map.of("role", "user", "content", userQuestion)));     // 用户问题
```

## 检索流程

```
用户输入: "我最近睡眠怎么样？"
    │
    ├─ ensureUpToDate(userId)  —— 检查是否有过期文档需要生成
    │
    ├─ 语义检索
    │     问题 → EmbeddingClient.embed() → 余弦相似度匹配 Top-5 rag_documents
    │
    ├─ 结构化检索
    │     TrackerService 拉 30 天全量 → 按类型聚合（avg/min/max/trend）+ 异常点
    │
    ├─ 合并为 ragContext
    │
    └─ AiAdvisorClient.replyStream() —— SSE 流式返回
```

## 降级策略

| 故障点 | 行为 |
|--------|------|
| Embedding API 不可用 | 跳过语义检索，仅结构化 → 正常回答 |
| 文档生成失败 | log error，下次重试 → 正常对话 |
| 结构化检索失败 | 跳过，仅剩余数据 → 正常回答 |
| Chat API 不可用 | 硬失败 → 返回错误（和现在一样） |

## 监控

- SLF4J 关键节点日志：embedding 调用成功/失败、文档生成成功/失败、检索命中数、降级触发
- 和现有 `AiAdvisorService` 日志风格一致

## Android 端

**零改动。** SSE API 签名不变，响应格式不变。

## 开发顺序

| 阶段 | 内容 |
|------|------|
| 1 | Flyway V13 + `RagDocument` entity + `RagDocumentRepository` |
| 2 | `EmbeddingClient` + 配置 |
| 3 | `DocumentGenerationService` |
| 4 | `RagRetrievalService` |
| 5 | 集成到 `AiAdvisorService.prompt()` |
| 6 | 对话摘要（`onComplete` 回调 + 10min 防抖） |
