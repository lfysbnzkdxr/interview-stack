package com.example.interviewhelper

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 练题页（卡牌练习）的端到端 UI 测试。
 * 交互设计：← / 翻转显示答案 / → 三按钮导航。
 */
@RunWith(AndroidJUnit4::class)
class PracticeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** 正面显示难度标签（初级/中级/高级），背面不显示——用于断言正反面切换 */
    private fun ComposeTestRule.hasDifficultyTag(): Boolean =
        onAllNodesWithText("初级").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("中级").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("高级").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun 练题页显示卡片与导航按钮() {
        // 默认启动页为练题
        composeRule.onNodeWithTag("flashCard").assertIsDisplayed()
        composeRule.onNodeWithText("翻转显示答案").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上一题").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下一题").assertIsDisplayed()
    }

    @Test
    fun 点击翻转按钮切换正反面() {
        // 正面显示难度标签
        assertTrue(composeRule.hasDifficultyTag())

        composeRule.onNodeWithText("翻转显示答案").performClick()

        // 背面不显示难度标签（显示答案内容）
        assertFalse(composeRule.hasDifficultyTag())

        // 再次点击翻回正面
        composeRule.onNodeWithText("翻转显示答案").performClick()
        assertTrue(composeRule.hasDifficultyTag())
    }

    @Test
    fun 下一题按钮切换题目() {
        composeRule.onNodeWithContentDescription("下一题").performClick()

        // 切题后仍显示卡片与按钮（第一题时非最后一题，下一题可用）
        composeRule.onNodeWithTag("flashCard").assertIsDisplayed()
        composeRule.onNodeWithText("翻转显示答案").assertIsDisplayed()
    }
}
