# QuestStack v0.1 代码审查优化方案

> 审查范围：`app/src/main/java/com/queststack/` 全部源码
> 审查日期：2026-08-01
> 优先级：🔴 严重（必须修复） → 🟡 警告（应当修复） → 🟢 建议（可考虑）

---

## 🔴 严重问题

### 1. AndroidManifest.xml 缺少 INTERNET 权限

**文件**：`app/src/main/AndroidManifest.xml`

**问题**：应用通过 OkHttp 发起 AI API 请求（`AiClient`）和 WebDAV 备份操作（`WebDavClient`），但未声明网络权限。Android 系统会阻止无此权限的网络访问，导致所有网络功能运行时抛出 `SecurityException` / `SocketException`，完全不可用。

**影响范围**：AI 生成追问链、AI 优化表述、AI 格式化、WebDAV 备份/恢复——全部网络功能。

**修复方案**：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".QuestStackApp"
        ...
```

---

### 2. AI 超时配置链路完全断裂

**文件**：
- `data/DataContainer.kt` L36-L43
- `ai/AiClient.kt` L30-L33
- `ui/screen/add/AddViewModel.kt` L106

**问题**：整条配置链路断裂，用户设置无任何实际效果：

```
SettingsScreen 输入 → DataStore 持久化 → AiConfig.timeoutSeconds → ❌ 断裂 → AiClient 从未接收
```

具体表现：
1. `AiClient` 在 `DataContainer.init()` 中以默认 30s 创建，之后从不更新
2. 传入的 `OkHttpClient()` 使用默认 `readTimeout=10s`
3. 用户配置 60s → 实际 10s 后 OkHttp 抛 `SocketTimeoutException`（属于 `IOException`）
4. 错误走到"网络错误"分支，"AI 请求超时（已设置 X 秒）"提示**永远不会触发**
5. 用户被误导去"设置调整"，但调整毫无效果

**修复方案**：

```kotlin
// ===== AiClient.kt =====
// 各公开方法增加 timeoutSeconds 参数，execute 接受动态超时

class AiClient(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    ): String {
        val responseBody = execute(
            buildRequest(chatCompletionsUrl(baseUrl), apiKey, chatRequestBody(model, messages)),
            timeoutSeconds
        )
        // ... 解析逻辑不变
    }

    suspend fun generateQuestionChain(
        baseUrl: String, apiKey: String, model: String, title: String,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    ): List<Pair<String, String>> { /* 同理传入 timeoutSeconds */ }

    suspend fun optimizeAnswer(
        baseUrl: String, apiKey: String, model: String, title: String, answer: String,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    ): String { /* 同理 */ }

    suspend fun formatAnswer(
        baseUrl: String, apiKey: String, model: String, title: String, answer: String,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    ): List<Pair<String, String>> { /* 同理 */ }

    private suspend fun execute(request: Request, timeoutSeconds: Int): String =
        withTimeout(timeoutSeconds * 1000L) {
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: $responseBody")
                    }
                    responseBody
                }
            }
        }
}

// ===== DataContainer.kt =====
// OkHttpClient 的 readTimeout 应 >= 用户可配最大值，避免 OkHttp 层先超时
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)  // 覆盖用户可配最大值
    .build()

// ===== AddViewModel.kt =====
// 调用时传入用户配置的超时值
val rounds = aiClient.generateQuestionChain(
    config.baseUrl, config.apiKey, config.model, current.title,
    timeoutSeconds = config.timeoutSeconds,
)
```

---

## 🟡 警告

### 3. PracticeChatViewModel 并发竞态

**文件**：`ui/screen/practice/PracticeChatViewModel.kt` L43-L56

**问题**：`load()` 内部通过 `viewModelScope.launch` 启动新协程，`nextQuestion()`/`prevQuestion()` 在各自协程中调用 `load()`。快速连续点击时多个 `load()` 并发执行且无取消机制：

- 后发起的请求可能先完成
- 先发起的后完成时覆写 `uiState.question` 为旧题目
- `history` 已记录新 ID → "上一问"导航到错误题目

**修复方案**：

```kotlin
class PracticeChatViewModel(...) : ViewModel() {

