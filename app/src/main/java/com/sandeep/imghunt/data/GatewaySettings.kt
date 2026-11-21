package com.sandeep.imghunt.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object GatewayAddress {
    fun normalize(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid HTTPS gateway URL")
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Use an HTTPS gateway URL without credentials, query parameters or fragments"
        }
        return uri.toASCIIString().trimEnd('/')
    }
}

class GatewayConfig(val baseUrl: String, val accessToken: String) {
    val configured: Boolean get() = baseUrl.isNotBlank() && accessToken.length >= 24
    override fun toString(): String = "GatewayConfig(baseUrl=$baseUrl, accessToken=REDACTED)"
}

class GatewaySettings(context: Context) {
    private val prefs = context.getSharedPreferences("imghunt_gateway", Context.MODE_PRIVATE)
    private val alias = "imghunt_gateway_token_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): GatewayConfig {
        val token = runCatching {
            val stored = prefs.getString("token", null) ?: return@runCatching ""
            val parts = stored.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
        return GatewayConfig(prefs.getString("base_url", "").orEmpty(), token)
    }
    fun save(url: String, newToken: String) {
        val normalized = GatewayAddress.normalize(url)
        val old = load()
        require(newToken.isNotBlank() || normalized == old.baseUrl) {
            "Enter an access token when changing the gateway server"
        }
        val token = newToken.trim().ifBlank { old.accessToken }
        require(token.length in 24..512 && token.none { it.isISOControl() }) {
            "Enter the app access token from your server (24 to 512 characters), not a provider key"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("base_url", normalized).putString("token", encrypted).commit()) {
            "Could not save the secure connection"
        }
    }
    fun clear() { prefs.edit().clear().apply() }
}
