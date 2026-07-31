package com.example.interviewhelper.data.remote.webdav

import com.example.interviewhelper.data.model.WebDavConfig
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Locale

class WebDavDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: WebDavDataSource

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        dataSource = WebDavDataSource(OkHttpClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = WebDavConfig(
        serverUrl = server.url("/").toString(),
        username = "user",
        password = "pass",
        remotePath = "/backups/"
    )

    @Test
    fun `连接测试成功时返回成功`() = runTest {
        server.enqueue(MockResponse().setResponseCode(207))
        assertTrue(dataSource.testConnection(config()).isSuccess)
    }

    @Test
    fun `连接测试认证失败返回 401 错误`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = dataSource.testConnection(config())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `连接测试网络异常返回失败`() = runTest {
        // 关闭服务器模拟连接失败
        val deadServer = MockWebServer()
        deadServer.start()
        val deadUrl = deadServer.url("/").toString()
        deadServer.shutdown()

        val deadConfig = WebDavConfig(serverUrl = deadUrl, username = "u", password = "p")
        val result = dataSource.testConnection(deadConfig)
        assertTrue(result.isFailure)
    }

    @Test
    fun `列出文件解析 MultiStatus XML`() = runTest {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
<d:response>
<d:href>/backups/</d:href>
<d:propstat>
<d:prop>
<d:resourcetype><d:collection/></d:resourcetype>
<d:getlastmodified>Mon, 12 Jan 2015 17:23:45 GMT</d:getlastmodified>
</d:prop>
</d:propstat>
</d:response>
<d:response>
<d:href>/backups/auto-backup-20260730-120000.json</d:href>
<d:propstat>
<d:prop>
<d:displayname>auto-backup-20260730-120000.json</d:displayname>
<d:getcontentlength>1234</d:getcontentlength>
<d:getlastmodified>Thu, 30 Jul 2026 12:00:00 GMT</d:getlastmodified>
<d:resourcetype/>
</d:prop>
</d:propstat>
</d:response>
</d:multistatus>"""
        server.enqueue(MockResponse().setResponseCode(207).setBody(xml))

        val result = dataSource.listFiles(config())

        assertTrue(result.isSuccess)
        val files = result.getOrThrow()
        // 自身目录（/backups/）应被排除，仅保留备份文件
        assertEquals(1, files.size)
        val backup = files.find { it.name == "auto-backup-20260730-120000.json" }
        assertNotNull(backup)
        assertEquals(1234L, backup!!.size)
        val expected = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            .parse("Thu, 30 Jul 2026 12:00:00 GMT")!!.time
        assertEquals(expected, backup.lastModified)
        assertFalse(files.any { it.isDirectory })
    }

    @Test
    fun `列出文件解析空 XML 返回空列表`() = runTest {
        val xml = """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:"></d:multistatus>"""
        server.enqueue(MockResponse().setResponseCode(207).setBody(xml))

        val result = dataSource.listFiles(config())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `列出文件 HTTP 错误返回失败`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = dataSource.listFiles(config())
        assertTrue(result.isFailure)
    }

    @Test
    fun `上传备份使用 PUT 并携带数据`() = runTest {
        val data = """{"categories":[],"questions":[]}"""
        server.enqueue(MockResponse().setResponseCode(201))

        val result = dataSource.uploadBackup(config(), "test.json", data)

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.endsWith("/backups/test.json"))
        assertEquals(data, request.body.readUtf8())
    }

    @Test
    fun `上传备份 403 返回错误`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val result = dataSource.uploadBackup(config(), "test.json", "{}")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
    }

    @Test
    fun `下载备份返回文件内容`() = runTest {
        val body = """{"version":1,"categories":[],"questions":[]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = dataSource.downloadBackup(config(), "backup.json")

        assertTrue(result.isSuccess)
        assertEquals(body, result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.endsWith("/backups/backup.json"))
    }

    @Test
    fun `删除备份使用 DELETE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = dataSource.deleteBackup(config(), "old.json")

        assertTrue(result.isSuccess)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `创建目录 MKCOL 405 已存在视为成功`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))

        val result = dataSource.ensureDirectory(config())

        assertTrue(result.isSuccess)
        assertEquals("MKCOL", server.takeRequest().method)
    }
}
