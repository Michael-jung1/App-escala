package com.aistudio.escala.util

import android.util.Log
import com.aistudio.escala.parser.GeminiApiKeyMissingException
import com.aistudio.escala.parser.GeminiScheduleParser
import kotlinx.serialization.SerializationException
import org.json.JSONException
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ErrorUtils {
    private const val TAG = "ErrorUtils"

    const val MSG_API_KEY_NAO_CONFIGURADA = "A importação por IA não está configurada neste aplicativo. Avise o desenvolvedor."
    const val MSG_ACESSO_RECUSADO = "O serviço de leitura por IA recusou o acesso. Avise o desenvolvedor."
    const val MSG_SERVICO_INDISPONIVEL = "O serviço de leitura por IA está indisponível no momento. Avise o desenvolvedor."
    const val MSG_SEM_CONEXAO = "Sem conexão com a internet. Verifique sua rede e tente novamente."
    const val MSG_TIMEOUT = "A conexão demorou demais para responder. Tente novamente em instantes."
    const val MSG_RATE_LIMIT = "Muitas tentativas em pouco tempo. Aguarde um momento antes de tentar de novo."
    const val MSG_FORMATO_INVALIDO = "Não conseguimos entender o conteúdo do arquivo enviado. Tente uma foto mais nítida ou um arquivo em outro formato."
    const val MSG_ERRO_GENERICO = "Não foi possível importar a escala agora. Tente novamente ou entre em contato com o suporte."

    /**
     * Mapeia exceções de importação e processamento para mensagens amigáveis e acionáveis em português,
     * garantindo que detalhes técnicos fiquem restritos ao log (Log.e) e nunca vazem na UI.
     *
     * Registra sempre a exceção completa com Log.e antes de produzir a mensagem,
     * incluindo código HTTP e o corpo da resposta de erro quando houver.
     */
    fun traduzirErroImportacao(e: Throwable): String {
        // Extrai dados HTTP completos (código e corpo) se houver HttpException na pilha
        var httpException: HttpException? = null
        var inspect: Throwable? = e
        while (inspect != null) {
            if (inspect is HttpException) {
                httpException = inspect
                break
            }
            inspect = inspect.cause
        }

        try {
            if (httpException != null) {
                val httpCode = httpException.code()
                val errorBody = try {
                    httpException.response()?.errorBody()?.string()
                } catch (_: Throwable) {
                    null
                }
                Log.e(
                    TAG,
                    "Falha técnica na importação [HTTP $httpCode, errorBody='$errorBody']: ${e.javaClass.name}: ${e.message}",
                    e
                )
            } else {
                Log.e(TAG, "Falha técnica na importação: ${e.javaClass.name}: ${e.message}", e)
            }
        } catch (_: Throwable) {
            // Permite execução em testes de unidade JVM locais sem mock do android.util.Log
        }

        if (e.message == GeminiScheduleParser.ERROR_FILE_TOO_LARGE) {
            return GeminiScheduleParser.ERROR_FILE_TOO_LARGE
        }

        var current: Throwable? = e
        while (current != null) {
            val msg = current.message?.lowercase() ?: ""

            // (1) Exceção da checagem de BuildConfig.GEMINI_API_KEY em branco
            if (current is GeminiApiKeyMissingException ||
                (current is IllegalStateException && (
                    msg.contains("chave da api gemini") ||
                    msg.contains("gemini_api_key") ||
                    msg.contains("chave de api gemini") ||
                    msg.contains("api key não configurada") ||
                    msg.contains("api key nao configurada")
                ))
            ) {
                return MSG_API_KEY_NAO_CONFIGURADA
            }

            // (2) HttpException 401 ou 403
            if (current is HttpException) {
                val code = current.code()
                if (code == 401 || code == 403) {
                    return MSG_ACESSO_RECUSADO
                }
                // (3) HttpException 404
                if (code == 404) {
                    return MSG_SERVICO_INDISPONIVEL
                }
                // (4) Mapeamentos de rede: 429 e 400
                if (code == 429) {
                    return MSG_RATE_LIMIT
                }
                if (code == 400) {
                    return MSG_FORMATO_INVALIDO
                }
            }

            // Mensagens literais de status HTTP 401, 403, 404
            if (msg.contains("http 401") || msg.contains("http 403") ||
                msg.contains("unauthenticated") || msg.contains("permission_denied")
            ) {
                return MSG_ACESSO_RECUSADO
            }
            if (msg.contains("http 404") || msg.contains("status 404")) {
                return MSG_SERVICO_INDISPONIVEL
            }

            // (4) Mapeamentos de rede mantidos:
            // Sem internet (UnknownHostException ou mensagem correspondente)
            if (current is UnknownHostException ||
                msg.contains("unable to resolve host") ||
                msg.contains("no address associated with hostname")
            ) {
                return MSG_SEM_CONEXAO
            }

            // Timeout (SocketTimeoutException ou menção explícita)
            if (current is SocketTimeoutException ||
                current is TimeoutException ||
                msg.contains("timeout") ||
                msg.contains("timed out")
            ) {
                return MSG_TIMEOUT
            }

            // Rate limit / Quota (429 ou menção explícita a quota)
            if (msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted") || msg.contains("rate limit")) {
                return MSG_RATE_LIMIT
            }

            // Erro de parsing JSON retornado pelo Gemini ou erro 400
            if (current is JSONException ||
                current is SerializationException ||
                msg.contains("json") ||
                msg.contains("400")
            ) {
                return MSG_FORMATO_INVALIDO
            }

            current = current.cause
        }

        // Genérico só para o que sobrar
        return MSG_ERRO_GENERICO
    }
}

fun traduzirErroImportacao(e: Exception): String = ErrorUtils.traduzirErroImportacao(e)
fun traduzirErroImportacao(e: Throwable): String = ErrorUtils.traduzirErroImportacao(e)
