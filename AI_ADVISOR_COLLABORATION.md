# AI Advisor Collaboration Guide

本文件面向负责 AI 顾问模块的开发者及其 Codex。开始修改前，请完整阅读本文，并以仓库中的实际代码和测试结果为准。

## 1. 项目介绍

WellnessMate AI 是一个 Android + Spring Boot 的健康记录应用，目标是让用户：

- 完成注册、登录和首次健康问卷；
- 记录 Food、Weight、Workout、Steps、Sleep、Water；
- 按日期查看数据、趋势图和个人健康信息；
- 与教练沟通；
- 基于自己的健康档案和近期 Tracker 数据向 AI 顾问提问。

项目采用单仓库结构：

```text
android-app/   Kotlin、Jetpack Compose、Retrofit
backend/       Java 21、Spring Boot、JPA、Flyway
docs/          架构、API 和协作文档
```

后端是模块化单体。AI 顾问代码位于 `backend/src/main/java/com/wellnessmate/advisor`，Android 对应代码位于 `android-app/app/src/main/java/com/wellnessmate/app/ui/advisor` 和 `AiAdvisorViewModel.kt`。

GitHub 仓库：`https://github.com/Hdx123321/wellness-mobile-app`

当前功能分支为 `codex/wellness-core-features`，Draft PR 为 GitHub PR #1。若 PR #1 尚未合并，请从该远程分支创建自己的分支；合并后则从最新 `main` 创建分支。

本文出现的 `backend/...`、`android-app/...` 和 `docs/...` 均为相对于仓库根目录的路径。协作双方可以把仓库 clone 到任意本地目录，不需要使用相同的磁盘、用户名或绝对路径；代码交换只通过 Git commit、远程分支和 Pull Request 完成。

## 2. 环境配置

### 2.1 必要环境

- Git 和 GitHub CLI；
- Java 21 或更新版本；
- Docker Desktop；
- Android Studio；
- Android SDK API 36.1；
- Android 模拟器或 Android 8.0（API 26）以上设备。

### 2.2 获取代码和创建协作分支

首次参与项目时，先 clone GitHub 仓库：

```powershell
git clone https://github.com/Hdx123321/wellness-mobile-app.git
cd wellness-mobile-app
git remote -v
```

`git remote -v` 应显示名为 `origin` 的上述 GitHub 地址。后续所有命令都在自己的仓库根目录执行，不依赖本文作者的本地路径。

PR #1 尚未合并时：

```powershell
git fetch origin
git switch -c codex/ai-advisor-improvements origin/codex/wellness-core-features
```

PR #1 已合并时：

```powershell
git switch main
git pull --ff-only origin main
git switch -c codex/ai-advisor-improvements
```

不要直接向他人的功能分支或 `main` 提交。每个明确任务使用独立分支和 Pull Request。

完成一组可验证的改动后：

```powershell
git status -sb
git add <本次任务涉及的文件>
git commit -m "Improve AI advisor ..."
git push -u origin codex/ai-advisor-improvements
```

随后在 GitHub 上从 `codex/ai-advisor-improvements` 向当前目标分支发起 Pull Request。不要通过复制本地文件夹、共享绝对路径或直接覆盖对方工作区来同步代码。

### 2.3 后端配置

在仓库根目录创建本地环境文件：

```powershell
Copy-Item .env.example .env
```

至少检查以下变量：

```env
MYSQL_DATABASE=wellness_mate
MYSQL_USER=wellness
MYSQL_PASSWORD=本地数据库密码
MYSQL_ROOT_PASSWORD=本地root密码
BACKEND_PORT=18080
JWT_SECRET=至少32字节的随机值
JWT_TTL=PT2H
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=你的服务端API密钥
LLM_MODEL=gpt-5.5
```

安全要求：

- `.env` 已被 Git 忽略，禁止强制提交；
- `LLM_API_KEY` 只能存在于后端环境，禁止写入 Kotlin、Gradle、Manifest 或 Android 资源；
- 日志、异常、测试快照和 PR 描述中不得出现真实密钥；
- 如账号无权使用默认模型，通过 `LLM_MODEL` 覆盖，不要硬编码个人账号专用模型。

启动后端：

```powershell
docker compose up -d --build
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:18080/actuator/health
docker compose ps
```

仅运行后端测试：

```powershell
cd backend
.\mvnw.cmd test
```

### 2.4 Android 配置

Android Studio 应打开 `android-app` 文件夹，而不是仓库根目录。调试版默认后端地址：

```text
http://10.0.2.2:18080/
```

`10.0.2.2` 是 Android 模拟器访问宿主机的地址。真机调试时必须改为同一局域网内可访问的电脑地址，并同步处理明文 HTTP 限制；生产版必须使用 HTTPS。

