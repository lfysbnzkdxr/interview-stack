package com.example.interviewhelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * 将 Dispatchers.Main 替换为测试调度器，使 viewModelScope 可在 JVM 单元测试中运行。
 * 用法：@JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()
 * 测试中使用 runTest(mainDispatcher.testDispatcher.scheduler) 共享调度器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}
