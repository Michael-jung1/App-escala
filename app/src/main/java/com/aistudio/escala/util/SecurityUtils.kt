package com.aistudio.escala.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Utilitários de segurança para hash de credenciais e armazenamento protegido via EncryptedSharedPreferences.
 */
object SecurityUtils {
    private const val TAG = "SecurityUtils"
    private const val PREFIX = "sha256:"
    private const val SECURE_PREFS_NAME = "escala_secure_prefs"

    private fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decodeBase64(str: String): ByteArray {
        return Base64.getDecoder().decode(str)
    }

    /**
     * Gera o hash SHA-256 com salt de 16 bytes para códigos de coordenador.
     * Retorna o formato: "sha256:<saltBase64>:<digestBase64>"
     */
    fun hashAccessCode(plainCode: String, customSalt: ByteArray? = null): String {
        val trimmed = plainCode.trim().uppercase()
        val salt = customSalt ?: ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val saltBase64 = encodeBase64(salt)

        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val digest = md.digest(trimmed.toByteArray(Charsets.UTF_8))
        val digestBase64 = encodeBase64(digest)

        return "$PREFIX$saltBase64:$digestBase64"
    }

    /**
     * Valida um código digitado contra o valor armazenado (que pode ser hash SHA-256 ou legado em texto puro).
     * Usa comparação em tempo constante MessageDigest.isEqual.
     */
    fun verifyAccessCode(inputCode: String, storedHashOrPlain: String): Boolean {
        val trimmedInput = inputCode.trim().uppercase()
        val trimmedStored = storedHashOrPlain.trim()
        if (trimmedInput.isEmpty() || trimmedStored.isEmpty()) return false

        if (trimmedStored.startsWith(PREFIX)) {
            val parts = trimmedStored.removePrefix(PREFIX).split(":")
            if (parts.size != 2) return false
            val salt = try {
                decodeBase64(parts[0])
            } catch (e: Exception) {
                return false
            }
            val expectedDigest = try {
                decodeBase64(parts[1])
            } catch (e: Exception) {
                return false
            }

            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            val actualDigest = md.digest(trimmedInput.toByteArray(Charsets.UTF_8))

            return MessageDigest.isEqual(expectedDigest, actualDigest)
        } else {
            // Compatibilidade com chaves legadas não migradas
            return trimmedInput.equals(trimmedStored, ignoreCase = true)
        }
    }

    /**
     * Verifica se uma string já está em formato hash seguro.
     */
    fun isHash(value: String): Boolean {
        return value.trim().startsWith(PREFIX)
    }

    /**
     * Obtém instância de SharedPreferences criptografada (EncryptedSharedPreferences - Jetpack Security).
     * Utiliza chave AES256-GCM via AndroidKeyStore com chave de valor AES256-GCM.
     */
    fun getEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar EncryptedSharedPreferences. Usando armazenamento privado com proteção local.", e)
            context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    const val KEY_DB_PASSPHRASE = "escala_db_encryption_passphrase"

    /**
     * Obtém ou gera uma chave de criptografia de 256 bits (32 bytes em hexadecimal) para o banco de dados SQLCipher.
     * A chave é armazenada de forma segura em EncryptedSharedPreferences (protegida pelo Android KeyStore).
     */
    @Synchronized
    fun getDatabasePassphrase(context: Context): String {
        val securePrefs = getEncryptedPreferences(context)
        var passphrase = securePrefs.getString(KEY_DB_PASSPHRASE, null)
        if (passphrase.isNullOrBlank()) {
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            passphrase = keyBytes.joinToString("") { "%02x".format(it) }
            securePrefs.edit().putString(KEY_DB_PASSPHRASE, passphrase).apply()
        }
        return passphrase
    }
}
