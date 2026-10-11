package com.aistudio.escala.importacao.csv

import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.dados.LinhaEscala
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Gerador de CSV com:
 * - Codificação UTF-8 com BOM (\uFEFF) para compatibilidade imediata com Excel e leitores desktop/mobile
 * - Separador ponto e vírgula (;)
 * - Cabeçalho fixo "data;horario;igreja;funcao;nome" (com suporte opcional a colunas estendidas)
 * - Aspas em campos com separador (;), quebra de linha (\n, \r) ou aspas ("), com escape de aspas ("")
 */
object CsvWriter {

    const val CABECALHO_PADRAO = "data;horario;igreja;funcao;nome"
    const val CABECALHO_ESTENDIDO = "data;horario;igreja;funcao;nome;confianca;observacao"
    const val SEPARADOR = ";"
    const val BOM = "\uFEFF"

    /**
     * Escapa um campo individual conforme as regras RFC 4180 adaptadas para ponto e vírgula.
     */
    fun escaparCampo(campo: String): String {
        val precisaAspas = campo.contains(SEPARADOR) ||
                campo.contains("\"") ||
                campo.contains("\n") ||
                campo.contains("\r")

        return if (precisaAspas) {
            val campoComAspasDuplas = campo.replace("\"", "\"\"")
            "\"$campoComAspasDuplas\""
        } else {
            campo
        }
    }

    /**
     * Gera o texto CSV completo para a lista de linhas fornecida.
     * @param incluirColunasExtras Se true, inclui confianca e observacao como colunas 6 e 7.
     */
    fun gerarTextoCsv(
        linhas: List<LinhaEscala>,
        incluirColunasExtras: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(if (incluirColunasExtras) CABECALHO_ESTENDIDO else CABECALHO_PADRAO)
        sb.append("\r\n")

        for (linha in linhas) {
            val campos = if (incluirColunasExtras) {
                listOf(
                    linha.data,
                    linha.horario,
                    linha.igreja,
                    linha.funcao,
                    linha.nome,
                    linha.confianca.name.lowercase(),
                    linha.observacao
                )
            } else {
                listOf(
                    linha.data,
                    linha.horario,
                    linha.igreja,
                    linha.funcao,
                    linha.nome
                )
            }

            sb.append(campos.joinToString(SEPARADOR) { escaparCampo(it) })
            sb.append("\r\n")
        }

        return sb.toString()
    }

    /**
     * Salva o CSV gerado em um arquivo no diretório especificado (ex: context.cacheDir).
     */
    fun salvarCsvNoCache(
        diretorioCache: File,
        nomeArquivo: String,
        linhas: List<LinhaEscala>,
        incluirColunasExtras: Boolean = false
    ): File {
        if (!diretorioCache.exists()) {
            diretorioCache.mkdirs()
        }
        val arquivoDestino = File(diretorioCache, nomeArquivo)
        val conteudo = gerarTextoCsv(linhas, incluirColunasExtras)

        FileOutputStream(arquivoDestino).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(conteudo)
                writer.flush()
            }
        }

        return arquivoDestino
    }
}
