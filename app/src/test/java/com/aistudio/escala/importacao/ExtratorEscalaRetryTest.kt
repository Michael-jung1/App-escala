package com.aistudio.escala.importacao

import android.content.Context
import com.aistudio.escala.importacao.dados.ResultadoExtracao
import com.aistudio.escala.importacao.dados.TipoErro
import com.aistudio.escala.importacao.extracao.ExtratorEscala
import com.aistudio.escala.importacao.extracao.GeminiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class ExtratorEscalaRetryTest {

    @Test
    fun testCategorizarErro_detectaServicoOcupado() {
        val erro503 = IOException("HTTP 503 Service Unavailable")
        assertEquals(TipoErro.SERVICO_OCUPADO, ExtratorEscala.categorizarErro(erro503))

        val erroUnavailable = RuntimeException("The service is temporarily UNAVAILABLE")
        assertEquals(TipoErro.SERVICO_OCUPADO, ExtratorEscala.categorizarErro(erroUnavailable))

        val erroHighDemand = IllegalStateException("Model is experiencing high demand, please try again")
        assertEquals(TipoErro.SERVICO_OCUPADO, ExtratorEscala.categorizarErro(erroHighDemand))

        val erroOverloaded = RuntimeException("Server is overloaded")
        assertEquals(TipoErro.SERVICO_OCUPADO, ExtratorEscala.categorizarErro(erroOverloaded))
    }

    @Test
    fun testCategorizarErro_semInternet_precisaoDeExcecao() {
        // UnknownHostException e ConnectException devem ser categorizadas como SEM_INTERNET
        val erroHost = java.net.UnknownHostException("firebase.googleapis.com")
        assertEquals(TipoErro.SEM_INTERNET, ExtratorEscala.categorizarErro(erroHost))

        val erroConnect = java.net.ConnectException("Failed to connect to /142.250.190.42:443")
        assertEquals(TipoErro.SEM_INTERNET, ExtratorEscala.categorizarErro(erroConnect))

        // Exceção genérica contendo palavras de rede na mensagem NÃO deve ser categorizada como SEM_INTERNET
        val erroGenerico = RuntimeException("Unable to resolve host or connect to custom service")
        assertEquals(TipoErro.DESCONHECIDO, ExtratorEscala.categorizarErro(erroGenerico))
    }

    @Test
    fun testCategorizarErro_errosFirebaseAI_especificos() {
        val erroAppCheck = RuntimeException("FirebaseAppCheck: Device attestation failed: 403")
        assertEquals(TipoErro.APP_CHECK_FALHOU, ExtratorEscala.categorizarErro(erroAppCheck))

        val erroApiDesabilitada = RuntimeException("Generative Language API has not been used in project or it is not enabled.")
        assertEquals(TipoErro.API_NAO_HABILITADA, ExtratorEscala.categorizarErro(erroApiDesabilitada))

        val erroPermissao = RuntimeException("403 Forbidden: PERMISSION_DENIED for caller")
        assertEquals(TipoErro.PERMISSAO_NEGADA, ExtratorEscala.categorizarErro(erroPermissao))

        val erroQuota = RuntimeException("429 RESOURCE_EXHAUSTED: quota limit exceeded")
        assertEquals(TipoErro.LIMITE_EXCEDIDO, ExtratorEscala.categorizarErro(erroQuota))
    }

    @Test
    fun testExtrair_retentaComDelaysCorretos_quandoErroTransitorio() {
        runBlocking {
            var chamadas = 0
            val delaysGravados = mutableListOf<Long>()

            val fakeClient = object : GeminiClient {
                override suspend fun extrairJsonEscala(imagemBytes: ByteArray): String {
                    chamadas++
                    if (chamadas < 3) {
                        throw RuntimeException("503 Service Unavailable: overloaded")
                    }
                    return """
                        {
                          "linhas": [
                            {
                              "data": "2026-10-18",
                              "horario": "10:00",
                              "igreja": "Matriz",
                              "funcao": "Leitor",
                              "nome": "João Paulo",
                              "confianca": "alta"
                            }
                          ]
                        }
                    """.trimIndent()
                }
            }

            val extrator = ExtratorEscala(
                geminiClient = fakeClient,
                delayProvider = { ms -> delaysGravados.add(ms) }
            )

            val tempDir = File.createTempFile("test_cache", "").apply {
                delete()
                mkdir()
            }

            val fakeContext = object : android.content.ContextWrapper(null) {
                override fun getCacheDir(): File = tempDir
            }

            val resultado = extrator.extrair(fakeContext, byteArrayOf(1, 2, 3))

            assertTrue(resultado is ResultadoExtracao.Sucesso)
            assertEquals(3, chamadas) // 1 chamada inicial + 2 retentativas
            assertEquals(listOf(2000L, 4000L), delaysGravados)

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testExtrair_esgotaTentativasERetornaServicoOcupado() {
        runBlocking {
            var chamadas = 0
            val delaysGravados = mutableListOf<Long>()

            val fakeClient = object : GeminiClient {
                override suspend fun extrairJsonEscala(imagemBytes: ByteArray): String {
                    chamadas++
                    throw RuntimeException("Resource UNAVAILABLE due to high demand")
                }
            }

            val extrator = ExtratorEscala(
                geminiClient = fakeClient,
                delayProvider = { ms -> delaysGravados.add(ms) }
            )

            val tempDir = File.createTempFile("test_cache2", "").apply {
                delete()
                mkdir()
            }

            val fakeContext = object : android.content.ContextWrapper(null) {
                override fun getCacheDir(): File = tempDir
            }

            val resultado = extrator.extrair(fakeContext, byteArrayOf(1, 2, 3))

            assertTrue(resultado is ResultadoExtracao.Erro)
            val erro = resultado as ResultadoExtracao.Erro
            assertEquals(TipoErro.SERVICO_OCUPADO, erro.tipo)
            assertEquals(3, chamadas) // Tentativa inicial + 2 novas tentativas antes de desistir
            assertEquals(listOf(2000L, 4000L), delaysGravados)

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testExtrair_erroNaoTransitorio_falhaImediatamenteSemRetry() {
        runBlocking {
            var chamadas = 0
            val delaysGravados = mutableListOf<Long>()

            val fakeClient = object : GeminiClient {
                override suspend fun extrairJsonEscala(imagemBytes: ByteArray): String {
                    chamadas++
                    throw RuntimeException("HTTP 429 quota rate limit exceeded")
                }
            }

            val extrator = ExtratorEscala(
                geminiClient = fakeClient,
                delayProvider = { ms -> delaysGravados.add(ms) }
            )

            val tempDir = File.createTempFile("test_cache3", "").apply {
                delete()
                mkdir()
            }

            val fakeContext = object : android.content.ContextWrapper(null) {
                override fun getCacheDir(): File = tempDir
            }

            val resultado = extrator.extrair(fakeContext, byteArrayOf(1, 2, 3))

            assertTrue(resultado is ResultadoExtracao.Erro)
            val erro = resultado as ResultadoExtracao.Erro
            assertEquals(TipoErro.LIMITE_EXCEDIDO, erro.tipo)
            assertEquals(1, chamadas) // Apenas 1 chamada, sem retentativas
            assertTrue(delaysGravados.isEmpty())

            tempDir.deleteRecursively()
        }
    }
}
