package com.aistudio.escala.importacao.extracao

import android.util.Base64
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
 * Implementação alternativa do cliente Gemini através de um servidor proxy HTTP seguro.
 *
 * Mantida como contingência/alternativa caso a organização opte por rotear chamadas
 * por um backend próprio em vez do Firebase AI Logic direto no cliente.
 */
class HttpProxyGeminiClient(
    private val proxyUrl: String,
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

    override suspend fun extrairJsonEscala(imagemBytes: ByteArray): String = withContext(Dispatchers.IO) {
        if (proxyUrl.isBlank()) {
            throw IllegalArgumentException("URL do proxy HTTP não configurada.")
        }

        val base64 = Base64.encodeToString(imagemBytes, Base64.NO_WRAP)

        val payload = JSONObject().apply {
            put("model", GeminiConfig.MODEL_NAME)
            put("systemInstruction", GeminiConfig.SYSTEM_PROMPT)
            put("temperature", GeminiConfig.TEMPERATURE.toDouble())
            put("responseMimeType", "application/json")
            put("responseSchema", JSONObject(GeminiConfig.RESPONSE_SCHEMA_JSON))
            put("imageBase64", base64)
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
                        Log.i(TAG, "Resposta recebida com sucesso do proxy Gemini (${responseBody.length} bytes).")
                        return@withContext responseBody
                    }

                    val code = response.code
                    val podeTentarNovamente = (code == 429 || code in 500..599) && tentativa < GeminiConfig.MAX_RETRIES

                    if (podeTentarNovamente) {
                        Log.w(TAG, "Código HTTP $code recebido do proxy. Aguardando para retentar...")
                    } else {
                        throw IOException("Falha no proxy Gemini: HTTP $code. Verifique o servidor.")
                    }
                }
            } catch (e: Exception) {
                ultimoErro = e
                val ehTransitorio = e is SocketTimeoutException || e is IOException

                if (ehTransitorio && tentativa < GeminiConfig.MAX_RETRIES) {
                    val esperaMs = (1000L * (1 shl tentativa)) // Backoff: 1s, 2s
                    Log.w(TAG, "Falha transitória (${e.javaClass.simpleName}). Aguardando ${esperaMs}ms...")
                    delay(esperaMs)
                } else {
                    throw e
                }
            }
            tentativa++
        }

        throw ultimoErro ?: IOException("Falha desconhecida na comunicação com o proxy Gemini.")
    }
}
