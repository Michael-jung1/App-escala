package com.aistudio.escala.util

import android.util.Log
import com.aistudio.escala.parser.GeminiScheduleParser
import kotlinx.serialization.SerializationException
import org.json.JSONException
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ErrorUtils {
    private const val TAG = "ErrorUtils"

    const val MSG_SEM_CONEXAO = "Sem conexão com a internet. Verifique sua rede e tente novamente."
    const val MSG_TIMEOUT = "A conexão demorou demais para responder. Tente novamente em instantes."
    const val MSG_RATE_LIMIT = "Muitas tentativas em pouco tempo. Aguarde um momento antes de tentar de novo."
    const val MSG_FORMATO_INVALIDO = "Não conseguimos entender o conteúdo do arquivo enviado. Tente uma foto mais nítida ou um arquivo em outro formato."
    const val MSG_ERRO_GENERICO = "Não foi possível importar a escala agora. Tente novamente ou entre em contato com o suporte."

    /**
     * Mapeia exceções de importação e processamento para mensagens amigáveis e acionáveis em português,
     * garantindo que detalhes técnicos fiquem restritos ao log (Log.e) e nunca vazem na UI.
     */
    fun traduzirErroImportacao(e: Throwable): String {
        try {
            Log.e(TAG, "Falha técnica na importação: ${e.javaClass.name}: ${e.message}", e)
        } catch (_: Throwable) {
            // Permite execução em testes de unidade JVM locais sem mock do android.util.Log
        }

        if (e.message == GeminiScheduleParser.ERROR_FILE_TOO_LARGE) {
            return GeminiScheduleParser.ERROR_FILE_TOO_LARGE
        }

        var current: Throwable? = e
        while (current != null) {
            val msg = current.message?.lowercase() ?: ""

            // 1. UnknownHostException ou mensagem contendo "Unable to resolve host"
            if (current is UnknownHostException ||
                msg.contains("unable to resolve host") ||
                msg.contains("no address associated with hostname")
            ) {
                return MSG_SEM_CONEXAO
            }

            // 2. SocketTimeoutException
            if (current is SocketTimeoutException ||
                current is TimeoutException ||
                msg.contains("timeout") ||
                msg.contains("timed out")
            ) {
                return MSG_TIMEOUT
            }

            // 3. HttpException com código 429 ou 400
            if (current is HttpException) {
                val code = current.code()
                if (code == 429) {
                    return MSG_RATE_LIMIT
                }
                if (code == 400) {
                    return MSG_FORMATO_INVALIDO
                }
            }

            // Menção explícita a quota ou 429 na mensagem
            if (msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted") || msg.contains("rate limit")) {
                return MSG_RATE_LIMIT
            }

            // 4. Erro de parsing JSON retornado pelo Gemini ou erro 400
            if (current is JSONException ||
                current is SerializationException ||
                msg.contains("json") ||
                msg.contains("400")
            ) {
                return MSG_FORMATO_INVALIDO
            }

            current = current.cause
        }

        // 5. Qualquer outra exceção não mapeada
        return MSG_ERRO_GENERICO
    }
}

fun traduzirErroImportacao(e: Exception): String = ErrorUtils.traduzirErroImportacao(e)
fun traduzirErroImportacao(e: Throwable): String = ErrorUtils.traduzirErroImportacao(e)