    private var loadJob: Job? = null

    private fun load(id: Long) {
        loadJob?.cancel()  // 取消上一次未完成的加载
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val question = questionRepository.getQuestion(id)
            // 协程被取消后 isActive=false，避免过期数据写入
            if (!isActive) return@launch
            _uiState.update {
                it.copy(question = question, currentId = id, revealed = 0, loading = false)
            }
        }
    }

    // nextQuestion / prevQuestion 调用 load() 时自动取消前一次
}
```

---

### 4. 多表写操作缺少事务保护

**文件**：`data/repository/QuestionRepositoryImpl.kt` L46-L55

**问题**：
- `updateQuestion()`：删旧 rounds → 插新 rounds → 更新 question（三步无事务）
- `deleteQuestion()`：删 rounds → 删 question（两步无事务）

进程在中间步骤被杀（低内存回收、ANR 强杀）→ 题目轮次数据永久丢失。

**修复方案**：

```kotlin
// 需要注入 AppDatabase 实例以使用 withTransaction
class QuestionRepositoryImpl(
    private val database: AppDatabase,
    private val questionDao: QuestionDao,
    private val roundDao: RoundDao,
) : QuestionRepository {

    override suspend fun updateQuestion(question: Question, rounds: List<Round>) {
        database.withTransaction {
            roundDao.deleteByQuestionId(question.id)
            roundDao.insertAll(rounds)
            questionDao.update(question.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun deleteQuestion(id: Long) {
        database.withTransaction {
            roundDao.deleteByQuestionId(id)
            questionDao.deleteById(id)  // 建议新增 DAO 方法，避免构造空 Question 对象
        }
    }

    override suspend fun addQuestion(...): Long {
        return database.withTransaction {
            val questionId = questionDao.insert(question)
            roundDao.insertAll(allRounds)
            questionId
        }
    }
}

// QuestionDao 新增：
@Query("DELETE FROM questions WHERE id = :id")
suspend fun deleteById(id: Long)
```

**DataContainer 对应修改**：
```kotlin
questionRepository = QuestionRepositoryImpl(database, database.questionDao(), database.roundDao())
```

---

### 5. Room 无迁移策略

**文件**：`data/db/AppDatabase.kt` L8-L29

**问题**：
- `@Database(version = 1, exportSchema = false)` 且未配置任何迁移策略
- 未来任何实体字段变更 → version 升至 2 → 已安装用户升级后抛 `IllegalStateException` → 应用无法启动
- `exportSchema = false` 导致无法自动生成迁移所需的 schema 历史文件

**修复方案**：

```kotlin
@Database(
    entities = [Category::class, Question::class, Round::class],
    version = 1,
    exportSchema = true  // 改为 true，保留 schema 快照
)
abstract class AppDatabase : RoomDatabase() {
    // ...

    companion object {
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quest-stack.db"
                )
                    // v0.x 阶段：破坏性迁移可接受（用户数据量少）
                    // 正式版应替换为精确 Migration 对象
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
```

**build.gradle.kts 添加 schema 输出目录**：
```kotlin
android {
    defaultConfig {
        // ...
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}
```

---

### 6. API Key 和 WebDAV 密码明文存储

**文件**：`data/repository/SettingsRepository.kt` L83-L91

**问题**：`KEY_API_KEY`、`KEY_WEBDAV_PASSWORD` 以 `stringPreferencesKey` 存储于 DataStore Preferences（底层为明文 protobuf 文件）。在已 root 设备或 adb backup 场景下可被直接读取。

**修复方案（轻量级）**：

```kotlin
// 1. 添加依赖
// implementation("androidx.security:security-crypto:1.1.0-alpha06")

// 2. 创建加密工具
object SecureStorage {
    private lateinit var masterKey: MasterKey

    fun init(context: Context) {
        masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray())
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, 12)
        val encrypted = decoded.copyOfRange(12, decoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }
}

// 3. SettingsRepository 中对敏感字段加解密
val aiConfig: Flow<AiConfig> = dataStore.data.map { prefs ->
    AiConfig(
        baseUrl = prefs[KEY_BASE_URL] ?: "",
        apiKey = SecureStorage.decrypt(prefs[KEY_API_KEY] ?: ""),
        model = prefs[KEY_MODEL] ?: "",
        timeoutSeconds = prefs[KEY_TIMEOUT_SECONDS] ?: 30,
    )
}

suspend fun setAiConfig(config: AiConfig) {
    dataStore.edit { prefs ->
        prefs[KEY_BASE_URL] = config.baseUrl
        prefs[KEY_API_KEY] = SecureStorage.encrypt(config.apiKey)
        // ...
    }
}
```

---

### 7. 备份导入缺少版本校验和前向兼容

**文件**：`data/backup/BackupRepository.kt` L23-L26, L55-L62

**问题**：
- `BackupFile.version` 导出时写入 `1`，但 `importFromJson()` 完全不校验
- `Json` 配置未设置 `ignoreUnknownKeys = true`
- v2 备份新增字段 → 反序列化直接抛异常 → 用户看到"格式不正确"，无法区分损坏还是版本不兼容

**修复方案**：

```kotlin
class BackupRepository(...) {

    companion object {
        const val CURRENT_BACKUP_VERSION = 1
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true  // 容忍高版本新增字段
    }

    suspend fun importFromJson(jsonText: String): Int {
        val backupFile = try {
            json.decodeFromString(BackupFile.serializer(), jsonText)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("备份文件格式不正确")
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("备份文件格式不正确")
        }

        // 版本校验
        if (backupFile.version > CURRENT_BACKUP_VERSION) {
            throw IllegalArgumentException(
                "备份文件版本过高（v${backupFile.version}），当前应用支持 v$CURRENT_BACKUP_VERSION，请升级应用后重试"
            )
        }

        // ... 后续导入逻辑不变
    }
}
```

---

### 8. 实体间无外键约束

**文件**：
- `data/db/Question.kt` L7-L18
- `data/db/Round.kt` L7-L18

**问题**：`Question.categoryId` 和 `Round.questionId` 均未声明 `@ForeignKey`。数据完整性完全依赖 Repository 应用逻辑。`BackupRepository` 已直接操作 DAO 层，未来新增的数据操作路径若绕过 Repository，将产生孤儿记录。

**修复方案**（需配合数据库迁移，建议在 v0.2 中实施）：

```kotlin
// Round.kt
@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE  // 删除题目时自动删除轮次
        )
    ],
    indices = [Index("questionId")]
)
data class Round(...)

// Question.kt
@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL  // 删除分类时题目的 categoryId 置空
        )
    ],
    indices = [Index("categoryId"), Index("difficulty")]
)
data class Question(...)
```

> ⚠️ 注意：添加外键属于 schema 变更，需要将 version 升至 2 并提供 Migration 对象（或依赖 `fallbackToDestructiveMigration`）。

---

## 🟢 建议

### 9. 非结构化 CoroutineScope 无异常处理

**文件**：
- `QuestStackApp.kt` L15-L17
- `data/DataContainer.kt` L51-L53

**问题**：`CoroutineScope(Dispatchers.IO).launch { ... }` 无 `CoroutineExceptionHandler`、无 `SupervisorJob`。若 Room 操作抛异常（数据库文件损坏），异常传递到 `uncaughtExceptionHandler`，直接导致应用崩溃。

**修复方案**：

```kotlin
// QuestStackApp.kt
class QuestStackApp : Application() {

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            android.util.Log.e("QuestStack", "Background task failed", e)
        }
    )

    override fun onCreate() {
        super.onCreate()
        DataContainer.init(this)
        appScope.launch {
            Seed.seedCategories(DataContainer.database.categoryDao())
        }
    }
}

