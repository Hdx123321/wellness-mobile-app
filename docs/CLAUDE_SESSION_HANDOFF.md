# Claude Session Handoff

本文用于将当前 WellnessMate AI 项目会话交接给 Claude。开始工作前，请先阅读本文、`README.md`、`docs/API.md` 和 `docs/AI_ADVISOR_COLLABORATION.md`，并以仓库中的实际代码、Git 状态和测试结果为准。

## 1. 项目与仓库

- GitHub：`https://github.com/Hdx123321/wellness-mobile-app`
- 后端：Java 21、Spring Boot、JPA、Flyway、MySQL
- Android：Kotlin、Jetpack Compose、Retrofit、CameraX
- 部署方式：Docker Compose 启动 MySQL 和后端
- 最低 Android 版本：API 26

项目采用模块化单体，不需要立即改成微服务。当前模块包括：

```text
backend/src/main/java/com/wellnessmate/
├── auth
├── onboarding
├── tracker
├── food
├── advisor
├── chat
├── common
└── config
```

所有文档中的路径都相对于仓库根目录。不要依赖任何人的本地绝对路径。

## 2. Git 当前状态

以下 PR 已合并到 `main`：

- PR #1：核心健康记录、Food、Coach、AI Advisor 等基础流程；
- PR #2：Food 餐次、过去日期编辑、每日唯一 Weight、协作文档。

PR #2：`https://github.com/Hdx123321/wellness-mobile-app/pull/2`

关键提交：

```text
250c48d Add meal tracking and daily weight constraints
70e3c32 Add AI advisor collaboration guide
52fd9fb Complete wellness tracking and advisor flows
```

交接时远程 `main` 最新提交为：

```text
de18221 Merge pull request #2 from Hdx123321/codex/wellness-core-features
```

