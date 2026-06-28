package com.wellnessmate.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the access token encrypted by a non-exportable Android Keystore key. @author TODO(team member) */
class TokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("wellness_session", Context.MODE_PRIVATE)

    fun save(response: AuthResponse) {
        preferences.edit()
            .putString(KEY_TOKEN, encrypt(response.accessToken))
            .putLong(KEY_USER_ID, response.userId)
            .putString(KEY_USERNAME, response.username)
            .putString(KEY_DISPLAY_NAME, response.displayName)
            .putString(KEY_ROLE, response.role)
            .putBoolean(KEY_ONBOARDING, response.onboardingRequired)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + response.expiresInSeconds * 1000)
            .apply()
    }

    fun token(): String? {
        if (preferences.getLong(KEY_EXPIRES_AT, 0) <= System.currentTimeMillis()) {
            clear()
            return null
        }
        return preferences.getString(KEY_TOKEN, null)?.let(::decrypt)
    }

    fun session(): SessionUser? {
        if (token() == null) return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        return SessionUser(
            userId = preferences.getLong(KEY_USER_ID, 0),
            username = username,
            displayName = preferences.getString(KEY_DISPLAY_NAME, null),
            role = preferences.getString(KEY_ROLE, "CLIENT") ?: "CLIENT",
            onboardingRequired = preferences.getBoolean(KEY_ONBOARDING, true),
        )
    }

    fun markOnboardingComplete() {
        preferences.edit().putBoolean(KEY_ONBOARDING, false).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun decrypt(value: String): String? = runCatching {
        val parts = value.split(':', limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrElse {
        clear()
        null
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "wellness_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ROLE = "role"
        const val KEY_ONBOARDING = "onboarding"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
