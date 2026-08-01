package com.example.interviewhelper

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 底部导航与题库页（合并浏览页后）的端到端 UI 测试。
 * 使用真实 Application（Hilt + 种子数据），覆盖核心导航与题库浏览交互。
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 底部导航显示四个页面() {
        composeRule.onNodeWithText("练题").assertIsDisplayed()
        composeRule.onNodeWithText("创建").assertIsDisplayed()
        composeRule.onNodeWithText("题库").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun 切换到题库页显示统计与筛选控件() {
        composeRule.onNodeWithText("题库").performClick()

        composeRule.onNodeWithText("共", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("仅看可见").assertIsDisplayed()
        composeRule.onNodeWithText("搜索题目...").assertIsDisplayed()
    }

    @Test
    fun 题库页分类Tab显示计数() {
        composeRule.onNodeWithText("题库").performClick()

        composeRule.onNodeWithText("全部", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Agent 智能体", substring = true).assertIsDisplayed()
    }

    @Test
    fun 搜索定位并展开题目查看答案() {
        composeRule.onNodeWithText("题库").performClick()

        composeRule.onNodeWithText("搜索题目...").performTextInput("AI Agent")
        composeRule.onNodeWithText("什么是 AI Agent？它与传统聊天机器人有什么区别？")
            .performClick()

        // 展开后显示答案内容（Markdown 渲染）
        composeRule.onNodeWithText("自主性", substring = true).assertIsDisplayed()
    }

    @Test
    fun 仅看可见开关可切换() {
        composeRule.onNodeWithText("题库").performClick()

        composeRule.onNodeWithTag("visibleOnlySwitch").performClick()
        composeRule.onNodeWithTag("visibleOnlySwitch").assertIsDisplayed()
        composeRule.onNodeWithText("共", substring = true).assertIsDisplayed()
    }
}
