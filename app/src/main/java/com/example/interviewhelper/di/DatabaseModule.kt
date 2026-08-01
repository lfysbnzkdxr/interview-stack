package com.example.interviewhelper.di

import android.content.Context
import androidx.room.Room
import com.example.interviewhelper.data.local.AppDatabase
import com.example.interviewhelper.data.local.QuestionDao
import com.example.interviewhelper.data.local.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideQuestionDao(database: AppDatabase): QuestionDao {
        return database.questionDao()
    }

    @Provides
    fun provideSettingsDao(database: AppDatabase): SettingsDao {
        return database.settingsDao()
    }
}