构建与测试：

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK 输出位置：

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

## 3. 当前 AI 顾问模块

### 3.1 用户流程

Android 底部有三个主选项卡：`Home / AI Advisor / Coach`。用户进入 AI Advisor 后可以：

1. 获取已持久化的历史消息；
2. 输入最多 2000 字符的问题；
3. 由后端读取用户档案、最近七天 Tracker 数据和最近对话；
4. 后端调用 OpenAI Responses API；
5. 成功后保存用户消息和 Assistant 消息；
6. Android 将新消息追加到当前对话。

当前请求是非流式的。发送期间 UI 通过 `sending` 状态禁止重复提交。

### 3.2 API

所有接口必须携带 JWT：

```text
GET  /api/ai-advisor/messages
POST /api/ai-advisor/messages
```

发送请求：

```json
{
  "content": "How can I improve my sleep consistency?"
}
```

响应：

```json
{
  "id": 1,
  "role": "ASSISTANT",
  "content": "...",
  "createdAt": "2026-06-29T08:00:00Z"
}
```

用户身份只允许来自 JWT subject。接口和服务不得接受客户端传入的 `userId`。

### 3.3 后端调用链

```text
AiAdvisorController
  -> AiAdvisorService
       -> OnboardingService：读取私有健康档案
       -> TrackerService：读取最近 7 天、最多 50 条记录
       -> AiChatSessionRepository / AiChatMessageRepository：读取最近会话
       -> AiAdvisorClient：调用 /v1/responses
       -> 保存 USER 和 ASSISTANT 消息
```

主要文件：

```text
backend/src/main/java/com/wellnessmate/advisor/api/AiAdvisorController.java
backend/src/main/java/com/wellnessmate/advisor/service/AiAdvisorService.java
backend/src/main/java/com/wellnessmate/advisor/service/AiAdvisorClient.java
backend/src/main/java/com/wellnessmate/advisor/domain/AiChatSession.java
backend/src/main/java/com/wellnessmate/advisor/domain/AiChatMessage.java
backend/src/main/java/com/wellnessmate/advisor/repository/
backend/src/test/java/com/wellnessmate/advisor/AiAdvisorClientTest.java
```

`AiAdvisorClient` 当前请求参数：

- endpoint：`POST /responses`；
- `store: false`；
- `max_output_tokens: 800`；
- 模型来自 `LLM_MODEL`；
- `instructions` 强制非诊断、非处方、紧急症状转介和不确定性表达；
- 从 Responses API 返回结构中提取首个 `output_text`。

### 3.4 数据库

AI 顾问复用 V1 创建的表：

```text
chat_sessions
chat_messages
```

`chat_messages.role` 只允许：

```text
USER / ASSISTANT / SYSTEM
```

不要将教练聊天写入上述表。教练沟通使用 V5 的 `coach_conversations` 和 `coach_messages`。

不得修改已经发布的 V1-V5 migration。任何数据库变更必须新增下一个 Flyway migration，例如 `V6__ai_advisor_sessions.sql`。JPA 配置为 `ddl-auto: validate`，数据库结构由 Flyway 管理。

### 3.5 Android 调用链

```text
AiAdvisorScreen
  -> AiAdvisorViewModel
       -> AiAdvisorRepository
            -> WellnessApi
                 -> /api/ai-advisor/messages
```

主要文件：

```text
android-app/app/src/main/java/com/wellnessmate/app/ui/advisor/AiAdvisorScreen.kt
android-app/app/src/main/java/com/wellnessmate/app/ui/AiAdvisorViewModel.kt
android-app/app/src/main/java/com/wellnessmate/app/data/Models.kt
android-app/app/src/main/java/com/wellnessmate/app/data/Repositories.kt
android-app/app/src/main/java/com/wellnessmate/app/data/WellnessApi.kt
android-app/app/src/test/java/com/wellnessmate/app/ui/AiAdvisorViewModelTest.kt
```

共享数据层文件也被 Food、Coach 和 Tracker 使用。修改 `Models.kt`、`Repositories.kt`、`WellnessApi.kt`、`AppContainer.kt` 或 `MainActivity.kt` 前，应先检查最新远程改动，避免覆盖其他模块。

## 4. AI 安全与隐私边界

以下约束不是建议，而是合并条件：

- 不得生成或展示诊断、处方、药物剂量或替代医生的结论；
- 遇到胸痛、呼吸困难、自伤等紧急风险，应建议用户联系当地急救服务；
- 输出必须说明估算和不确定性，不把模型答案包装成医学事实；
- 默认只提供完成当前问题所需的最小用户上下文；
- 当前上下文不包含 ethnicity；新增敏感字段前必须说明用途并获得明确产品决定；
- 不得把其他用户、教练或管理员的数据加入 prompt；
- 所有数据库读取都必须使用 JWT 用户 ID 约束；
- 保持 Responses API 的 `store: false`，除非产品和隐私政策明确变更；
- 不在日志中输出完整 prompt、健康档案、消息正文或 API 响应；
- AI 功能失败不得阻止用户继续使用 Tracker、Food 或 Coach 功能。

