package com.aistudio.escala.importacao

import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.extracao.ExtratorEscala
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários para mapeamento do JSON estruturado retornado pela IA para List<LinhaEscala>.
 * Valida resiliência contra markdown code fences, campos nulos/vazios e respostas malformadas.
 */
class MapeadorExtracaoJsonTest {

    @Test
    fun testParseJsonValido_comMarkdownCodeFences() {
        val jsonComMarkdown = """
            ```json
            {
              "linhas": [
                {
                  "data": "2026-10-12",
                  "horario": "08:00",
                  "igreja": "Nossa Senhora Aparecida",
                  "funcao": "Missal",
                  "nome": "Carlos Drummond",
                  "confianca": "alta",
                  "observacao": ""
                },
                {
                  "data": "2026-10-12",
                  "horario": "19:00",
                  "igreja": "Nossa Senhora Aparecida",
                  "funcao": "Credência",
                  "nome": "Ana Clara",
                  "confianca": "baixa",
                  "observacao": "Sobrenome cortado na borda"
                }
              ]
            }
            ```
        """.trimIndent()

        val resultado = ExtratorEscala.parsearJsonParaLinhas(jsonComMarkdown)

        assertEquals(2, resultado.size)

        val l1 = resultado[0]
        assertEquals("2026-10-12", l1.data)
        assertEquals("08:00", l1.horario)
        assertEquals("Nossa Senhora Aparecida", l1.igreja)
        assertEquals("Missal", l1.funcao)
        assertEquals("Carlos Drummond", l1.nome)
        assertEquals(Confianca.ALTA, l1.confianca)
        assertEquals("", l1.observacao)

        val l2 = resultado[1]
        assertEquals(Confianca.BAIXA, l2.confianca)
        assertEquals("Sobrenome cortado na borda", l2.observacao)
    }

    @Test
    fun testParseJsonComCamposNulosOuAusentes_naoGeraCrash() {
        val jsonComNulos = """
            {
              "linhas": [
                {
                  "data": null,
                  "horario": "18:00",
                  "igreja": "Matriz",
                  "funcao": null,
                  "nome": null,
                  "confianca": "media"
                }
              ]
            }
        """.trimIndent()

        val resultado = ExtratorEscala.parsearJsonParaLinhas(jsonComNulos)

        assertEquals(1, resultado.size)
        val l = resultado[0]
        assertEquals("", l.data) // null vira string vazia
        assertEquals("18:00", l.horario)
        assertEquals("Matriz", l.igreja)
        assertEquals("", l.funcao)
        assertEquals("", l.nome)
        assertEquals(Confianca.MEDIA, l.confianca)
    }

    @Test(expected = JSONException::class)
    fun testParseJsonMalformado_deveLancarExcecao() {
        val jsonCorrompido = "{ \"linhas\": [ { \"data\": \"2026-10-12\" " // Chaves e colchetes não fechados
        ExtratorEscala.parsearJsonParaLinhas(jsonCorrompido)
    }

    @Test
    fun testParseJsonSemChaveLinhas_retornaListaVazia() {
        val jsonSemLinhas = "{ \"outro_objeto\": [] }"
        val resultado = ExtratorEscala.parsearJsonParaLinhas(jsonSemLinhas)
        assertTrue(resultado.isEmpty())
    }

    @Test
    fun testCategorizacaoErros() {
        val errInternet = java.net.UnknownHostException("Unable to resolve host proxy.exemplo.com")
        assertEquals(
            com.aistudio.escala.importacao.dados.TipoErro.SEM_INTERNET,
            ExtratorEscala.categorizarErro(errInternet)
        )

        val errTimeout = java.net.SocketTimeoutException("Read timed out")
        assertEquals(
            com.aistudio.escala.importacao.dados.TipoErro.TIMEOUT,
            ExtratorEscala.categorizarErro(errTimeout)
        )

        val errQuota = RuntimeException("HTTP 429 Resource has been exhausted (rate limit)")
        assertEquals(
            com.aistudio.escala.importacao.dados.TipoErro.LIMITE_EXCEDIDO,
            ExtratorEscala.categorizarErro(errQuota)
        )
    }
}
