package com.aistudio.escala.importacao.csv

import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.dados.LinhaEscala
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Leitor determinístico de CSV com suporte a:
 * - Remoção automática do BOM UTF-8 (\uFEFF)
 * - Separador ponto e vírgula (;)
 * - Campos entre aspas duplas com aspas escapadas ("")
 * - Campos com quebras de linha embutidas
 * - Conversão para List<LinhaEscala>
 */
object CsvReader {

    private const val SEPARADOR = ';'
    private const val ASPAS = '"'

    /**
     * Parseia o conteúdo de texto de um CSV e retorna a lista de [LinhaEscala].
     */
    fun parsearTextoCsv(conteudo: String): List<LinhaEscala> {
        val textoLimpo = if (conteudo.startsWith("\uFEFF")) {
            conteudo.substring(1)
        } else {
            conteudo
        }

        val registrosBrutos = parsearRegistrosCsv(textoLimpo)
        if (registrosBrutos.isEmpty()) return emptyList()

        // Primeira linha é o cabeçalho
        val cabecalho = registrosBrutos.first()
        val linhasDeDados = registrosBrutos.drop(1)

        val indiceData = cabecalho.indexOfFirst { it.trim().equals("data", ignoreCase = true) }
        val indiceHorario = cabecalho.indexOfFirst { it.trim().equals("horario", ignoreCase = true) || it.trim().equals("hora", ignoreCase = true) }
        val indiceIgreja = cabecalho.indexOfFirst { it.trim().equals("igreja", ignoreCase = true) || it.trim().equals("comunidade", ignoreCase = true) }
        val indiceFuncao = cabecalho.indexOfFirst { it.trim().equals("funcao", ignoreCase = true) || it.trim().equals("servico", ignoreCase = true) || it.trim().equals("posto", ignoreCase = true) }
        val indiceNome = cabecalho.indexOfFirst { it.trim().equals("nome", ignoreCase = true) || it.trim().equals("servidor", ignoreCase = true) }
        val indiceConfianca = cabecalho.indexOfFirst { it.trim().equals("confianca", ignoreCase = true) }
        val indiceObservacao = cabecalho.indexOfFirst { it.trim().equals("observacao", ignoreCase = true) }

        val resultado = mutableListOf<LinhaEscala>()

        for (registro in linhasDeDados) {
            // Ignora linhas totalmente vazias
            if (registro.all { it.isBlank() }) continue

            fun campoEm(idx: Int, defaultIdx: Int): String {
                val indexFinal = if (idx >= 0) idx else defaultIdx
                return if (indexFinal in registro.indices) registro[indexFinal].trim() else ""
            }

            val data = campoEm(indiceData, 0)
            val horario = campoEm(indiceHorario, 1)
            val igreja = campoEm(indiceIgreja, 2)
            val funcao = campoEm(indiceFuncao, 3)
            val nome = campoEm(indiceNome, 4)

            val confiancaStr = campoEm(indiceConfianca, 5).lowercase()
            val confianca = when (confiancaStr) {
                "alta" -> Confianca.ALTA
                "media", "média" -> Confianca.MEDIA
                "baixa" -> Confianca.BAIXA
                else -> Confianca.ALTA
            }

            val observacao = campoEm(indiceObservacao, 6)

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
     * Lê e parseia um arquivo CSV do sistema de arquivos local.
     */
    fun lerArquivoCsv(arquivo: File): List<LinhaEscala> {
        if (!arquivo.exists()) return emptyList()
        val texto = FileInputStream(arquivo).use { fis ->
            InputStreamReader(fis, StandardCharsets.UTF_8).use { reader ->
                reader.readText()
            }
        }
        return parsearTextoCsv(texto)
    }

    /**
     * Máquina de estados para parsear registros e células CSV RFC 4180 (com separador ';').
     */
    fun parsearRegistrosCsv(texto: String): List<List<String>> {
        val registros = mutableListOf<List<String>>()
        val linhaAtual = mutableListOf<String>()
        val campoAtual = StringBuilder()

        var dentroDeAspas = false
        var i = 0
        val len = texto.length

        while (i < len) {
            val c = texto[i]

            when {
                c == ASPAS -> {
                    if (dentroDeAspas && i + 1 < len && texto[i + 1] == ASPAS) {
                        // Aspas duplas escapadas ("")
                        campoAtual.append(ASPAS)
                        i++ // pula a segunda aspa
                    } else {
                        // Alterna estado de aspas
                        dentroDeAspas = !dentroDeAspas
                    }
                }
                c == SEPARADOR && !dentroDeAspas -> {
                    linhaAtual.add(campoAtual.toString())
                    campoAtual.clear()
                }
                (c == '\r' || c == '\n') && !dentroDeAspas -> {
                    // Trata \r\n ou apenas \n
                    if (c == '\r' && i + 1 < len && texto[i + 1] == '\n') {
                        i++
                    }
                    linhaAtual.add(campoAtual.toString())
                    campoAtual.clear()
                    registros.add(linhaAtual.toList())
                    linhaAtual.clear()
                }
                else -> {
                    campoAtual.append(c)
                }
            }
            i++
        }

        // Adiciona último campo/registro se sobrou algo
        if (campoAtual.isNotEmpty() || linhaAtual.isNotEmpty()) {
            linhaAtual.add(campoAtual.toString())
            registros.add(linhaAtual.toList())
        }

        return registros
    }
}
