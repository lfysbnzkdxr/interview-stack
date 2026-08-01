# Interview Stack（面试刷题助手）

基于 Jetpack Compose 的 Android 面试刷题应用：内置题库管理、卡牌式练习、AI 辅助润色与云端备份，帮助求职者高效准备技术面试。

## 功能特性

- **卡牌练习**：三按钮导航（上一题 / 翻转显示答案 / 下一题）+ 横向滑动切题，Fisher-Yates 洗牌 + 队列自动补充，支持按分类/难度筛选
- **题库管理**：Paging 3 分页浏览，分类 Tab 计数切换、搜索、难度筛选、「仅看可见题」开关、批量删除/隐藏/移动分类
- **题目创建**：手动录入题目，支持 Markdown 格式答案，AI 一键生成问答
- **AI 辅助**：接入 OpenAI 兼容接口（预设 DeepSeek、GLM、Kimi、通义千问、MiMo 五个提供商），支持问答优化、对话润色、追加子问题，含 HTTP 5xx/429 自动重试
- **备份与恢复**：WebDAV 云端备份（自动备份 + 手动备份，保留最近 5 份）、SAF 文件导入导出（JSON，支持覆盖/合并两种模式）
- **安全存储**：API Key 与 WebDAV 密码使用 EncryptedSharedPreferences 加密存储
- **种子数据**：首次启动内置题库，支持一键重置恢复默认

## 技术栈

| 类别 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3（HyperOS/MIUI 风格：小米蓝主题、大圆角卡片、胶囊控件、深浅色） |
| 架构 | MVVM + Repository，单向数据流（StateFlow） |
| 依赖注入 | Hilt |
| 本地存储 | Room（SQLite）+ EncryptedSharedPreferences |
| 异步 | Kotlin Coroutines + Flow |
| 分页 | Paging 3 |
| 后台任务 | WorkManager（退出时自动备份） |
| 网络 | OkHttp + kotlinx.serialization |
| 测试 | JUnit 5 + MockK + MockWebServer |

## 环境要求

- JDK 17
- Android SDK（compileSdk 35，minSdk 26）
- Gradle 8.7（使用项目内 wrapper，无需单独安装）

## 构建与测试

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 运行单元测试（94 个，含 LLM / WebDAV / 仓库 / ViewModel 层）
./gradlew testDebugUnitTest

# Lint 静态检查
./gradlew lintDebug

# Compose UI 测试（8 个，需真机/模拟器；真机建议 adb 直装后 am instrument 运行）
./gradlew connectedDebugAndroidTest
```

Debug APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 持续集成

GitHub Actions 在每次 push / PR 时自动运行单元测试、Lint、Debug 与 Release 构建，并上传 Lint 报告与 APK 产物（CI 无签名配置时 Release 构建自动回退 debug 签名）。

## 项目结构

```
app/src/main/java/com/example/interviewhelper/
├── ui/                  # Compose UI 层（MVVM 的 View）
│   ├── navigation/      # 底部导航（练题/创建/题库/设置）
│   ├── common/          # 共享组件（PageHeader/FilterBar/Tags 等）
│   ├── practice/        # 卡牌练习
│   ├── create/          # 题目创建与 AI 生成
│   ├── bank/            # 题库管理（Paging + 批量操作 + AI 润色）
│   └── settings/        # 设置（备份/导入导出/API 配置）
├── data/
│   ├── local/           # Room 实体、DAO、种子数据
│   ├── repository/      # 仓库层（数据源统一出口）
│   └── remote/          # LLM 服务、WebDAV 客户端
├── di/                  # Hilt 依赖注入模块
├── worker/              # WorkManager 后台任务
└── domain/              # 领域层（预留扩展接口）
```

## 使用说明

1. 安装后首次启动自动载入内置题库
2. 在「设置 - AI 配置」填入任一提供商（DeepSeek/GLM/Kimi/通义千问/MiMo）的 API Key 即可使用 AI 功能
3. 在「设置 - WebDAV」配置服务器地址与账号密码后，支持自动/手动云端备份

## 依赖升级路线（待办）

当前依赖无未修复的严重 CVE，暂不升级。后续升级顺序建议：

1. 低风险独立项（可随时单独升）：security-crypto 1.1.0（修复 alpha 竞态 bug）、junit5 5.14.4、mockk 1.14.11、workManager 2.11.2、paging 3.5.0
2. 工具链主线：compileSdk 35→36 → Gradle 8.7→8.13 → AGP 8.5.2→8.13.2（AGP 8.5.2 已不支持 compileSdk 35）
3. 成组迁移：Kotlin 2.0→2.4 + KSP 2.3.10（KSP1→KSP2）+ Hilt 2.60.1 + Room 2.8.4（Room 2.8 起 minSdk 升为 23）
4. 其余 AndroidX 与 Compose BOM 跟进（Compose 1.9+ 需 compileSdk 36）
5. okhttp 4.12→5.4（含少量破坏性变更，需回归网络层）；security-crypto 长期建议迁移到 Android Keystore
6. AGP 9.x 待评估（内置 Kotlin、移除 kotlin-android 插件，行为变更较大）