## 5. 当前已知限制

接手时不要把以下能力误认为已完成：

1. 当前只有“最近一个会话”，没有多会话创建、重命名或删除 UI；
2. 请求为普通 HTTP，尚未实现 SSE/WebSocket 流式输出和取消生成；
3. prompt 通过字符串拼接生成，Tracker context 使用 Java record 的文本表示；
4. 上下文只限制消息数量，没有精确 token budget、摘要或截断策略；
5. 没有用户级限流、内容审核、成本预算和使用量统计；
6. 没有 retry/backoff、超时配置、熔断或 provider abstraction；
7. API 调用失败时可能已创建一个空 `chat_sessions` 记录；
8. 模型成功但数据库保存失败时，没有完整的事务补偿策略；
9. Android 当前使用临时负 ID 在本地追加用户消息，重新拉取历史后才得到真实持久化 ID；
10. 自动化测试验证了 Responses API 请求/解析和 Android ViewModel，但缺少完整 AI Advisor 鉴权、持久化、越权和故障路径集成测试；
11. 仓库当前没有真实 `LLM_API_KEY`，因此真实模型响应尚未作为自动化测试执行。

建议优先级：

1. 补齐服务和 Controller 集成测试；
2. 抽出可测试的 context/prompt builder，并限制 token 预算；
3. 增加明确的超时、错误分类、限流和成本保护；
4. 再评估流式响应和多会话 UI；
5. 最后考虑工具调用、长期记忆或更复杂的 agent 流程。

不要在基础测试和安全边界不足时直接加入自主工具调用。

## 6. GitHub 合作规范

### 6.1 开始工作前

```powershell
git fetch origin
git status -sb
git rebase origin/main
```

如果工作基于尚未合并的 PR #1，应将 `origin/main` 替换为 `origin/codex/wellness-core-features`。工作区不干净时不要 rebase，不要使用 `git reset --hard` 清除他人的修改。

### 6.2 提交范围

- 一个 PR 解决一个明确问题；
- AI 后端、Android UI、大规模数据库迁移尽量拆分提交；
- 不顺手格式化或重构无关模块；
- 不提交 `.env`、密钥、APK、`build/`、`target/`、数据库卷或 `local.properties`；
- migration 一旦进入共享分支，不重写版本和内容；
- commit message 简洁描述结果，例如 `Add AI advisor context limits`。

### 6.3 PR 要求

PR 描述至少包含：

- 改了什么；
- 为什么改；
- 对 API、数据库和 Android 的影响；
- 隐私和安全影响；
- 运行过的测试；
- 是否实际调用过模型，以及使用的是 mock 还是真实凭据；
- 尚未解决的限制。

涉及共享文件时，在 PR 中明确列出，便于另一位开发者提前处理冲突。

## 7. 验证标准

提交前至少运行：

```powershell
cd backend
.\mvnw.cmd test

cd ..\android-app
.\gradlew.bat testDebugUnitTest assembleDebug

cd ..
git diff --check
```

AI 模块变更还必须覆盖：

- 无 JWT 返回 401；
- 用户只能读取自己的会话；
- 空消息和超过 2000 字符返回验证错误；
- 无 API Key 返回稳定的 503 错误；
- provider 失败和无效响应映射为稳定的 502 错误；
- 模型失败时不保存伪造 Assistant 消息；
- prompt 不包含其他用户或未经允许的敏感字段；
- Android 在 loading、empty、sending、error、success 状态下均可用；
- AI 失败不会导致应用崩溃或影响其他主选项卡。

若改动涉及数据库，请额外执行：

```powershell
docker compose up -d --build backend
docker compose ps
Invoke-RestMethod http://localhost:18080/actuator/health
```

## 8. 给接手 Codex 的执行提示

开始任务时建议遵循以下顺序：

1. 阅读本文件、`README.md`、`docs/API.md`；
2. 检查当前分支、远程 PR 和工作区状态；
3. 阅读 `advisor` 后端包及 Android AI Advisor 文件；
4. 先为目标行为增加测试；
5. 做最小范围实现；
6. 运行后端与 Android 全量测试；
7. 在 PR 中说明真实模型调用是否已验证；
8. 不得自行扩大健康数据使用范围或降低非诊断安全限制。

当需求与本文件冲突时，以用户最新明确要求为准，但必须在 PR 中记录安全、隐私或兼容性影响。
