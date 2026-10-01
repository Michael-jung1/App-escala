package com.aistudio.escala.util

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("escala_prefs", Context.MODE_PRIVATE)
    private val defaultPrefs: SharedPreferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = SecurityUtils.getEncryptedPreferences(context)

    companion object {
        private const val KEY_NOME_USUARIO = "key_nome_usuario"
        private const val KEY_COORDENADOR_CHAVE = "key_coordenador_chave"
        private const val KEY_THEME_MODE = "key_theme_mode"
        const val KEY_SENHA_COORD_CACHE = "senha_coord_cache"
        const val KEY_IS_ADMIN_CACHE = "isAdmin_cache"
    }

    init {
        migrarSenhasLegadas()
    }

    private fun migrarSenhasLegadas() {
        // Migração de SharedPreferences legado para EncryptedSharedPreferences com hash seguro
        val legacySenha = prefs.getString("senha_coord_cache", null)
            ?: prefs.getString("senha_hash_cache", null)
            ?: defaultPrefs.getString("senha_coord_cache", null)
        if (legacySenha != null) {
            val hashed = if (SecurityUtils.isHash(legacySenha)) legacySenha else SecurityUtils.hashAccessCode(legacySenha)
            securePrefs.edit().putString(KEY_SENHA_COORD_CACHE, hashed).apply()
        }
        // Remove quaisquer credenciais em texto simples de SharedPreferences comuns
        prefs.edit().remove("senha_coord_cache").remove("senha_hash_cache").apply()
        defaultPrefs.edit().remove("senha_coord_cache").remove("senha_hash_cache").apply()

        // Migra coordenadorChave caso existisse no SharedPreferences não criptografado
        val legacyKey = prefs.getString(KEY_COORDENADOR_CHAVE, null)
            ?: defaultPrefs.getString(KEY_COORDENADOR_CHAVE, null)
        if (legacyKey != null) {
            val hashed = if (SecurityUtils.isHash(legacyKey)) legacyKey else SecurityUtils.hashAccessCode(legacyKey)
            securePrefs.edit().putString(KEY_COORDENADOR_CHAVE, hashed).apply()
            prefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
            defaultPrefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
        }
    }

    var senhaCoordCache: String?
        get() {
            val secureVal = securePrefs.getString(KEY_SENHA_COORD_CACHE, null)
                ?: securePrefs.getString("senha_hash_cache", null)
            if (secureVal != null) {
                if (!SecurityUtils.isHash(secureVal)) {
                    val hashed = SecurityUtils.hashAccessCode(secureVal)
                    securePrefs.edit().putString(KEY_SENHA_COORD_CACHE, hashed).apply()
                    return hashed
                }
                return secureVal
            }
            return null
        }
        set(value) {
            if (value != null) {
                // Garante que o código de acesso NUNCA seja armazenado em texto simples: sempre salva hash SHA-256 com salt
                val hashed = if (SecurityUtils.isHash(value)) value else SecurityUtils.hashAccessCode(value)
                securePrefs.edit().putString(KEY_SENHA_COORD_CACHE, hashed).apply()
            } else {
                securePrefs.edit().remove(KEY_SENHA_COORD_CACHE).remove("senha_hash_cache").apply()
            }
            // Limpa de qualquer armazenamento não criptografado
            prefs.edit().remove("senha_coord_cache").remove("senha_hash_cache").apply()
            defaultPrefs.edit().remove("senha_coord_cache").remove("senha_hash_cache").apply()
        }

    var isAdminCache: Boolean
        get() = securePrefs.getBoolean(KEY_IS_ADMIN_CACHE, false)
        set(value) = securePrefs.edit().putBoolean(KEY_IS_ADMIN_CACHE, value).apply()

    fun salvarIsAdminCache(isAdmin: Boolean) {
        securePrefs.edit().putBoolean(KEY_IS_ADMIN_CACHE, isAdmin).apply()
    }

    var themeMode: ThemeMode
        get() {
            val str = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            return try {
                ThemeMode.valueOf(str ?: ThemeMode.SYSTEM.name)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var nomeUsuario: String?
        get() = prefs.getString(KEY_NOME_USUARIO, null)
        set(value) = prefs.edit().putString(KEY_NOME_USUARIO, value).apply()

    var coordenadorChave: String?
        get() {
            val secureVal = securePrefs.getString(KEY_COORDENADOR_CHAVE, null)
            if (secureVal != null) {
                if (!SecurityUtils.isHash(secureVal)) {
                    val hashed = SecurityUtils.hashAccessCode(secureVal)
                    securePrefs.edit().putString(KEY_COORDENADOR_CHAVE, hashed).apply()
                    return hashed
                }
                return secureVal
            }
            val legacy = prefs.getString(KEY_COORDENADOR_CHAVE, null)
                ?: defaultPrefs.getString(KEY_COORDENADOR_CHAVE, null)
            if (legacy != null) {
                val hashed = if (SecurityUtils.isHash(legacy)) legacy else SecurityUtils.hashAccessCode(legacy)
                securePrefs.edit().putString(KEY_COORDENADOR_CHAVE, hashed).apply()
                prefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
                defaultPrefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
                return hashed
            }
            return null
        }
        set(value) {
            if (value != null) {
                // Apenas um hash SHA-256 com salt é armazenado, nunca a senha original
                val hashed = if (SecurityUtils.isHash(value)) value else SecurityUtils.hashAccessCode(value)
                securePrefs.edit().putString(KEY_COORDENADOR_CHAVE, hashed).apply()
            } else {
                securePrefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
            }
            prefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
            defaultPrefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
        }

    fun limparSessaoCoordenador() {
        securePrefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
        prefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
        defaultPrefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
    }
}
