package com.example.interviewhelper

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 练题页（卡牌练习）的端到端 UI 测试。
 */
@RunWith(AndroidJUnit4::class)
class PracticeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 练题页显示卡片与翻转提示() {
        // 默认启动页为练题
        composeRule.onNodeWithText("点击翻转查看答案").assertIsDisplayed()
    }

    @Test
    fun 点击卡片翻转显示答案() {
        composeRule.onNodeWithText("点击翻转查看答案").performClick()

        // 翻转后正面提示消失（背面显示答案）
        composeRule.onNodeWithText("点击翻转查看答案").assertDoesNotExist()
    }

    @Test
    fun 再次点击翻回正面() {
        composeRule.onNodeWithText("点击翻转查看答案").performClick()
        composeRule.onNodeWithText("点击翻转查看答案").assertDoesNotExist()

        composeRule.onNodeWithText("什么是 AI Agent？它与传统聊天机器人有什么区别？", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("点击翻转查看答案").assertIsDisplayed()
    }
}
