package com.example.interviewhelper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "interview_helper.db"
    }
}