// DataContainer.kt 同理：
private val scope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        android.util.Log.e("DataContainer", "Settings collect failed", e)
    }
)

fun init(context: Context) {
    // ...
    scope.launch {
        settingsRepository.themeMode.collect { AppSettings.themeMode = it }
    }
}
```

---

### 10. 题库页"编辑"按钮为空操作占位符

**文件**：`ui/screen/library/LibraryScreen.kt` L319-L327

**问题**：`QuestionCard` 中渲染了编辑图标按钮，`onClick` 为空 lambda。用户可见但点击无响应。

**修复方案**（二选一）：

**方案 A：暂时隐藏（推荐，若编辑功能不在本版本）**
```kotlin
// 移除或注释掉编辑按钮，保留 TODO 注释
// TODO: 下一里程碑接入编辑页
// IconButton(onClick = { onEdit(question.id) }) { ... }
```

**方案 B：实现编辑跳转**
```kotlin
// LibraryScreen 参数增加 onEdit 回调
IconButton(onClick = { onEdit(question.id) }) { ... }

// MainScreen 中导航到添加页并预填数据
composable("edit/{questionId}") { entry ->
    val questionId = entry.arguments?.getLong("questionId") ?: 0L
    AddScreen(editQuestionId = questionId)
}
```

---

### 11. 死代码清理

**文件**：
- `data/db/QuestionDao.kt` L34-L35：`count()` 无调用方
- `data/backup/BackupRepository.kt` L122：`exportFileSizeHint()` 无调用方
- `ui/screen/practice/PracticeChatViewModel.kt` L33：`categoryRepository` 参数未使用

**修复方案**：

```kotlin
// QuestionDao.kt - 移除未使用方法（若为预留则添加注释）
// @Query("SELECT COUNT(*) FROM questions")
// suspend fun count(): Int

