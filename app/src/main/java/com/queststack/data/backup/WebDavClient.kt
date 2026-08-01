package com.queststack.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

/** WebDAV 服务器配置（值对象，由 SettingsRepository 持久化） */
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = ""
)

/**
 * WebDAV 备份客户端。超时由传入的 [OkHttpClient] 决定。
 */
class WebDavClient(private val client: OkHttpClient) {

    /** 上传文件（PUT）。2xx 返回 true，否则抛 IOException */
    suspend fun put(url: String, username: String, password: String, content: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .put(content.toByteArray().toRequestBody(JSON_MEDIA_TYPE))
            .apply { addAuth(this, username, password) }
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    throw IOException("WebDAV PUT failed: HTTP ${response.code}")
                }
            }
        }
    }

    /** 下载文件（GET）。2xx 返回 body；404 抛“远程文件不存在”；其他失败抛 IOException */
    suspend fun get(url: String, username: String, password: String): String {
        val request = Request.Builder()
            .url(url)
            .apply { addAuth(this, username, password) }
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> body
                    response.code == 404 -> throw IOException("远程文件不存在")
                    else -> throw IOException("WebDAV GET failed: HTTP ${response.code}")
                }
            }
        }
    }

    /** 创建集合（目录）（MKCOL）。405/409 视为已存在（幂等），失败抛 IOException */
    suspend fun ensureCollection(url: String, username: String, password: String) {
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .apply { addAuth(this, username, password) }
            .build()
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 405 && response.code != 409) {
                    throw IOException("WebDAV MKCOL failed: HTTP ${response.code}")
                }
            }
        }
    }

    private fun addAuth(builder: Request.Builder, username: String, password: String) {
        if (username.isNotEmpty() && password.isNotEmpty()) {
            val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            builder.header("Authorization", "Basic $credentials")
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
