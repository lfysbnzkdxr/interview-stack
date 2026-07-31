package com.example.interviewhelper.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BrowseGallery
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Practice : Screen(
        route = "practice",
        title = "练题",
        selectedIcon = Icons.Filled.PlayCircle,
        unselectedIcon = Icons.Outlined.PlayCircleOutline
    )

    data object Browse : Screen(
        route = "browse",
        title = "浏览",
        selectedIcon = Icons.Filled.BrowseGallery,
        unselectedIcon = Icons.Outlined.BrowseGallery
    )

    data object Create : Screen(
        route = "create",
        title = "创建",
        selectedIcon = Icons.Filled.Add,
        unselectedIcon = Icons.Outlined.AddCircleOutline
    )

    data object Bank : Screen(
        route = "bank",
        title = "题库",
        selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
        unselectedIcon = Icons.AutoMirrored.Outlined.LibraryBooks
    )

    data object Settings : Screen(
        route = "settings",
        title = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems = listOf(Practice, Browse, Create, Bank, Settings)
    }
}