接手后不要继续在已合并的 `codex/wellness-core-features` 上开发。应执行：

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c codex/<task-name>
```

创建新分支前先执行 `git status -sb`。不要使用 `git reset --hard` 清理不属于自己的修改。

## 3. 当前已完成能力

### 用户与健康信息

- 注册、登录、JWT 鉴权和登出；
- 新用户首次问卷；
- 用户健康档案；
- 身高、体重、BMI、基础代谢、燃脂心率估算；
- 目标体重计划及进度；
- 用户管理、提醒设置。

### Tracker

内置类型：

```text
FOOD / WEIGHT / WORKOUT / STEPS / SLEEP / WATER
```

- 全局日期选择；
- 按日显示数据；
- 七日趋势图；
- 支持过去日期的添加、修改和删除；
- `WEIGHT` 每个 UTC 日期最多一条；再次创建会更新已有记录；
- V7 migration 会清理历史重复 Weight，并回填唯一日期字段。

### Food

- 内置食物目录；
- 后端根据克数计算热量、蛋白质、碳水、脂肪和纤维；
- Food 页面分为 `BREAKFAST`、`LUNCH`、`DINNER`、`SNACK`；
- 每个餐次有添加食物入口；
- 独立食物选择页面支持切换日期和餐次；
- 选择页面隐藏全局用户头像和日历栏；
- CameraX 拍照并调用后端 AI 识别；
- 用户确认后才保存识别结果；
- 拍照确认会保留对应日期和餐次。

### 沟通

- 用户与教练聊天，当前使用轮询；
- Android 底部导航为 `Home / AI Advisor / Coach`；
- AI Advisor 会读取用户档案、最近七天 Tracker 和近期对话。

## 4. 最近一次 Tracker 改动

数据库迁移：

```text
backend/src/main/resources/db/migration/V6__meal_types_and_daily_weight.sql
backend/src/main/resources/db/migration/V7__backfill_daily_weight_dates.sql
```

主要规则：

- Food entry 必须包含 `mealType`；
- 可选值只能是 `BREAKFAST`、`LUNCH`、`DINNER`、`SNACK`；
- 已有 Food entry 迁移为 `SNACK`；
- Weight 使用 `tracking_date` 和唯一索引保证每日唯一；
- 重复创建 Weight 时，`TrackerService`更新已有记录并返回相同 ID。

Food 创建请求示例：

```json
{
  "recordedAt": "2026-06-29T04:00:00Z",
  "mealType": "LUNCH",
  "items": [
    {
      "foodId": 1,
      "grams": 150
    }
  ],
  "notes": null
}
```

注意：`POST /api/food/entries/analyzed` 同样要求 `mealType`。调整拍照识别时不能丢失用户选择的日期和餐次。

## 5. AI 与拍照识别的协作边界

AI Advisor 和食物拍照识别主要交由另一位协作者负责。Claude 修改以下内容前必须先确认并同步：

- `backend/src/main/java/com/wellnessmate/advisor/**`；
- `backend/src/main/java/com/wellnessmate/food/service/FoodImageAnalyzer.java`；
- `android-app/app/src/main/java/com/wellnessmate/app/ui/advisor/**`；
- `AiAdvisorViewModel.kt`；
- Food 拍照、分析结果和确认保存流程；
- `LLM_API_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`；
- Responses API 请求或响应格式。

以下共享文件冲突风险较高：

```text
android-app/app/src/main/java/com/wellnessmate/app/data/Models.kt
android-app/app/src/main/java/com/wellnessmate/app/data/WellnessApi.kt
android-app/app/src/main/java/com/wellnessmate/app/data/Repositories.kt
android-app/app/src/main/java/com/wellnessmate/app/data/AppContainer.kt
android-app/app/src/main/java/com/wellnessmate/app/MainActivity.kt
android-app/app/src/main/java/com/wellnessmate/app/ui/tracker/TrackerScreens.kt
backend/src/main/resources/application.yml
docker-compose.yml
.env.example
```

修改共享 API、DTO、导航、数据库 migration 或 AI 环境变量时，应提前说明：

1. 将修改哪些文件；
2. API 或数据库结构如何变化；
3. 是否存在兼容性影响；
4. 预计合并顺序。

## 6. RAG 与架构方向

当前决定：暂不拆微服务，先保持模块化单体。

如果继续实现 RAG：

- Profile、体重、步数、睡眠等结构化数据继续以 MySQL 为事实来源；
- 向量库只保存适合语义检索的对话摘要、长期偏好、用户明确要求记住的内容和健康知识文档；
- 向量记录必须包含 `userId`，所有检索必须强制按用户过滤；
- 用户删除原始数据时必须同步删除向量；
- 不允许把向量库作为用户健康数据的唯一来源；
- 先抽出 `LlmClient`、`ContextAssembler` 和多个 Context Provider，再增加 RAG；
- 验证效果、成本、隔离和删除能力后，再考虑拆出独立 AI 服务。

## 7. 环境与运行

创建本地配置：

```powershell
Copy-Item .env.example .env
```

启动后端：

```powershell
docker compose up -d --build
Invoke-RestMethod http://localhost:18080/actuator/health
```

Android Studio 应打开 `android-app`。模拟器访问宿主机后端使用：

```text
http://10.0.2.2:18080/
```

真实 `LLM_API_KEY` 只能放在后端环境变量中，禁止写入 Android、Gradle、Manifest、代码、日志或提交记录。

## 8. 已完成验证

最近一次完整验证结果：

- 后端测试：17 个通过；
- Android 单元测试通过；
- Android Debug APK 构建通过；
- V6、V7 在 H2 测试环境通过；
- V6、V7 在 MySQL 8.4 实际应用成功；
- 后端健康检查返回 `UP`；
- Android 模拟器验证了 Food 四餐布局、食物选择页和隐藏全局顶栏；
- API smoke test 验证同日两次提交 Weight 返回相同 ID，最终只保存新数值；
- API smoke test验证 Food entry 正确保存并返回 `mealType`。

重新验证命令：

```powershell
cd backend
.\mvnw.cmd test

cd ..\android-app
.\gradlew.bat testDebugUnitTest assembleDebug

cd ..
git diff --check
```

## 9. 开发约束

- 不修改已经共享的 V1-V7 migration；数据库变化只能新增 V8 或更高版本；
- 一个 PR 只处理一个清晰目标；
- 不顺手重构或格式化无关模块；
- 不提交 `.env`、密钥、APK、`build/`、`target/`、数据库卷或 `local.properties`；
- 后端只接受 JWT 中的用户 ID，不接受客户端指定 `userId`；
- 健康建议必须保持非诊断、非处方和紧急风险转介边界；
- AI 故障不能阻止 Tracker、Food 或 Coach 的使用；
- 所有用户数据查询必须进行用户隔离；
- 修改前先加或调整可验证测试，完成后运行后端和 Android 检查。

## 10. 建议的接手顺序

1. 更新本地 `main` 并创建新的 `codex/` 分支；
2. 阅读交接文档和 API 文档；
3. 确认本次任务是否涉及 AI/拍照识别共享边界；
4. 检查相关 migration、DTO、Repository、ViewModel 和导航；
5. 先定义成功标准和测试；
6. 做最小范围修改；
7. 运行后端测试、Android 测试和构建；
8. 使用真实 MySQL 验证新增 migration；
9. 在 PR 描述中记录 API、数据库、隐私、安全和协作影响。

若本文与用户最新明确要求冲突，以用户最新要求为准，但不能静默降低数据隔离、安全或医疗建议边界。
