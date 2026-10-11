package com.aistudio.escala.importacao

import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.dados.ExemploCadastroRepository
import com.aistudio.escala.importacao.dados.LinhaEscala
import com.aistudio.escala.importacao.validacao.CampoLinha
import com.aistudio.escala.importacao.validacao.Severidade
import com.aistudio.escala.importacao.validacao.ValidadorEscala
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários para ValidadorEscala cobrindo todas as regras com precisão.
 */
class ValidadorEscalaTest {

    private val validador = ValidadorEscala(anoBase = 2026)

    private fun linhaValidaBase() = LinhaEscala(
        data = "2026-10-15",
        horario = "19:00",
        igreja = "São José",
        funcao = "Missal",
        nome = "Lucas Oliveira",
        confianca = Confianca.ALTA
    )

    @Test
    fun testRegra1_dataInvalida_deveGerarErro() = runBlocking {
        val linhas = listOf(linhaValidaBase().copy(data = "15/10/2026")) // formato não-ISO
        val relatorio = validador.validar(linhas)

        assertFalse(relatorio.podeImportar)
        val problemas = relatorio.linhasValidadas[0].problemas
        assertTrue(problemas.any { it.severidade == Severidade.ERRO && it.campo == CampoLinha.DATA })
    }

    @Test
    fun testRegra1_dataForaDoIntervaloRazoavel_deveGerarErro() = runBlocking {
        // Ano base 2026: aceita 2025 a 2028. Ano 2020 deve falhar
        val linhas = listOf(linhaValidaBase().copy(data = "2020-05-10"))
        val relatorio = validador.validar(linhas)

        assertFalse(relatorio.podeImportar)
        val problemas = relatorio.linhasValidadas[0].problemas
        assertTrue(problemas.any { it.severidade == Severidade.ERRO && it.campo == CampoLinha.DATA })
    }

    @Test
    fun testRegra2_horarioForaDoFormato_deveGerarErro() = runBlocking {
        val linhas = listOf(
            linhaValidaBase().copy(horario = "8h"),
            linhaValidaBase().copy(horario = "25:00"),
            linhaValidaBase().copy(horario = "")
        )
        val relatorio = validador.validar(linhas)

        assertFalse(relatorio.podeImportar)
        for (lv in relatorio.linhasValidadas) {
            assertTrue(lv.problemas.any { it.severidade == Severidade.ERRO && it.campo == CampoLinha.HORARIO })
        }
    }

    @Test
    fun testRegra3_igrejaVazia_deveGerarErro() = runBlocking {
        val linhas = listOf(linhaValidaBase().copy(igreja = "   "))
        val relatorio = validador.validar(linhas)

        assertFalse(relatorio.podeImportar)
        assertTrue(relatorio.linhasValidadas[0].problemas.any {
            it.severidade == Severidade.ERRO && it.campo == CampoLinha.IGREJA
        })
    }

    @Test
    fun testRegra4_funcaoVazia_deveGerarErro() = runBlocking {
        val linhas = listOf(linhaValidaBase().copy(funcao = ""))
        val relatorio = validador.validar(linhas)

        assertFalse(relatorio.podeImportar)
        assertTrue(relatorio.linhasValidadas[0].problemas.any {
            it.severidade == Severidade.ERRO && it.campo == CampoLinha.FUNCAO
        })
    }

    @Test
    fun testRegra5_nomeVazio_deveGerarErro() = runBlocking {
        val linhas = listOf(linhaValidaBase().copy(nome = "   "))
        val relatorio = validador.validar(linhas)

        assertFalse(relatorio.podeImportar)
        assertTrue(relatorio.linhasValidadas[0].problemas.any {
            it.severidade == Severidade.ERRO && it.campo == CampoLinha.NOME
        })
    }

    @Test
    fun testRegra6_nomeNaoCadastradoLocalmente_deveGerarAvisoNaoBloqueante() = runBlocking {
        val cadastroRepo = ExemploCadastroRepository(
            nomesMock = setOf("Pedro Henrique", "Mateus Silva")
        )
        // "Lucas Oliveira" não está na lista
        val linhas = listOf(linhaValidaBase().copy(nome = "Lucas Oliveira"))
        val relatorio = validador.validar(linhas, cadastroRepo)

        // Deve ser AVISO, não ERRO, logo pode importar
        assertTrue(relatorio.podeImportar)
        assertEquals(0, relatorio.totalErros)
        assertEquals(1, relatorio.totalAvisos)
        assertTrue(relatorio.linhasValidadas[0].problemas.any {
            it.severidade == Severidade.AVISO && it.campo == CampoLinha.NOME
        })
    }

    @Test
    fun testRegra7_linhasDuplicadas_deveGerarErro() = runBlocking {
        val l1 = linhaValidaBase()
        val l2 = linhaValidaBase() // mesma data, horário, igreja e função
        val relatorio = validador.validar(listOf(l1, l2))

        assertFalse(relatorio.podeImportar)
        assertTrue(relatorio.linhasValidadas[0].problemas.any {
            it.severidade == Severidade.ERRO && it.campo == CampoLinha.GERAL
        })
        assertTrue(relatorio.linhasValidadas[1].problemas.any {
            it.severidade == Severidade.ERRO && it.campo == CampoLinha.GERAL
        })
    }

    @Test
    fun testRegra8_mesmaPessoaEmDuasFuncoesNoMesmoHorario_deveGerarAviso() = runBlocking {
        val l1 = linhaValidaBase().copy(funcao = "Missal", nome = "Lucas")
        val l2 = linhaValidaBase().copy(funcao = "Turíbulo", nome = "Lucas") // mesma data e horário, funções distintas
        val relatorio = validador.validar(listOf(l1, l2))

        // Não é erro fatal, é aviso de conflito de escala
        assertTrue(relatorio.podeImportar)
        assertTrue(relatorio.linhasValidadas[0].problemas.any {
            it.severidade == Severidade.AVISO && it.campo == CampoLinha.NOME
        })
    }

    @Test
    fun testRegra9_confiancaBaixa_deveGerarAviso() = runBlocking {
        val linha = linhaValidaBase().copy(
            confianca = Confianca.BAIXA,
            observacao = "Dúvida no nome"
        )
        val relatorio = validador.validar(listOf(linha))

        assertTrue(relatorio.podeImportar)
        assertEquals(1, relatorio.totalAvisos)
        assertTrue(relatorio.linhasValidadas[0].problemas.any {
            it.severidade == Severidade.AVISO && it.mensagem.contains("Dúvida no nome")
        })
    }

    @Test
    fun testLinhasValidasCompletas_liberadasParaImportacao() = runBlocking {
        val cadastroRepo = ExemploCadastroRepository(
            nomesMock = setOf("Lucas Oliveira", "Mateus Silva")
        )
        val linhas = listOf(
            linhaValidaBase().copy(nome = "Lucas Oliveira"),
            linhaValidaBase().copy(
                horario = "10:00",
                funcao = "Cruz",
                nome = "Mateus Silva"
            )
        )
        val relatorio = validador.validar(linhas, cadastroRepo)

        assertTrue(relatorio.podeImportar)
        assertEquals(0, relatorio.totalErros)
        assertEquals(0, relatorio.totalAvisos)
    }
}
