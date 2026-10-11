package com.aistudio.escala.importacao

import com.aistudio.escala.importacao.csv.CsvReader
import com.aistudio.escala.importacao.csv.CsvWriter
import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.dados.LinhaEscala
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários para CsvWriter e CsvReader.
 * Valida o ciclo completo de escrita e leitura (ida e volta) com:
 * - Acentos e caracteres especiais em UTF-8 com BOM
 * - Campos com ponto e vírgula (;)
 * - Campos com aspas duplas (") e escape ("")
 * - Campos com quebras de linha embutidas (\n)
 */
class CsvInOutTest {

    @Test
    fun testCicloCompleto_comAcentos_pontoEVirgula_e_aspas() {
        val linhasOriginais = listOf(
            LinhaEscala(
                data = "2026-10-11",
                horario = "08:00",
                igreja = "Paróquia São José; Comunidade Perpétuo Socorro",
                funcao = "Acólito \"Chefe\" de Cerimônia",
                nome = "João de Alcântara & Conceição",
                confianca = Confianca.ALTA,
                observacao = "Leitura nítida"
            ),
            LinhaEscala(
                data = "2026-10-18",
                horario = "19:30",
                igreja = "Capela Santa Luzia",
                funcao = "Sineta 1 e 2\n(substituto)",
                nome = "Maria Luísa Gonçalves",
                confianca = Confianca.BAIXA,
                observacao = "Nome borrado na borda"
            )
        )

        // 1. Gera CSV
        val csvTexto = CsvWriter.gerarTextoCsv(linhasOriginais, incluirColunasExtras = false)

        // Verifica que o BOM está presente no início
        assertTrue(csvTexto.startsWith("\uFEFF"))
        // Verifica que o cabeçalho fixo padrão está correto
        assertTrue(csvTexto.contains("data;horario;igreja;funcao;nome"))

        // 2. Lê de volta via CsvReader
        val linhasLidas = CsvReader.parsearTextoCsv(csvTexto)

        assertEquals(2, linhasLidas.size)

        // Linha 1: ponto e vírgula e aspas duplas escapadas
        val l1 = linhasLidas[0]
        assertEquals("2026-10-11", l1.data)
        assertEquals("08:00", l1.horario)
        assertEquals("Paróquia São José; Comunidade Perpétuo Socorro", l1.igreja)
        assertEquals("Acólito \"Chefe\" de Cerimônia", l1.funcao)
        assertEquals("João de Alcântara & Conceição", l1.nome)

        // Linha 2: quebra de linha embutida
        val l2 = linhasLidas[1]
        assertEquals("2026-10-18", l2.data)
        assertEquals("19:30", l2.horario)
        assertEquals("Capela Santa Luzia", l2.igreja)
        assertEquals("Sineta 1 e 2\n(substituto)", l2.funcao)
        assertEquals("Maria Luísa Gonçalves", l2.nome)
    }

    @Test
    fun testCicloCompleto_colunasEstendidas() {
        val linhasOriginais = listOf(
            LinhaEscala(
                data = "2026-11-01",
                horario = "10:00",
                igreja = "Catedral",
                funcao = "Turíbulo",
                nome = "Gabriel",
                confianca = Confianca.BAIXA,
                observacao = "Verificar se é Gabriel ou Daniel"
            )
        )

        val csvTexto = CsvWriter.gerarTextoCsv(linhasOriginais, incluirColunasExtras = true)
        val lidas = CsvReader.parsearTextoCsv(csvTexto)

        assertEquals(1, lidas.size)
        assertEquals(Confianca.BAIXA, lidas[0].confianca)
        assertEquals("Verificar se é Gabriel ou Daniel", lidas[0].observacao)
    }

    @Test
    fun testEscaparCampo_apenasSeNecessario() {
        assertEquals("Missal", CsvWriter.escaparCampo("Missal"))
        assertEquals("\"Missal; Cruz\"", CsvWriter.escaparCampo("Missal; Cruz"))
        assertEquals("\"Acólito \"\"Mor\"\"\"", CsvWriter.escaparCampo("Acólito \"Mor\""))
        assertEquals("\"Linha 1\nLinha 2\"", CsvWriter.escaparCampo("Linha 1\nLinha 2"))
    }
}
