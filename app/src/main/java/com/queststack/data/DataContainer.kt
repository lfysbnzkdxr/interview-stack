package com.queststack.data

import android.content.Context
import com.queststack.ai.AiClient
import com.queststack.data.backup.BackupRepository
import com.queststack.data.backup.WebDavClient
import com.queststack.data.db.AppDatabase
import com.queststack.data.repository.CategoryRepository
import com.queststack.data.repository.CategoryRepositoryImpl
import com.queststack.data.repository.QuestionRepository
import com.queststack.data.repository.QuestionRepositoryImpl
import com.queststack.data.repository.SettingsRepository
import com.queststack.ui.theme.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object DataContainer {
    lateinit var database: AppDatabase
        private set
    lateinit var questionRepository: QuestionRepository
        private set
    lateinit var categoryRepository: CategoryRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var aiClient: AiClient
        private set
    lateinit var backupRepository: BackupRepository
        private set
    lateinit var webDavClient: WebDavClient
        private set

    private val okHttpClient = OkHttpClient()

    fun init(context: Context) {
        database = AppDatabase.getInstance(context)
        questionRepository = QuestionRepositoryImpl(database.questionDao(), database.roundDao())
        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        settingsRepository = SettingsRepository(context)
        aiClient = AiClient(okHttpClient)
        backupRepository = BackupRepository(database.questionDao(), database.categoryDao(), database.roundDao())
        webDavClient = WebDavClient(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        )
        CoroutineScope(Dispatchers.IO).launch {
            settingsRepository.themeMode.collect { AppSettings.themeMode = it }
        }
    }
}
