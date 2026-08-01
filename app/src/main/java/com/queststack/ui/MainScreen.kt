package com.queststack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.queststack.ui.component.GlassNavigationBar
import com.queststack.ui.component.GlassTopAppBar
import com.queststack.ui.component.LocalGlassBackdrop
import com.queststack.ui.screen.add.AddScreen
import com.queststack.ui.screen.library.LibraryScreen
import com.queststack.ui.screen.practice.PracticeChatScreen
import com.queststack.ui.screen.practice.PracticeScreen
import com.queststack.ui.screen.settings.SettingsScreen
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 底部导航 tab 定义 */
enum class MainTab(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    Practice("practice", MiuixIcons.Home, "练题"),
    Library("library", MiuixIcons.GridView, "题库"),
    Add("add", MiuixIcons.Add, "添加"),
    Settings("settings", MiuixIcons.Settings, "设置"),
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 子路由（如答题聊天页）不显示底部导航，营造沉浸式答题体验；
    // 首次组合 currentRoute 尚为 null，视为 tab 路由避免首帧闪烁
    val isTabRoute = currentRoute?.let { route -> MainTab.entries.any { it.route == route } } ?: true

    // 采集整页内容作为玻璃顶栏 / 底栏的模糊背景层（参考 miuix example 的 CompactScreenLayout）
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val navItems = remember { MainTab.entries.map { NavigationItem(label = it.label, icon = it.icon) } }

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MiuixTheme.colorScheme.background,
            bottomBar = {
                if (isTabRoute) {
                    GlassNavigationBar(
                        selected = MainTab.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0),
                        onSelect = { index ->
                            val tab = MainTab.entries[index]
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        items = navItems,
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = MainTab.Practice.route,
                    modifier = Modifier.padding(
                        PaddingValues(
                            // 顶部不预留 inset：玻璃顶栏自行处理状态栏区域（edge-to-edge）
                            top = 0.dp,
                            // 底部预留悬浮底栏高度（含悬浮间距，避免内容被遮挡）；
                            // 子路由时 bottomBar 为空，Scaffold 仅保留系统导航栏 inset
                            bottom = padding.calculateBottomPadding(),
                            start = padding.calculateStartPadding(LocalLayoutDirection.current),
                            end = padding.calculateEndPadding(LocalLayoutDirection.current),
                        ),
                    ),
                ) {
                    composable(MainTab.Practice.route) {
                        TabPage(MainTab.Practice, topBar = {}) {
                            PracticeScreen(
                                onStart = { questionId ->
                                    navController.navigate("practice_chat/$questionId")
                                },
                            )
                        }
                    }
                    composable(MainTab.Library.route) {
                        TabPage(MainTab.Library) {
                            LibraryScreen(
                                onQuestionClick = { questionId ->
                                    navController.navigate("practice_chat/$questionId")
                                },
                            )
                        }
                    }
                    composable(MainTab.Add.route) {
                        TabPage(MainTab.Add) { AddScreen() }
                    }
                    composable(MainTab.Settings.route) {
                        TabPage(MainTab.Settings, topBar = {}) { SettingsScreen() }
                    }
                    composable(
                        route = "practice_chat/{questionId}",
                        arguments = listOf(navArgument("questionId") { type = NavType.LongType }),
                    ) { entry ->
                        val questionId = entry.arguments?.getLong("questionId") ?: 0L
                        PracticeChatScreen(
                            questionId = questionId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

/** 单个 tab 页面：玻璃顶栏 + 内容（练题 tab 顶栏由页面自绘，可传空 topBar） */
@Composable
private fun TabPage(
    tab: MainTab,
    topBar: @Composable () -> Unit = { GlassTopAppBar(title = tab.label) },
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        topBar()
        content()
    }
}
