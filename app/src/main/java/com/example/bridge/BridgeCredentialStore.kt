package com.example.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class BridgeSecrets(
    val apiToken: String,
    val sshPrivateKeyPem: String,
    val sshPublicKeyOpenSsh: String
)

/**
 * Encrypted credential storage backed by Android Keystore AES-256-GCM.
 * Credentials are never stored in plaintext on disk.
 */
class BridgeCredentialStore(context: Context) {

    private val encryptedFile = File(context.filesDir, "termux_bridge_vault.enc")
    private var inMemoryFallbackKey: SecretKey? = null

    fun hasCredentials(): Boolean = encryptedFile.exists() && encryptedFile.length() > 0

    fun getOrCreateSecrets(): BridgeSecrets {
        loadSecrets()?.let { return it }
        return regenerateAllSecrets()
    }

    fun loadSecrets(): BridgeSecrets? {
        if (!encryptedFile.exists()) return null
        return runCatching {
            val payload = encryptedFile.readBytes()
            if (payload.size <= 12) return null
            val iv = payload.copyOfRange(0, 12)
            val ciphertext = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            val plain = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            val json = JSONObject(plain)
            BridgeSecrets(
                apiToken = json.getString("apiToken"),
                sshPrivateKeyPem = json.getString("sshPrivateKeyPem"),
                sshPublicKeyOpenSsh = json.getString("sshPublicKeyOpenSsh")
            )
        }.getOrNull()
    }

    fun regenerateAllSecrets(): BridgeSecrets {
        val tokenBytes = ByteArray(32)
        SecureRandom().nextBytes(tokenBytes)
        val apiToken = Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val jsch = JSch()
        val keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
        val privOut = ByteArrayOutputStream()
        val pubOut = ByteArrayOutputStream()
        keyPair.writePrivateKey(privOut)
        keyPair.writePublicKey(pubOut, "termux-2.0-bridge")
        keyPair.dispose()

        val secrets = BridgeSecrets(
            apiToken = apiToken,
            sshPrivateKeyPem = privOut.toString(Charsets.UTF_8.name()),
            sshPublicKeyOpenSsh = pubOut.toString(Charsets.UTF_8.name()).trim()
        )
        saveEncryptedSecrets(secrets)
        return secrets
    }

    fun revokeAll() {
        if (encryptedFile.exists()) {
            encryptedFile.writeBytes(ByteArray(64))
            encryptedFile.delete()
        }
    }

    private fun saveEncryptedSecrets(secrets: BridgeSecrets) {
        val json = JSONObject().apply {
            put("apiToken", secrets.apiToken)
            put("sshPrivateKeyPem", secrets.sshPrivateKeyPem)
            put("sshPublicKeyOpenSsh", secrets.sshPublicKeyOpenSsh)
        }.toString().toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(json)

        encryptedFile.writeBytes(iv + ciphertext)
    }

    private fun getOrCreateAesKey(): SecretKey {
        return runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            (ks.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generator.generateKey()
            }
        }.getOrElse {
            // Fallback for headless JVM unit test environments without AndroidKeyStore daemon
            inMemoryFallbackKey ?: ByteArray(32).let { raw ->
                SecureRandom().nextBytes(raw)
                SecretKeySpec(raw, "AES").also { inMemoryFallbackKey = it }
            }
        }
    }

    companion object {
        private const val KEY_ALIAS = "termux_2_bridge_aes_key"
    }
}
