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
        val bytes = token.toByteArray(Charsets.UTF_8)
        val payload = if (isWindows) Crypt32Util.cryptProtectData(bytes) else bytes
        if (!isWindows) restrictToOwner(file)
        file.writeText(Base64.getEncoder().encodeToString(payload))
    }

    private fun load(file: File): String? {
        if (!file.exists()) return null
        return try {
            val payload = Base64.getDecoder().decode(file.readText())
            val decrypted = if (isWindows) Crypt32Util.cryptUnprotectData(payload) else payload
            String(decrypted, Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    // DPAPI is Windows-only; elsewhere tokens sit in an owner-only (0600) file.
    private fun restrictToOwner(file: File) {
        file.createNewFile()
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    private companion object {
        val isWindows: Boolean =
            System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    }
}