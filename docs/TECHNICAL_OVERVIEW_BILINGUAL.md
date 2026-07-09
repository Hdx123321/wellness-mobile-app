# Wellness Mobile App 技术功能概览 / Technical Feature Overview

## 中文版

### 1. Android 移动端界面

项目移动端使用 Kotlin 与 Jetpack Compose 实现。通过声明式 UI 构建 Home、Tracker、Food、AI Adviser、Training Plan 和 Coach Chat 等页面，使界面迭代更快，并能在不同页面间共享统一的状态管理方式。

### 2. 用户认证与账户体系

后端使用 Spring Boot Security 与 JWT 实现登录、注册和接口鉴权，Android 端通过 Retrofit 请求后端并在本地保存 token。该机制保证用户登录后只能访问自己的受保护数据，并支持普通用户与教练用户的角色区分。

### 3. 首次问卷与健康画像

首次登录问卷由后端维护问题和选项，Android 端以表单方式采集身高、体重、年龄、目标、活动习惯和核心需求等信息。问卷结果会形成用户基础健康画像，为后续健康档案、AI Adviser 和个性化建议提供数据基础。

### 4. Tracker 健康数据记录

Tracker 模块使用 Spring Boot REST API、MySQL 和 Android ViewModel 实现数据记录、编辑和按日期查看。Food、Weight、Workout、Sleep 等记录会持久化到数据库，并在客户端按日历和图表方式展示，帮助用户理解长期变化。

### 5. Food Tracker 与营养计算

Food Tracker 使用后端内置食物数据库和营养字段，Android 端提供食物分类、搜索、份量选择和四餐记录界面。用户选择食物后系统自动计算 calories、carbs、protein、fat 和 fiber，减少手动输入成本。

### 6. 拍照识别食物

拍照识别使用 Android Camera / 相册能力采集图片，并通过后端调用大模型进行食物识别和营养估算。识别结果会进入确认流程，用户确认后再写入 Food Tracker，避免 AI 估算直接污染正式记录。模型接入火山引擎 Doubao Ark 系列模型。

### 7. Weight / Sleep / Workout 可视化

客户端使用 Jetpack Compose 自定义绘制折线图、柱状图和进度条。Weight 展示体重趋势，Sleep 展示近 7 日睡眠情况，Workout 展示本周运动天数和消耗，使用户不用阅读原始记录也能快速理解状态。

### 8. AI Adviser

AI Adviser 由 Android 聊天界面、Spring Boot AI 服务、大模型调用和 tool calling 组成。它可以读取用户画像和 tracker 数据，生成健康建议，并在必要时通过工具调用查询或更新记录。
AI 调用层使用 OpenAI-compatible Chat Completions API 形式，支持通过环境变量切换模型和 base URL

### 9. RAG 用户数据检索

后端包含 RAG 检索服务，将用户历史健康数据整理为可检索上下文。AI Adviser 在回答长期趋势、习惯变化和健康建议类问题时，可以结合用户历史数据，而不是只依赖当前对话文本。

### 10. Coach Chat 实时沟通

教练聊天功能使用 Spring Boot REST API、MySQL 消息表和 Android 定时轮询实现近实时通信。客户端区分用户端和教练端视图，并通过未读消息计数提示新消息，同时后端校验会话参与者，避免越权读取。

### 11. Training Plan 训练计划

训练计划模块使用后端计划、订阅、打卡和 workout block 数据结构实现。教练可以发布包含文字、图片和视频的训练计划，用户可以浏览、订阅、打卡并联系教练。

### 12. 云端部署与 APK 分发

后端使用 Docker Compose 部署到云服务器，配合 MySQL 和反向代理提供公网 API。Android 端通过 Gradle 注入 `API_BASE_URL` 打包 APK，使安装包可以直接连接云端服务进行测试。
当前云端测试环境部署在 DigitalOcean Droplet 上，并通过域名 `api.hdx-lab.org` 对外提供后端 API。

---

## English Version

### 1. Android Mobile UI

The mobile client is built with Kotlin and Jetpack Compose. Declarative UI is used to implement Home, Tracker, Food, AI Adviser, Training Plan, and Coach Chat screens, enabling faster UI iteration and consistent state handling across screens.

### 2. Authentication and Account System

The backend uses Spring Boot Security and JWT for login, registration, and protected API access, while the Android app communicates through Retrofit and stores the token locally. This ensures authenticated users can access only protected resources and supports role separation between clients and coaches.

### 3. Onboarding and Health Profile

The onboarding questionnaire is managed by the backend and rendered as forms on Android. It collects height, weight, age, goals, activity habits, and user needs, creating a basic health profile for later profile pages, AI advice, and personalization.

### 4. Health Tracker Data Recording

The tracker module is implemented with Spring Boot REST APIs, MySQL persistence, and Android ViewModels. Food, Weight, Workout, Sleep, and other records are stored in the database and displayed by date and charts on the client, helping users understand long-term changes.

### 5. Food Tracker and Nutrition Calculation

Food Tracker uses a backend food catalog with built-in nutrition fields, while Android provides category browsing, search, serving selection, and meal-based logging. After users select foods, the system automatically calculates calories, carbs, protein, fat, and fiber, reducing manual input.

### 6. Food Photo Recognition

Food photo recognition uses Android camera/gallery input and backend large-model analysis to estimate food items and nutrition. The result goes through a user confirmation step before being saved to Food Tracker, preventing unverified AI estimates from directly entering official records.The food recognition flow is configured to support Volcengine Doubao Ark models.

### 7. Weight / Sleep / Workout Visualization

The Android client uses custom Jetpack Compose charts, including line charts, bar charts, and progress indicators. Weight shows trend changes, Sleep shows the last 7 days, and Workout summarizes weekly activity and estimated calories, allowing users to understand status without reading raw logs.

### 8. AI Adviser

AI Adviser combines an Android chat UI, Spring Boot AI services, large-model calls, and tool calling. It can read user profiles and tracker data, generate health suggestions, and use tools to query or update records when appropriate.
The AI integration uses an OpenAI-compatible Chat Completions API style, with model names and base URLs configurable through environment variables. 

### 9. RAG User Data Retrieval

The backend includes a RAG retrieval service that converts historical health data into retrievable context. When answering questions about long-term trends, habit changes, or health suggestions, AI Adviser can use historical user data instead of relying only on the current conversation.

### 10. Coach Chat

Coach Chat is implemented with Spring Boot REST APIs, MySQL message storage, and Android polling for near-real-time updates. The app separates client and coach views, shows unread message indicators, and the backend verifies conversation participants to prevent unauthorized access.

### 11. Training Plans

The training plan module uses backend entities for plans, subscriptions, check-ins, and workout blocks. Coaches can publish plans with text, images, and videos, while users can browse, subscribe, check in, and contact the coach.

### 12. Cloud Deployment and APK Delivery

The backend is deployed to a cloud server using Docker Compose with MySQL and a reverse proxy. The Android APK is built through Gradle with an injected `API_BASE_URL`, allowing the installed app to connect directly to the cloud API for testing.
The current cloud testing environment runs on a DigitalOcean Droplet and exposes the backend through `api.hdx-lab.org`.
