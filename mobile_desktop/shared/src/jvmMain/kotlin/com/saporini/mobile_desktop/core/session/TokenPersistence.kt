package com.saporini.mobile_desktop.core.session

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.util.Base64

actual class TokenPersistence {

    private val storageDir: File = File(System.getProperty("user.home"), ".saporini").apply { mkdirs() }
    private val accessFile = File(storageDir, "access.bin")
    private val refreshFile = File(storageDir, "refresh.bin")

    actual suspend fun saveAccessToken(token: String?) = save(accessFile, token)
    actual suspend fun saveRefreshToken(token: String?) = save(refreshFile, token)
    actual suspend fun loadAccessToken(): String? = load(accessFile)
    actual suspend fun loadRefreshToken(): String? = load(refreshFile)

    actual suspend fun clear() {
        accessFile.delete()
        refreshFile.delete()
    }

    private fun save(file: File, token: String?) {
        if (token == null) {
            file.delete()
            return
        }
        val encrypted = Crypt32Util.cryptProtectData(token.toByteArray(Charsets.UTF_8))
        file.writeText(Base64.getEncoder().encodeToString(encrypted))
    }

    private fun load(file: File): String? {
        if (!file.exists()) return null
        return try {
            val encrypted = Base64.getDecoder().decode(file.readText())
            val decrypted = Crypt32Util.cryptUnprotectData(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}