// BackupRepository.kt - 移除
// suspend fun exportFileSizeHint(): Int = exportToJson().toByteArray().size

// PracticeChatViewModel.kt - 移除未使用参数
class PracticeChatViewModel(
    questionId: Long,
    private val questionRepository: QuestionRepository = DataContainer.questionRepository,
    // 移除: categoryRepository: CategoryRepository = DataContainer.categoryRepository,
) : ViewModel() { ... }
```

---

## 修复优先级排序

| 优先级 | 编号 | 问题 | 预估工作量 |
|--------|------|------|-----------|
| P0 | #1 | INTERNET 权限缺失 | 1 分钟 |
| P0 | #2 | AI 超时配置断裂 | 30 分钟 |
| P1 | #3 | 并发竞态 | 15 分钟 |
| P1 | #4 | 事务保护 | 20 分钟 |
| P1 | #5 | 数据库迁移策略 | 15 分钟 |
| P2 | #6 | 敏感信息加密 | 1 小时 |
| P2 | #7 | 备份版本兼容 | 15 分钟 |
| P2 | #8 | 外键约束 | 30 分钟（含迁移） |
| P3 | #9 | 协程异常处理 | 10 分钟 |
| P3 | #10 | 编辑按钮占位 | 5 分钟 |
| P3 | #11 | 死代码清理 | 5 分钟 |

---

## 附：架构层面长期建议

1. **依赖注入**：当前 `DataContainer` 全局单例 + ViewModel 构造器默认参数的方式不利于测试。建议引入 Hilt 或至少改为工厂模式，方便单元测试 mock。

2. **数据库版本管理**：从 v0.2 开始，每次 schema 变更应编写精确 `Migration` 对象，并将 `schemas/` 目录纳入版本控制。

3. **错误上报**：建议接入 Crashlytics 或类似服务，捕获非结构化协程中的异常，避免静默失败。

4. **ProGuard 规则**：`kotlinx.serialization` 的 `@Serializable` 类需要 keep 规则，当前 `proguard-rules.pro` 应确认包含：
   ```
   -keepattributes *Annotation*, InnerClasses
   -dontnote kotlinx.serialization.AnnotationsKt
   -keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
   -keep,includedescriptorclasses class com.queststack.**$$serializer { *; }
   -keepclassmembers class com.queststack.** { *** Companion; }
   -keepclasseswithmembers class com.queststack.** { kotlinx.serialization.KSerializer serializer(...); }
   ```

5. **单元测试覆盖**：`TextStandardizer`、`BackupRepository`（导入/导出逻辑）、`AiClient.parseRounds` 为纯逻辑代码，应优先补充单元测试。
