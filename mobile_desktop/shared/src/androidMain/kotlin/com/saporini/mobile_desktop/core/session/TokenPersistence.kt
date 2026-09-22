package com.saporini.mobile_desktop.core.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import android.util.Base64

private val Context.dataStore by preferencesDataStore(name = "saporini_auth")

actual class TokenPersistence {

    private val aead: Aead = buildAead()
    private val store = appContext.dataStore

    actual suspend fun saveAccessToken(token: String?) = save(KEY_ACCESS, token)
    actual suspend fun saveRefreshToken(token: String?) = save(KEY_REFRESH, token)
    actual suspend fun loadAccessToken(): String? = load(KEY_ACCESS)
    actual suspend fun loadRefreshToken(): String? = load(KEY_REFRESH)

    actual suspend fun clear() {
        store.edit { it.clear() }
    }

    private suspend fun save(key: Preferences.Key<String>, token: String?) {
        store.edit { prefs ->
            if (token == null) {
                prefs.remove(key)
            } else {
                val encrypted = aead.encrypt(token.toByteArray(Charsets.UTF_8), null)
                prefs[key] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            }
        }
    }

    private suspend fun load(key: Preferences.Key<String>): String? {
        val encoded = store.data.first()[key] ?: return null
        return try {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            val decrypted = aead.decrypt(encrypted, null)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildAead(): Aead {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(appContext, "saporini_keyset", "saporini_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://saporini_master_key")
            .build()
            .keysetHandle
        return keysetHandle.getPrimitive(Aead::class.java)
    }

    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")

        lateinit var appContext: Context
            private set

        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }
}