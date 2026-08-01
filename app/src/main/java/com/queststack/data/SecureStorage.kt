package com.queststack.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的轻量加密（AES/GCM）。
 * 密钥不可导出，仅本设备可解密；解密失败返回原文（降级，避免配置丢失）。
 */
object SecureStorage {
    private const val KEY_ALIAS = "queststack_secret"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH = 12

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密为 Base64(IV + ciphertext)；空串原样返回 */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText // 降级：加密失败存明文（Keystore 不可用场景）
        }
    }

    /** 解密；解密失败返回原文（数据非加密格式或密钥丢失时降级） */
    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
            if (decoded.size <= IV_LENGTH) return cipherText
            val iv = decoded.copyOfRange(0, IV_LENGTH)
            val encrypted = decoded.copyOfRange(IV_LENGTH, decoded.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText // 降级返回原文
        }
    }
}
