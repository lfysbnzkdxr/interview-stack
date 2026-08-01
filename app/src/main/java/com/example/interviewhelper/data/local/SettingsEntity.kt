package com.example.interviewhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 设置实体 - 键值对存储，value 为 JSON 字符串
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
