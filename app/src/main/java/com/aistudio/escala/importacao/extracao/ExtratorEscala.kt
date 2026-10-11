package com.aistudio.escala.importacao.extracao

import android.content.Context
import android.util.Log
import com.aistudio.escala.importacao.csv.CsvWriter
import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.dados.LinhaEscala
import com.aistudio.escala.importacao.dados.ResultadoExtracao
import com.aistudio.escala.importacao.dados.TipoErro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Coordenador da Etapa A:
 * Imagem (Base64) -> Chamada IA Gemini -> Parse JSON estruturado -> Geração de CSV no cache interno.
 */
class ExtratorEscala(
    private val geminiClient: GeminiClient
) {

    companion object {
        private const val TAG = "ExtratorEscala"

        /**
         * Converte o texto JSON retornado pela IA para a lista tipada de [LinhaEscala].
         * Tratamento defensivo para campos nulos, ausentes ou formatos inesperados.
         */
        fun parsearJsonParaLinhas(jsonTexto: String): List<LinhaEscala> {
            val limpo = jsonTexto
                .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
                .trim()

            val root = JSONObject(limpo)
            val linhasArray = root.optJSONArray("linhas") ?: return emptyList()

            val resultado = mutableListOf<LinhaEscala>()

            for (i in 0 until linhasArray.length()) {
                if (linhasArray.isNull(i)) continue
                val obj = linhasArray.getJSONObject(i)

                val data = if (!obj.isNull("data")) obj.optString("data", "").trim() else ""
                val horario = if (!obj.isNull("horario")) obj.optString("horario", "").trim() else ""
                val igreja = if (!obj.isNull("igreja")) obj.optString("igreja", "").trim() else ""
                val funcao = if (!obj.isNull("funcao")) obj.optString("funcao", "").trim() else ""
                val nome = if (!obj.isNull("nome")) obj.optString("nome", "").trim() else ""

                val confiancaStr = if (!obj.isNull("confianca")) {
                    obj.optString("confianca", "alta").trim().lowercase()
                } else "alta"

                val confianca = when (confiancaStr) {
                    "alta" -> Confianca.ALTA
                    "media", "média" -> Confianca.MEDIA
                    "baixa" -> Confianca.BAIXA
                    else -> Confianca.ALTA
                }

                val observacao = if (!obj.isNull("observacao")) obj.optString("observacao", "").trim() else ""

                resultado.add(
                    LinhaEscala(
                        data = data,
                        horario = horario,
                        igreja = igreja,
                        funcao = funcao,
                        nome = nome,
                        confianca = confianca,
                        observacao = observacao
                    )
                )
            }

            return resultado
        }

        /**
         * Mapeia qualquer exceção para o tipo de erro amigável correspondente.
         */
        fun categorizarErro(e: Throwable): TipoErro {
            var current: Throwable? = e
            while (current != null) {
                val msg = current.message?.lowercase() ?: ""

                if (current is UnknownHostException ||
                    msg.contains("unable to resolve host") ||
                    msg.contains("no address associated with hostname")
                ) {
                    return TipoErro.SEM_INTERNET
                }

                if (current is SocketTimeoutException ||
                    current is TimeoutException ||
                    msg.contains("timeout") ||
                    msg.contains("timed out")
                ) {
                    return TipoErro.TIMEOUT
                }

                if (msg.contains("429") ||
                    msg.contains("quota") ||
                    msg.contains("resource_exhausted") ||
                    msg.contains("rate limit")
                ) {
                    return TipoErro.LIMITE_EXCEDIDO
                }

                if (msg.contains("ilegível") || msg.contains("ilegivel") || msg.contains("imagem não reconhecida")) {
                    return TipoErro.IMAGEM_ILEGIVEL
                }

                if (current is JSONException || msg.contains("json") || msg.contains("resposta vazia")) {
                    return TipoErro.RESPOSTA_INVALIDA
                }

                current = current.cause
            }

            return TipoErro.DESCONHECIDO
        }
    }

    /**
     * Executa a extração completa e grava o CSV no cache interno do app.
     */
    suspend fun extrair(
        context: Context,
        imagemBase64: String
    ): ResultadoExtracao = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Iniciando extração via Gemini...")

            val jsonResposta = geminiClient.extrairJsonEscala(imagemBase64)
            val linhas = parsearJsonParaLinhas(jsonResposta)

            if (linhas.isEmpty()) {
                Log.w(TAG, "IA respondeu com lista de linhas vazia.")
                return@withContext ResultadoExtracao.Erro(
                    TipoErro.IMAGEM_ILEGIVEL,
                    "Nenhuma linha litúrgica pôde ser identificada no documento escaneado."
                )
            }

            // Grava o CSV intermediário no cache interno (context.cacheDir)
            val nomeArquivo = "escala_extraida_${System.currentTimeMillis()}.csv"
            val csvFile = CsvWriter.salvarCsvNoCache(
                diretorioCache = context.cacheDir,
                nomeArquivo = nomeArquivo,
                linhas = linhas,
                incluirColunasExtras = false
            )

            Log.i(TAG, "Extração concluída com sucesso: ${linhas.size} linhas gravadas em ${csvFile.name}")

            ResultadoExtracao.Sucesso(
                linhas = linhas,
                csvPath = csvFile.absolutePath
            )
        } catch (e: Exception) {
            val tipoErro = categorizarErro(e)
            Log.e(TAG, "Falha na extração de escala: ${tipoErro.name} (${e.javaClass.simpleName})", e)
            ResultadoExtracao.Erro(
                tipo = tipoErro,
                mensagemDetalhada = e.message ?: ""
            )
        }
    }

    /**
     * Remove com segurança arquivos de CSV gerados temporariamente no cache.
     */
    fun limparCsvsTemporarios(context: Context) {
        try {
            val arquivos = context.cacheDir.listFiles { _, name ->
                name.startsWith("escala_extraida_") && name.endsWith(".csv")
            }
            arquivos?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha não-crítica ao limpar CSVs temporários: ${e.message}")
        }
    }
}
