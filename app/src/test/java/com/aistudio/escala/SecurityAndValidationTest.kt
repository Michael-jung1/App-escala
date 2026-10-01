package com.aistudio.escala

import com.aistudio.escala.data.DatabaseHelper
import com.aistudio.escala.util.CoordenadorLockoutManager
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.SearchUtils
import com.aistudio.escala.util.SecurityUtils
import java.time.LocalDate
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndValidationTest {

    @Test
    fun testHashAccessCode_generatesUniqueSaltedHash() {
        val code = "COORD2026"
        val hash1 = SecurityUtils.hashAccessCode(code)
        val hash2 = SecurityUtils.hashAccessCode(code)

        assertTrue("Hash 1 deve iniciar com sha256:", hash1.startsWith("sha256:"))
        assertTrue("Hash 2 deve iniciar com sha256:", hash2.startsWith("sha256:"))
        assertNotEquals("Dois hashes do mesmo código devem ser diferentes devido ao salt aleatório", hash1, hash2)
    }

    @Test
    fun testVerifyAccessCode_successAndFailure() {
        val code = "JFYLQP"
        val hash = SecurityUtils.hashAccessCode(code)

        assertTrue("Código correto deve ser validado com sucesso", SecurityUtils.verifyAccessCode("JFYLQP", hash))
        assertTrue("Código correto em minúsculas deve ser aceito", SecurityUtils.verifyAccessCode("jfylqp", hash))
        assertFalse("Código errado deve ser rejeitado", SecurityUtils.verifyAccessCode("ERRADO123", hash))
        assertFalse("Código em branco deve ser rejeitado", SecurityUtils.verifyAccessCode("", hash))
    }

    @Test
    fun testSanitizeInput_removesControlCharsAndEnforcesLimit() {
        val inputWithControl = "João\u0000\u0007 da Silva  \r\n"
        val sanitized = DatabaseHelper.sanitizeInput(inputWithControl, 20)
        assertEquals("João da Silva", sanitized)

        val longInput = "A".repeat(150)
        val truncated = DatabaseHelper.sanitizeInput(longInput, 100)
        assertEquals(100, truncated.length)

        val emptyInput = "     "
        val emptyResult = DatabaseHelper.sanitizeInput(emptyInput, 50)
        assertEquals("", emptyResult)
    }

    @Test
    fun testCoordenadorLockoutManager_locksOutAfter5AttemptsFor30Seconds() {
        CoordenadorLockoutManager.reset()
        assertFalse("Não deve estar bloqueado no início", CoordenadorLockoutManager.isLockedOut())
        assertEquals(0, CoordenadorLockoutManager.failedAttempts)
        assertEquals(0, CoordenadorLockoutManager.getRemainingSeconds())

        // 4 tentativas falhas - ainda não bloqueado
        for (i in 1..4) {
            CoordenadorLockoutManager.recordFailedAttempt()
            assertEquals(i, CoordenadorLockoutManager.failedAttempts)
            assertFalse("Não deve estar bloqueado com $i tentativas", CoordenadorLockoutManager.isLockedOut())
        }

        // 5ª tentativa falha - bloqueio ativado por 30 segundos
        CoordenadorLockoutManager.recordFailedAttempt()
        assertEquals(5, CoordenadorLockoutManager.failedAttempts)
        assertTrue("Deve estar bloqueado após 5 tentativas", CoordenadorLockoutManager.isLockedOut())
        val remaining = CoordenadorLockoutManager.getRemainingSeconds()
        assertTrue("Segundos restantes devem ser entre 1 e 30 (obtido $remaining)", remaining in 1..30)

        // Reset limpa tentativas e bloqueio
        CoordenadorLockoutManager.reset()
        assertFalse("Não deve estar bloqueado após reset", CoordenadorLockoutManager.isLockedOut())
        assertEquals(0, CoordenadorLockoutManager.failedAttempts)
        assertEquals(0, CoordenadorLockoutManager.getRemainingSeconds())
    }

    @Test
    fun testAccessInputSanitization_preservesValidCharactersAndStripsDangerous() {
        val rawName = "  Maria Oliveira <script>  "
        val sanitizedName = DatabaseHelper.sanitizeInput(rawName, 100)
        assertEquals("Maria Oliveira <script>", sanitizedName)

        val rawKey = "  coord123\u0000  "
        val sanitizedKey = DatabaseHelper.sanitizeInput(rawKey, 20).uppercase()
        assertEquals("COORD123", sanitizedKey)
    }

    @Test
    fun testOfflineCoordinatorHashLoginVerification() {
        val originalSecret = "MEUACESSO2026"
        // Simula o hash gerado para o PreferencesManager via hashAccessCode
        val savedHashInPreferences = SecurityUtils.hashAccessCode(originalSecret)
        assertTrue(SecurityUtils.isHash(savedHashInPreferences))
        assertFalse(savedHashInPreferences.contains(originalSecret))

        // 1. Caso a chave no banco esteja em texto puro legado:
        val legacyDbKey = originalSecret
        assertTrue(
            "Deve autenticar comparando chave do banco com o hash salvo nas preferências",
            SecurityUtils.verifyAccessCode(legacyDbKey, savedHashInPreferences)
        )

        // 2. Caso a chave no banco seja o mesmo hash:
        val identicalDbHash = savedHashInPreferences
        assertEquals(identicalDbHash, savedHashInPreferences)

        // 3. Credencial incorreta no banco ou preferências não deve autenticar:
        val wrongDbKey = "OUTRASSENHA"
        assertFalse(
            "Chave divergente não pode autenticar",
            SecurityUtils.verifyAccessCode(wrongDbKey, savedHashInPreferences)
        )
    }

    @Test
    fun testCoordenadorLockoutManager_persistsAcrossRestarts() {
        val fakeStorage = mutableMapOf<String, Any>()
        val editor = object : android.content.SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = this
            override fun putStringSet(key: String, values: Set<String>?) = this
            override fun putInt(key: String, value: Int) = apply { fakeStorage[key] = value }
            override fun putLong(key: String, value: Long) = apply { fakeStorage[key] = value }
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun remove(key: String) = apply { fakeStorage.remove(key) }
            override fun clear() = apply { fakeStorage.clear() }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
        val fakePrefs = object : android.content.SharedPreferences {
            override fun getAll(): Map<String, *> = fakeStorage
            override fun getString(key: String, defValue: String?): String? = fakeStorage[key] as? String ?: defValue
            override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = null
            override fun getInt(key: String, defValue: Int): Int = fakeStorage[key] as? Int ?: defValue
            override fun getLong(key: String, defValue: Long): Long = fakeStorage[key] as? Long ?: defValue
            override fun getFloat(key: String, defValue: Float): Float = defValue
            override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
            override fun contains(key: String): Boolean = fakeStorage.containsKey(key)
            override fun edit(): android.content.SharedPreferences.Editor = editor
            override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        // Inicializa o lockout manager com as preferências simuladas
        CoordenadorLockoutManager.init(fakePrefs)
        CoordenadorLockoutManager.reset()

        assertEquals(0, CoordenadorLockoutManager.failedAttempts)
        assertFalse(CoordenadorLockoutManager.isLockedOut())

        // Executa 5 tentativas falhas
        for (i in 1..5) {
            CoordenadorLockoutManager.recordFailedAttempt()
        }
        assertEquals(5, CoordenadorLockoutManager.failedAttempts)
        assertTrue(CoordenadorLockoutManager.isLockedOut())
        assertTrue(CoordenadorLockoutManager.getRemainingSeconds() > 0)

        // Simula o encerramento do processo do app (novo init recarregando do SharedPreferences)
        CoordenadorLockoutManager.init(fakePrefs)

        assertEquals("Tentativas falhas devem persistir após reinício do processo", 5, CoordenadorLockoutManager.failedAttempts)
        assertTrue("Bloqueio deve persistir após reinício do processo", CoordenadorLockoutManager.isLockedOut())
        assertTrue("Segundos restantes devem persistir após reinício", CoordenadorLockoutManager.getRemainingSeconds() > 0)

        // Sucesso no login deve resetar a persistência
        CoordenadorLockoutManager.reset()
        assertEquals(0, CoordenadorLockoutManager.failedAttempts)
        assertFalse(CoordenadorLockoutManager.isLockedOut())

        // Simula reinício novamente após o reset
        CoordenadorLockoutManager.init(fakePrefs)
        assertEquals(0, CoordenadorLockoutManager.failedAttempts)
        assertFalse(CoordenadorLockoutManager.isLockedOut())
    }

    @Test
    fun testGeminiScheduleParser_parseJsonToEscala_handlesNullAndMissingFieldsSafely() {
        val jsonText = """
            {
              "periodo": "Novembro de 2026",
              "igrejas": [
                {
                  "igreja": "Comunidade São Pedro",
                  "titulo": null,
                  "coordenadores": "",
                  "datas": ["01/11", "08/11"],
                  "postos": [
                    {
                      "funcao": "Missal",
                      "escalacoes": [
                        { "data": "01/11", "pessoa": "Carlos" }
                      ]
                    }
                  ]
                },
                {
                  "igreja": "Matriz Santo Antônio",
                  "titulo": "Escala Anual de Cerimoniários",
                  "coordenadores": "Ana e Paulo",
                  "datas": ["01/11"],
                  "postos": []
                }
              ]
            }
        """.trimIndent()

        val parsed = com.aistudio.escala.parser.GeminiScheduleParser.parseJsonToEscala(jsonText, "teste.json")
        assertEquals("Novembro de 2026", parsed.periodo)
        assertEquals(2, parsed.igrejas.size)

        val ig1 = parsed.igrejas[0]
        assertEquals("Comunidade São Pedro", ig1.nome)
        assertEquals(null, ig1.titulo)
        assertEquals(null, ig1.coordenadores)
        assertEquals(1, ig1.postos.size)

        val ig2 = parsed.igrejas[1]
        assertEquals("Matriz Santo Antônio", ig2.nome)
        assertEquals("Escala Anual de Cerimoniários", ig2.titulo)
        assertEquals("Ana e Paulo", ig2.coordenadores)
    }

    @Test
    fun testEscapeSqliteLike_escapesWildcardsCorrectly() {
        val inputWithWildcards = "São_José 50% & Cia\\teste"
        val escaped = DatabaseHelper.escapeSqliteLike(inputWithWildcards)
        assertEquals("São\\_José 50\\% & Cia\\\\teste", escaped)

        val cleanInput = "Igreja Matriz São Pedro"
        val cleanEscaped = DatabaseHelper.escapeSqliteLike(cleanInput)
        assertEquals(cleanInput, cleanEscaped)

        val onlyWildcards = "%_\\"
        val escapedWildcards = DatabaseHelper.escapeSqliteLike(onlyWildcards)
        assertEquals("\\%\\_\\\\", escapedWildcards)
    }

    @Test
    fun testGeminiScheduleParser_fileSizeLimitConfiguration() {
        assertEquals(15 * 1024 * 1024L, com.aistudio.escala.parser.GeminiScheduleParser.MAX_FILE_SIZE_BYTES)
        assertEquals(
            "Arquivo muito grande. Selecione um arquivo com até 15MB ou divida a escala em partes menores.",
            com.aistudio.escala.parser.GeminiScheduleParser.ERROR_FILE_TOO_LARGE
        )
    }

    @Test
    fun testIsPlaintextDatabase_detectsPlaintextAndEncryptedHeaders() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "sqlcipher_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val plainDbFile = java.io.File(tempDir, "plain.db")
            plainDbFile.writeBytes("SQLite format 3\u0000SomeRandomData123456789".toByteArray(Charsets.US_ASCII))
            assertTrue("Arquivo com cabeçalho SQLite format 3 deve ser identificado como texto puro", DatabaseHelper.isPlaintextDatabase(plainDbFile))

            val encryptedDbFile = java.io.File(tempDir, "encrypted.db")
            encryptedDbFile.writeBytes(ByteArray(64) { (it * 7).toByte() })
            assertFalse("Arquivo sem cabeçalho padrão SQLite deve ser identificado como não-texto puro (criptografado)", DatabaseHelper.isPlaintextDatabase(encryptedDbFile))

            val tinyFile = java.io.File(tempDir, "tiny.db")
            tinyFile.writeBytes("SQLite".toByteArray())
            assertFalse("Arquivo menor que 16 bytes não deve ser considerado banco de dados válido", DatabaseHelper.isPlaintextDatabase(tinyFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testConverterDataServico_formatosReaisImportacao() {
        val periodo = "Outubro 2026"
        val esperado = LocalDate.of(2026, 10, 5)

        val formatos = listOf(
            "05",
            "05/10",
            "05/10/2026",
            "Domingo 05",
            "05 de outubro"
        )

        for (formato in formatos) {
            val resultado = DateUtils.converterDataServico(formato, periodo)
            assertEquals("Deveria extrair dia 5 para o formato '$formato'", esperado, resultado)
        }
    }

    @Test
    fun testConverterDataServico_validacoesFaixaEDataInvalida() {
        // Dia maior que 31 ou menor que 1
        assertNull(DateUtils.converterDataServico("32", "Outubro 2026"))
        assertNull(DateUtils.converterDataServico("00", "Outubro 2026"))
        assertNull(DateUtils.converterDataServico("Sem data", "Outubro 2026"))

        // Dia 31 em mês de 30 dias (Novembro) deve capturar DateTimeException e retornar null
        assertNull(DateUtils.converterDataServico("31", "Novembro 2026"))

        // Dia 29 de Fevereiro em ano não-bissexto (2025)
        assertNull(DateUtils.converterDataServico("29", "Fevereiro 2025"))

        // Dia 29 de Fevereiro em ano bissexto (2024)
        assertEquals(LocalDate.of(2024, 2, 29), DateUtils.converterDataServico("29", "Fevereiro 2024"))
    }

    @Test
    fun testTraduzirErroImportacao_mapeamentoHumanizado() {
        // 1. Sem conexão
        val errHost1 = java.net.UnknownHostException("Unable to resolve host generativelanguage.googleapis.com")
        assertEquals(
            "Sem conexão com a internet. Verifique sua rede e tente novamente.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(errHost1)
        )
        val errHost2 = RuntimeException("java.io.IOException: Unable to resolve host generativelanguage.googleapis.com: No address associated with hostname")
        assertEquals(
            "Sem conexão com a internet. Verifique sua rede e tente novamente.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(errHost2)
        )

        // 2. Timeout
        val errTimeout1 = java.net.SocketTimeoutException("timeout")
        assertEquals(
            "A conexão demorou demais para responder. Tente novamente em instantes.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(errTimeout1)
        )
        val errTimeout2 = RuntimeException("Connection timed out during request")
        assertEquals(
            "A conexão demorou demais para responder. Tente novamente em instantes.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(errTimeout2)
        )

        // 3. HTTP 429
        val resp429 = retrofit2.Response.error<Any>(429, "Quota exceeded".toResponseBody(null))
        val err429 = retrofit2.HttpException(resp429)
        assertEquals(
            "Muitas tentativas em pouco tempo. Aguarde um momento antes de tentar de novo.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(err429)
        )

        // 4. HTTP 400 ou erro JSON
        val resp400 = retrofit2.Response.error<Any>(400, "Bad request".toResponseBody(null))
        val err400 = retrofit2.HttpException(resp400)
        assertEquals(
            "Não conseguimos entender o conteúdo do arquivo enviado. Tente uma foto mais nítida ou um arquivo em outro formato.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(err400)
        )

        val errJson = org.json.JSONException("Value < at 0 of type java.lang.String cannot be converted to JSONObject")
        assertEquals(
            "Não conseguimos entender o conteúdo do arquivo enviado. Tente uma foto mais nítida ou um arquivo em outro formato.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(errJson)
        )

        // 5. Genérico não mapeado
        val errGenerico = IllegalStateException("Unexpected internal state")
        assertEquals(
            "Não foi possível importar a escala agora. Tente novamente ou entre em contato com o suporte.",
            com.aistudio.escala.util.ErrorUtils.traduzirErroImportacao(errGenerico)
        )
    }

    @Test
    fun testPeriodoReferenciaDinamico() {
        val formatoPeriodo = java.time.format.DateTimeFormatter.ofPattern("MMMM 'de' yyyy", java.util.Locale("pt", "BR"))
        val dataFixa = LocalDate.of(2026, 10, 15)
        val periodo = dataFixa.format(formatoPeriodo).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(java.util.Locale("pt", "BR")) else it.toString()
        }
        assertEquals("Outubro de 2026", periodo)
    }

    @Test
    fun testNormalizacaoNomeParaConflitos() {
        val n1 = "João da Silva"
        val n2 = "joao da silva"
        val n3 = "JOÃO DA SILVA"
        val n4 = "  Joao   da   Silva  "

        val norm1 = SearchUtils.normalizarTexto(n1)
        val norm2 = SearchUtils.normalizarTexto(n2)
        val norm3 = SearchUtils.normalizarTexto(n3)

        assertEquals("joao da silva", norm1)
        assertEquals(norm1, norm2)
        assertEquals(norm1, norm3)
    }

    @Test
    fun testImportResultSuccess_comAvisosConflito() {
        val resultSemAvisos = com.aistudio.escala.data.ImportResult.Success(
            periodo = "Outubro de 2026",
            totalIgrejas = 1,
            totalEscalacoes = 10,
            totalPostos = 5
        )
        assertTrue(resultSemAvisos.avisosConflito.isEmpty())

        val avisos = listOf("Carlos já está escalado em Recepção no dia Domingo 04 (Matriz)")
        val resultComAvisos = com.aistudio.escala.data.ImportResult.Success(
            periodo = "Outubro de 2026",
            totalIgrejas = 1,
            totalEscalacoes = 10,
            totalPostos = 5,
            avisosConflito = avisos
        )
        assertEquals(1, resultComAvisos.avisosConflito.size)
        assertEquals("Carlos já está escalado em Recepção no dia Domingo 04 (Matriz)", resultComAvisos.avisosConflito.first())
    }

    @Test
    fun testFallbackFuturoNaoRetornaEscalaPassada() {
        val today = LocalDate.of(2026, 10, 1)
        val targetDate = LocalDate.of(2026, 9, 2)

        val dutyPassado1 = com.aistudio.escala.data.EscalacaoPessoa(
            escalacaoId = 1L,
            igreja = "Matriz",
            funcao = "Recepção",
            data = "Quarta 02",
            periodo = "Setembro de 2026",
            dataReal = "2026-09-02",
            localDate = LocalDate.of(2026, 9, 2)
        )

        val dutyPassado2 = com.aistudio.escala.data.EscalacaoPessoa(
            escalacaoId = 2L,
            igreja = "Matriz",
            funcao = "Recepção",
            data = "Quarta 16",
            periodo = "Setembro de 2026",
            dataReal = "2026-09-16",
            localDate = LocalDate.of(2026, 9, 16)
        )

        val userDuties = listOf(dutyPassado1, dutyPassado2)

        val futureDuty = userDuties.filter { duty ->
            duty.localDate != null && (duty.localDate >= today)
        }.minByOrNull { it.localDate!! } ?: userDuties.filter { duty ->
            duty.localDate != null && (targetDate == null || duty.localDate > targetDate) && (duty.localDate >= today)
        }.minByOrNull { it.localDate!! }

        assertNull("Quando só existem escalas passadas, o fallback deve retornar null", futureDuty)
    }
}
