package com.example.interviewhelper.data.remote.webdav

import com.example.interviewhelper.data.model.WebDavConfig
import com.example.interviewhelper.data.model.WebDavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildClient(config: WebDavConfig): OkHttpClient {
        return okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(config.username, config.password))
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun buildUrl(config: WebDavConfig, path: String = ""): String {
        val base = config.serverUrl.trimEnd('/')
        val remotePath = config.remotePath.trimStart('/').trimEnd('/')
        val fullPath = if (path.isNotEmpty()) "$remotePath/${path.trimStart('/')}" else remotePath
        return "$base/$fullPath"
    }

    /**
     * 测试连接 - PROPFIND Depth:0
     */
    suspend fun testConnection(config: WebDavConfig): Result<Boolean> {
        return try {
            val client = buildClient(config)
            val request = Request.Builder()
                .url(config.serverUrl.trimEnd('/') + "/")
                .method("PROPFIND", "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><resourcetype/></prop></propfind>".toRequestBody("application/xml".toMediaType()))
                .header("Depth", "0")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful || response.code == 207 -> Result.success(true)
                        response.code == 401 -> Result.failure(Exception("认证失败: 用户名或密码错误 (401)"))
                        else -> Result.failure(Exception("连接失败: HTTP ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络连接失败: ${e.message}"))
        }
    }

    /**
     * 列出文件 - PROPFIND Depth:1
     */
    suspend fun listFiles(config: WebDavConfig, path: String = ""): Result<List<WebDavFile>> {
        return try {
            val client = buildClient(config)
            val url = buildUrl(config, path)
            val body = """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><displayname/><getcontentlength/><getlastmodified/><resourcetype/></prop></propfind>"""

            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
                .header("Depth", "1")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 207) {
                        return@withContext Result.failure(Exception("列出文件失败: HTTP ${response.code}"))
                    }
                    val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))
                    val files = parseMultiStatus(responseBody, url)
                    Result.success(files)
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("列出文件失败: ${e.message}"))
        }
    }

    /**
     * 上传备份 - PUT
     */
    suspend fun uploadBackup(config: WebDavConfig, fileName: String, data: String): Result<Unit> {
        return try {
            val client = buildClient(config)
            val url = buildUrl(config, fileName)

            val request = Request.Builder()
                .url(url)
                .put(data.toRequestBody(jsonMediaType))
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 201 || response.code == 204) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("上传失败: HTTP ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("上传失败: ${e.message}"))
        }
    }

    /**
     * 下载备份 - GET
     */
    suspend fun downloadBackup(config: WebDavConfig, fileName: String): Result<String> {
        return try {
            val client = buildClient(config)
            val url = buildUrl(config, fileName)

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))
                        Result.success(body)
                    } else {
                        Result.failure(Exception("下载失败: HTTP ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载失败: ${e.message}"))
        }
    }

    /**
     * 删除文件 - DELETE
     */
    suspend fun deleteBackup(config: WebDavConfig, fileName: String): Result<Unit> {
        return try {
            val client = buildClient(config)
            val url = buildUrl(config, fileName)

            val request = Request.Builder()
                .url(url)
                .delete()
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 204) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("删除失败: HTTP ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("删除失败: ${e.message}"))
        }
    }

    /**
     * 创建目录 - MKCOL
     */
    suspend fun ensureDirectory(config: WebDavConfig, path: String = ""): Result<Unit> {
        return try {
            val client = buildClient(config)
            val url = buildUrl(config, path)

            val request = Request.Builder()
                .url(url)
                .method("MKCOL", null)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    // 201=创建成功, 405=已存在
                    if (response.isSuccessful || response.code == 201 || response.code == 405) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("创建目录失败: HTTP ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("创建目录失败: ${e.message}"))
        }
    }

    /**
     * 解析 PROPFIND 返回的 MultiStatus XML
     */
    private fun parseMultiStatus(xml: String, baseUrl: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xml))

            var currentHref = ""
            var currentName = ""
            var currentSize = 0L
            var currentModified = 0L
            var isCollection = false
            var inResponse = false
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name?.lowercase() ?: ""
                        currentTag = localName
                        when {
                            localName == "response" -> {
                                inResponse = true
                                currentHref = ""
                                currentName = ""
                                currentSize = 0L
                                currentModified = 0L
                                isCollection = false
                            }
                            localName == "collection" && inResponse -> isCollection = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inResponse) {
                            val text = parser.text?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "href" -> currentHref = text
                                    "displayname" -> currentName = text
                                    "getcontentlength" -> currentSize = text.toLongOrNull() ?: 0L
                                    "getlastmodified" -> currentModified = parseHttpDate(text)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name?.lowercase() ?: ""
                        when {
                            localName == "response" && inResponse -> {
                                inResponse = false
                                // 排除自身目录
                                val decodedHref = java.net.URLDecoder.decode(currentHref, "UTF-8")
                                // displayname 为空时从 href 提取文件名
                                val finalName = if (currentName.isEmpty() && decodedHref.isNotEmpty()) {
                                    decodedHref.trimEnd('/').substringAfterLast('/').ifEmpty { decodedHref }
                                } else {
                                    currentName
                                }
                                if (finalName.isNotEmpty() && decodedHref != baseUrl.trimEnd('/') + "/") {
                                    files.add(
                                        WebDavFile(
                                            name = finalName,
                                            path = decodedHref,
                                            size = currentSize,
                                            lastModified = currentModified,
                                            isDirectory = isCollection
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // XML 解析失败返回空列表
        }
        return files
    }

    /**
     * 解析 WebDAV 返回的 HTTP 日期（RFC 1123 格式），失败返回 0
     */
    private fun parseHttpDate(text: String): Long {
        return try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(text)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
