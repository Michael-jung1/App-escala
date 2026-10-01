package com.aistudio.escala.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Gerenciador persistente do mecanismo de rate limit e bloqueio contra força bruta
 * no login do coordenador.
 *
 * Persiste failedAttempts e lockUntilTimestamp em EncryptedSharedPreferences
 * (via SecurityUtils.getEncryptedPreferences), garantindo que o rate limiting
 * e o bloqueio sobrevivam ao encerramento e reinício do processo do aplicativo.
 */
object CoordenadorLockoutManager {
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L

    private const val KEY_FAILED_ATTEMPTS = "coordenador_lockout_failed_attempts"
    private const val KEY_LOCK_UNTIL_TIMESTAMP = "coordenador_lockout_lock_until"

    private var encryptedPrefs: SharedPreferences? = null

    @Volatile
    var failedAttempts: Int = 0
        private set

    @Volatile
    var lockUntilTimestamp: Long = 0L
        private set

    /**
     * Inicializa o gerenciador com o contexto da aplicação, carregando
     * o estado persistido em EncryptedSharedPreferences.
     */
    @Synchronized
    fun init(context: Context) {
        init(SecurityUtils.getEncryptedPreferences(context.applicationContext))
    }

    /**
     * Inicializa diretamente com uma instância de SharedPreferences (útil para testes e injeção).
     */
    @Synchronized
    fun init(prefs: SharedPreferences) {
        encryptedPrefs = prefs
        carregarEstadoPersistido()
    }

    @Synchronized
    private fun carregarEstadoPersistido() {
        val prefs = encryptedPrefs ?: return
        failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        val savedTimestamp = prefs.getLong(KEY_LOCK_UNTIL_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()

        if (savedTimestamp > now) {
            lockUntilTimestamp = savedTimestamp
        } else {
            lockUntilTimestamp = 0L
            if (savedTimestamp != 0L) {
                prefs.edit().putLong(KEY_LOCK_UNTIL_TIMESTAMP, 0L).apply()
            }
        }
    }

    /**
     * Registra uma tentativa de login falha.
     * Se atingir MAX_ATTEMPTS (5), bloqueia por LOCKOUT_DURATION_MS (30s).
     * Persiste failedAttempts e lockUntilTimestamp em EncryptedSharedPreferences.
     * Retorna o timestamp de término do bloqueio (ou 0L se ainda não bloqueado).
     */
    @Synchronized
    fun recordFailedAttempt(): Long {
        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            lockUntilTimestamp = System.currentTimeMillis() + LOCKOUT_DURATION_MS
        }
        encryptedPrefs?.let { prefs ->
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
                .putLong(KEY_LOCK_UNTIL_TIMESTAMP, lockUntilTimestamp)
                .apply()
        }
        return lockUntilTimestamp
    }

    /**
     * Retorna se o usuário está atualmente bloqueado.
     */
    fun isLockedOut(): Boolean {
        return System.currentTimeMillis() < lockUntilTimestamp
    }

    /**
     * Retorna os segundos restantes de bloqueio (arredondado para cima), ou 0 se não bloqueado.
     */
    fun getRemainingSeconds(): Int {
        val now = System.currentTimeMillis()
        val diff = lockUntilTimestamp - now
        return if (diff > 0L) {
            ((diff + 999L) / 1000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
    }

    /**
     * Reseta as tentativas e o bloqueio após um login bem-sucedido.
     * Persiste a limpeza em EncryptedSharedPreferences.
     */
    @Synchronized
    fun reset() {
        failedAttempts = 0
        lockUntilTimestamp = 0L
        encryptedPrefs?.let { prefs ->
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCK_UNTIL_TIMESTAMP, 0L)
                .apply()
        }
    }
}
