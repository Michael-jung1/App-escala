package com.aistudio.escala.importacao.extracao

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Implementação do cliente Gemini através de um servidor proxy HTTP seguro.
 *
 * O proxy é responsável por autenticar o app, anexar as credenciais do Google Cloud / Gemini
 * e retornar diretamente o resultado JSON estruturado.
 *
 * Configurações incluídas:
 * - Timeout explícito de 30 segundos
 * - Retentativas com backoff exponencial (máx. 2 tentativas) para falhas transitórias
 * - Sem logs de dados pessoais (PII)
 */
class HttpProxyGeminiClient(
    private val proxyUrl: String = "https://seu-servidor-proxy.com/api/escala/extrair",
    private val authToken: String? = null
) : GeminiClient {

    companion object {
        private const val TAG = "HttpProxyGeminiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(GeminiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(GeminiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(GeminiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun extrairJsonEscala(imagemBase64: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", GeminiConfig.MODEL_NAME)
            put("systemInstruction", GeminiConfig.SYSTEM_PROMPT)
            put("temperature", GeminiConfig.TEMPERATURE.toDouble())
            put("responseMimeType", "application/json")
            put("responseSchema", JSONObject(GeminiConfig.RESPONSE_SCHEMA_JSON))
            put("imageBase64", imagemBase64)
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(proxyUrl)
            .post(requestBody)

        if (!authToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val request = requestBuilder.build()

        var tentativa = 0
        var ultimoErro: Exception? = null

        while (tentativa <= GeminiConfig.MAX_RETRIES) {
            try {
                Log.i(TAG, "Enviando requisição ao proxy Gemini (tentativa ${tentativa + 1})...")

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        if (responseBody.isBlank()) {
                            throw IllegalStateException("Resposta vazia retornada pelo servidor proxy.")
                        }
                        Log.i(TAG, "Resposta recebida com sucesso do proxy Gemini (bytes: ${responseBody.length}).")
                        return@withContext responseBody
                    }

                    val code = response.code
                    // Erros 5xx ou 429 são elegíveis para retry se ainda houver tentativas
                    val podeTentarNovamente = (code == 429 || code in 500..599) && tentativa < GeminiConfig.MAX_RETRIES

                    if (podeTentarNovamente) {
                        Log.w(TAG, "Código HTTP $code recebido. Tentando novamente...")
                    } else {
                        throw IOException("Falha no proxy Gemini: HTTP $code. Verifique a configuração do servidor.")
                    }
                }
            } catch (e: Exception) {
                ultimoErro = e
                val ehTransitório = e is SocketTimeoutException || e is IOException

                if (ehTransitório && tentativa < GeminiConfig.MAX_RETRIES) {
                    val esperaMs = (1000L * (1 shl tentativa)) // 1s, 2s
                    Log.w(TAG, "Falha transitória (${e.javaClass.simpleName}). Aguardando ${esperaMs}ms para retentar...")
                    delay(esperaMs)
                } else {
                    Log.e(TAG, "Falha definitiva na chamada ao proxy Gemini: ${e.javaClass.simpleName}")
                    throw e
                }
            }

            tentativa++
        }

        throw ultimoErro ?: IOException("Falha ao comunicar com o servidor proxy após retentativas.")
    }
}
