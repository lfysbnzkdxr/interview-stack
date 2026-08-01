package com.queststack.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 主题模式：跟随系统 / 浅色 / 深色 */
enum class ThemeMode { System, Light, Dark }

/** 全局轻量状态（后续接入 DataStore 持久化） */
object AppSettings {
    var themeMode by mutableStateOf(ThemeMode.System)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val mode = AppSettings.themeMode
    val controller = remember(mode) {
        ThemeController(
            colorSchemeMode = when (mode) {
                ThemeMode.System -> ColorSchemeMode.System
                ThemeMode.Light -> ColorSchemeMode.Light
                ThemeMode.Dark -> ColorSchemeMode.Dark
            }
        )
    }
    MiuixTheme(controller = controller) {
        content()
    }
}
