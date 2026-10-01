package com.aistudio.escala.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.SearchUtils
import com.aistudio.escala.util.SecurityUtils
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        const val DB_NAME = "escala.db"
        const val DB_VERSION = 2

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        const val MAX_NOME_LENGTH = 100
        const val MAX_FUNCAO_LENGTH = 80
        const val MAX_IGREJA_LENGTH = 120
        const val MAX_DATA_LENGTH = 50
        const val MAX_PERIODO_LENGTH = 60
        const val MAX_CHAVE_LENGTH = 64

        fun sanitizeInput(input: String?, maxLength: Int): String {
            if (input == null) return ""
            val limpo = input.replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "").trim()
            return if (limpo.length > maxLength) limpo.substring(0, maxLength).trim() else limpo
        }

        /**
         * Escapa caracteres especiais do operador LIKE no SQLite (%, _ e \)
         * permitindo que sejam tratados como literais com a cláusula ESCAPE '\'.
         */
        fun escapeSqliteLike(input: String): String {
            return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
        }

        /**
         * Verifica se um arquivo de banco de dados é SQLite padrão em texto puro (não criptografado).
         * Arquivos SQLite em texto puro sempre iniciam com o cabeçalho ASCII "SQLite format 3\u0000".
         */
        fun isPlaintextDatabase(file: File): Boolean {
            if (!file.exists() || file.length() < 16) return false
            return try {
                file.inputStream().use { input ->
                    val header = ByteArray(16)
                    val bytesRead = input.read(header)
                    if (bytesRead == 16) {
                        val headerStr = String(header, Charsets.US_ASCII)
                        headerStr.startsWith("SQLite format 3")
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                false
            }
        }

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val passphrase: String
        get() = SecurityUtils.getDatabasePassphrase(context)

    val writableDatabase: SQLiteDatabase
        get() = getWritableDatabase(passphrase)

    val readableDatabase: SQLiteDatabase
        get() = getReadableDatabase(passphrase)

    init {
        try {
            SQLiteDatabase.loadLibs(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao carregar bibliotecas nativas do SQLCipher", e)
        }
        ensureEncryptedDatabase()
    }

    private fun ensureEncryptedDatabase() {
        val dbFile = context.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()

        val tempPlainFile = File(context.cacheDir, "temp_escala_plain.db")
        try {
            if (!dbFile.exists()) {
                // Primeira execução: copia o escala.db de assets para arquivo temporário e criptografa em repouso
                Log.d(TAG, "Primeira execução: copiando escala.db de assets e criptografando com SQLCipher...")
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(tempPlainFile).use { output ->
                        input.copyTo(output)
                    }
                }
                encryptPlaintextDatabase(tempPlainFile, dbFile, passphrase)
                Log.d(TAG, "Banco escala.db criado e criptografado com sucesso em repouso.")
            } else if (isPlaintextDatabase(dbFile)) {
                // Migração transparente de banco legado em texto puro para criptografado em repouso
                Log.d(TAG, "Detectado escala.db em texto puro. Migrando dados para formato criptografado SQLCipher...")
                if (tempPlainFile.exists()) tempPlainFile.delete()
                val renamed = dbFile.renameTo(tempPlainFile)
                if (!renamed) {
                    dbFile.inputStream().use { input ->
                        FileOutputStream(tempPlainFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    dbFile.delete()
                }
                encryptPlaintextDatabase(tempPlainFile, dbFile, passphrase)
                Log.d(TAG, "Migração para banco criptografado SQLCipher concluída com sucesso.")
            } else {
                Log.d(TAG, "Banco escala.db já está devidamente criptografado com SQLCipher.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante a inicialização/criptografia do banco de dados", e)
        } finally {
            if (tempPlainFile.exists()) {
                try {
                    // Sanitiza o arquivo temporário sobrescrevendo os primeiros bytes antes de excluir
                    tempPlainFile.writeBytes(ByteArray(minOf(tempPlainFile.length().toInt(), 4096)))
                } catch (_: Exception) {}
                tempPlainFile.delete()
            }
            // Limpa eventuais arquivos residuais de WAL/SHM legados
            File(dbFile.parentFile, "$DB_NAME-wal").delete()
            File(dbFile.parentFile, "$DB_NAME-shm").delete()
            File(dbFile.parentFile, "$DB_NAME-journal").delete()
        }
    }

    private fun encryptPlaintextDatabase(sourcePlainFile: File, targetEncryptedFile: File, key: String) {
        if (targetEncryptedFile.exists()) {
            targetEncryptedFile.delete()
        }
        val plainDb = SQLiteDatabase.openOrCreateDatabase(sourcePlainFile, "", null)
        try {
            val escapedPath = targetEncryptedFile.absolutePath.replace("'", "''")
            val escapedKey = key.replace("'", "''")
            plainDb.rawExecSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY '$escapedKey';")
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            plainDb.rawExecSQL("DETACH DATABASE encrypted;")
        } finally {
            plainDb.close()
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Database is copied from pre-populated asset
        garantirColunaIsAdmin(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "Upgrading database from version $oldVersion to $newVersion")
        if (oldVersion < 2) {
            garantirColunaIsAdmin(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        garantirColunaIsAdmin(db)
        garantirMigracaoHashes(db)
    }

    private fun garantirMigracaoHashes(db: SQLiteDatabase) {
        try {
            val cursor = db.rawQuery("SELECT id, chave_acesso FROM coordenador", null)
            val pendentes = mutableListOf<Pair<Long, String>>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val chave = c.getString(1) ?: ""
                    if (chave.isNotBlank() && !SecurityUtils.isHash(chave)) {
                        pendentes.add(Pair(id, chave))
                    }
                }
            }
            for ((id, plain) in pendentes) {
                val hashed = SecurityUtils.hashAccessCode(plain.uppercase())
                val cv = ContentValues().apply {
                    put("chave_acesso", hashed)
                }
                db.update("coordenador", cv, "id = ?", arrayOf(id.toString()))
                Log.d(TAG, "Chave do coordenador id=$id migrada para hash SHA-256 com sucesso.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao migrar hashes de coordenadores", e)
        }
    }

    private fun garantirColunaIsAdmin(db: SQLiteDatabase) {
        try {
            var columnExists = false
            val cursor = db.rawQuery("PRAGMA table_info(coordenador)", null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    val colName = c.getString(1)
                    if (colName.equals("is_admin", ignoreCase = true)) {
                        columnExists = true
                        break
                    }
                }
            }
            if (!columnExists) {
                db.execSQL("ALTER TABLE coordenador ADD COLUMN is_admin INTEGER DEFAULT 0")
                Log.d(TAG, "Coluna is_admin adicionada com sucesso à tabela coordenador.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao garantir coluna is_admin", e)
        }
    }

    fun listarIgrejas(): List<Igreja> {
        val list = mutableListOf<Igreja>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, nome, titulo_escala, coordenadores FROM igreja ORDER BY nome",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    Igreja(
                        id = c.getLong(0),
                        nome = c.getString(1),
                        tituloEscala = if (c.isNull(2)) null else c.getString(2),
                        coordenadores = if (c.isNull(3)) null else c.getString(3)
                    )
                )
            }
        }
        return list
    }

    fun listarPessoas(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT TRIM(pessoa_nome) AS pessoa_nome
            FROM escalacao
            WHERE pessoa_nome IS NOT NULL
              AND TRIM(pessoa_nome) != ''
            ORDER BY pessoa_nome COLLATE NOCASE
            """.trimIndent(),
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val nome = c.getString(0)
                if (!nome.isNullOrBlank()) {
                    list.add(nome.trim())
                }
            }
        }
        return list
    }

    fun buscarEscalaPessoa(nomeBusca: String, apenasFuturas: Boolean = true): List<EscalacaoPessoa> {
        val trimmed = nomeBusca.trim()
        if (trimmed.isEmpty()) return emptyList()

        val list = mutableListOf<EscalacaoPessoa>()
        val db = readableDatabase
        val hoje = LocalDate.now()

        // 1. Tenta consulta direta com LIKE e o termo fornecido
        val cursor = db.rawQuery(
            """
            SELECT
                igreja.nome AS igreja,
                posto.funcao AS funcao,
                escalacao.data_servico AS data,
                periodo_escala.referencia AS periodo,
                escalacao.pessoa_nome AS pessoa
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
            JOIN igreja ON igreja.id = periodo_escala.igreja_id
            WHERE escalacao.pessoa_nome LIKE ? ESCAPE '\' COLLATE NOCASE
            """.trimIndent(),
            arrayOf("%${escapeSqliteLike(trimmed)}%")
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val igreja = c.getString(0)
                val funcao = c.getString(1)
                val data = c.getString(2)
                val periodo = c.getString(3)

                val localDate = DateUtils.converterDataServico(data, periodo)
                val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }

                if (apenasFuturas && localDate != null && localDate.isBefore(hoje)) {
                    continue
                }

                list.add(
                    EscalacaoPessoa(
                        igreja = igreja,
                        funcao = funcao,
                        data = data,
                        periodo = periodo,
                        dataReal = dataReal,
                        localDate = localDate
                    )
                )
            }
        }

        // 2. Se a busca direta não retornar nada (ou para termos com acentos/typos como "joao" vs "João"),
        // busca todas as escalações e aplica normalização NFD e fuzzy search do SearchUtils
        if (list.isEmpty()) {
            val cursorAll = db.rawQuery(
                """
                SELECT
                    igreja.nome AS igreja,
                    posto.funcao AS funcao,
                    escalacao.data_servico AS data,
                    periodo_escala.referencia AS periodo,
                    escalacao.pessoa_nome AS pessoa
                FROM escalacao
                JOIN posto ON posto.id = escalacao.posto_id
                JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
                JOIN igreja ON igreja.id = periodo_escala.igreja_id
                """.trimIndent(),
                null
            )

            cursorAll.use { c ->
                while (c.moveToNext()) {
                    val pessoaNome = c.getString(4)
                    if (SearchUtils.correspondeBusca(pessoaNome, trimmed)) {
                        val igreja = c.getString(0)
                        val funcao = c.getString(1)
                        val data = c.getString(2)
                        val periodo = c.getString(3)

                        val localDate = DateUtils.converterDataServico(data, periodo)
                        val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }

                        if (apenasFuturas && localDate != null && localDate.isBefore(hoje)) {
                            continue
                        }

                        list.add(
                            EscalacaoPessoa(
                                igreja = igreja,
                                funcao = funcao,
                                data = data,
                                periodo = periodo,
                                dataReal = dataReal,
                                localDate = localDate
                            )
                        )
                    }
                }
            }
        }

        list.sortWith(compareBy({ it.dataReal == null }, { it.dataReal ?: "" }))
        return list
    }

    fun buscarEscalaDoDia(dataServico: String): List<EscalacaoDia> {
        val list = mutableListOf<EscalacaoDia>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT
                escalacao.id AS escalacao_id,
                igreja.nome AS igreja,
                posto.funcao AS funcao,
                escalacao.pessoa_nome AS pessoa
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
            JOIN igreja ON igreja.id = periodo_escala.igreja_id
            WHERE escalacao.data_servico = ? COLLATE NOCASE
            ORDER BY igreja.nome, posto.ordem_na_planilha
            """.trimIndent(),
            arrayOf(dataServico)
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    EscalacaoDia(
                        escalacaoId = c.getLong(0),
                        igreja = c.getString(1),
                        funcao = c.getString(2),
                        pessoa = c.getString(3)
                    )
                )
            }
        }
        return list
    }

    fun listarDatasDisponiveis(): List<DataDisponivel> {
        val list = mutableListOf<DataDisponivel>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT escalacao.data_servico, periodo_escala.referencia
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
            ORDER BY escalacao.data_servico
            """.trimIndent(),
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val dataServico = c.getString(0)
                val periodo = c.getString(1)
                val localDate = DateUtils.converterDataServico(dataServico, periodo)
                val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }
                list.add(
                    DataDisponivel(
                        dataServico = dataServico,
                        periodo = periodo,
                        dataReal = dataReal,
                        localDate = localDate
                    )
                )
            }
        }
        list.sortWith(compareBy({ it.localDate == null }, { it.localDate }))
        return list
    }

    fun autenticarCoordenador(chaveAcesso: String): Coordenador? {
        val chaveLimpa = chaveAcesso.trim()
        if (chaveLimpa.isEmpty()) return null

        val db = readableDatabase
        var coordenador: Coordenador? = null

        val cursor = db.rawQuery(
            "SELECT id, nome, chave_acesso, is_admin FROM coordenador",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val nome = c.getString(1)
                val chaveArmazenada = c.getString(2) ?: ""
                val isAdmin = if (c.isNull(3)) false else c.getInt(3) == 1

                val isMatch = if (SecurityUtils.isHash(chaveLimpa)) {
                    // Login automático com hash salvo: compara o hash com as chaves ou hashes do banco via SecurityUtils.verifyAccessCode
                    chaveLimpa == chaveArmazenada ||
                    SecurityUtils.verifyAccessCode(chaveArmazenada, chaveLimpa) ||
                    SecurityUtils.verifyAccessCode(chaveLimpa, chaveArmazenada)
                } else {
                    SecurityUtils.verifyAccessCode(chaveLimpa.uppercase(), chaveArmazenada)
                }

                if (isMatch) {
                    val hashFinal = if (SecurityUtils.isHash(chaveArmazenada)) {
                        chaveArmazenada
                    } else if (SecurityUtils.isHash(chaveLimpa)) {
                        chaveLimpa
                    } else {
                        SecurityUtils.hashAccessCode(chaveLimpa.uppercase())
                    }

                    // Migra chave em texto puro para hash se ainda não foi migrada
                    if (!SecurityUtils.isHash(chaveArmazenada)) {
                        val cv = ContentValues().apply { put("chave_acesso", hashFinal) }
                        writableDatabase.update("coordenador", cv, "id = ?", arrayOf(id.toString()))
                    }

                    // Obter igrejas associadas
                    val igrejas = mutableListOf<Igreja>()
                    val cursorIgrejas = db.rawQuery(
                        """
                        SELECT igreja.id, igreja.nome, igreja.titulo_escala, igreja.coordenadores
                        FROM coordenador_igreja
                        JOIN igreja ON igreja.id = coordenador_igreja.igreja_id
                        WHERE coordenador_igreja.coordenador_id = ?
                        ORDER BY igreja.nome
                        """.trimIndent(),
                        arrayOf(id.toString())
                    )
                    cursorIgrejas.use { ci ->
                        while (ci.moveToNext()) {
                            igrejas.add(
                                Igreja(
                                    id = ci.getLong(0),
                                    nome = ci.getString(1),
                                    tituloEscala = if (ci.isNull(2)) null else ci.getString(2),
                                    coordenadores = if (ci.isNull(3)) null else ci.getString(3)
                                )
                            )
                        }
                    }

                    coordenador = Coordenador(
                        id = id,
                        nome = nome,
                        chaveAcesso = hashFinal, // Armazena apenas o hash seguro com salt, nunca a senha original
                        igrejas = if (igrejas.isNotEmpty()) igrejas else listarIgrejas(),
                        isAdmin = isAdmin
                    )
                    break
                }
            }
        }
        return coordenador
    }

    fun validarSenhaCoordenador(chaveDigitada: String): ValidacaoSenhaResult {
        val coord = autenticarCoordenador(chaveDigitada)
        return if (coord != null) {
            ValidacaoSenhaResult(isValid = true, isAdmin = coord.isAdmin)
        } else {
            ValidacaoSenhaResult(isValid = false, isAdmin = false)
        }
    }

    fun obterSenhaCoordenador(): String {
        val db = readableDatabase
        var hashChave = ""
        try {
            val cursor = db.rawQuery("SELECT id, nome, chave_acesso, is_admin FROM coordenador", null)
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val s = c.getString(2)
                    if (!s.isNullOrBlank()) {
                        hashChave = if (SecurityUtils.isHash(s)) s else SecurityUtils.hashAccessCode(s.trim().uppercase())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter hash da senha do coordenador", e)
        }
        return hashChave
    }

    fun listarTodosCoordenadores(): List<Coordenador> {
        val db = readableDatabase
        val list = mutableListOf<Coordenador>()
        try {
            val cursor = db.rawQuery(
                "SELECT id, nome, chave_acesso, COALESCE(is_admin, 0) FROM coordenador ORDER BY is_admin DESC, nome ASC",
                null
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(
                        Coordenador(
                            id = c.getLong(0),
                            nome = c.getString(1),
                            chaveAcesso = "••••••••", // Mascarado para não exibir senhas ou hashes na tela
                            igrejas = emptyList(),
                            isAdmin = c.getInt(3) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao listar coordenadores", e)
        }
        return list
    }

    fun salvarCoordenadorAcesso(nome: String, chaveAcesso: String, isAdmin: Boolean): Boolean {
        val nomeSanitizado = sanitizeInput(nome, MAX_NOME_LENGTH)
        val chaveLimpa = sanitizeInput(chaveAcesso, MAX_CHAVE_LENGTH).uppercase()
        if (nomeSanitizado.isEmpty() || chaveLimpa.isEmpty()) return false

        val db = writableDatabase
        return try {
            val chaveArmazenar = if (SecurityUtils.isHash(chaveLimpa)) chaveLimpa else SecurityUtils.hashAccessCode(chaveLimpa)
            val cv = ContentValues().apply {
                put("nome", nomeSanitizado)
                put("chave_acesso", chaveArmazenar)
                put("is_admin", if (isAdmin) 1 else 0)
            }
            val id = db.insertWithOnConflict("coordenador", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            if (id > 0) {
                // Se o coordenador não tiver vínculos em coordenador_igreja, vincula às igrejas disponíveis
                val cursorCount = db.rawQuery("SELECT COUNT(*) FROM coordenador_igreja WHERE coordenador_id = ?", arrayOf(id.toString()))
                val count = cursorCount.use { if (it.moveToFirst()) it.getInt(0) else 0 }
                if (count == 0) {
                    val cursorIgrejas = db.rawQuery("SELECT id FROM igreja", null)
                    cursorIgrejas.use { ci ->
                        while (ci.moveToNext()) {
                            val igrejaId = ci.getLong(0)
                            val cvIgreja = ContentValues().apply {
                                put("coordenador_id", id)
                                put("igreja_id", igrejaId)
                            }
                            db.insertWithOnConflict("coordenador_igreja", null, cvIgreja, SQLiteDatabase.CONFLICT_IGNORE)
                        }
                    }
                }
            }
            id > 0
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar coordenador", e)
            false
        }
    }

    fun atualizarCoordenadorAcesso(id: Long, nome: String, chaveAcesso: String, isAdmin: Boolean): Boolean {
        val nomeSanitizado = sanitizeInput(nome, MAX_NOME_LENGTH)
        if (nomeSanitizado.isEmpty()) return false

        val db = writableDatabase
        return try {
            val cv = ContentValues().apply {
                put("nome", nomeSanitizado)
                val chaveLimpa = sanitizeInput(chaveAcesso, MAX_CHAVE_LENGTH).uppercase()
                if (chaveLimpa.isNotBlank()) {
                    val chaveArmazenar = if (SecurityUtils.isHash(chaveLimpa)) chaveLimpa else SecurityUtils.hashAccessCode(chaveLimpa)
                    put("chave_acesso", chaveArmazenar)
                }
                put("is_admin", if (isAdmin) 1 else 0)
            }
            val rows = db.update("coordenador", cv, "id = ?", arrayOf(id.toString()))
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar coordenador", e)
            false
        }
    }

    fun excluirCoordenadorAcesso(id: Long): Boolean {
        val db = writableDatabase
        return try {
            val rows = db.delete("coordenador", "id = ?", arrayOf(id.toString()))
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir coordenador", e)
            false
        }
    }

    fun listarPostosDaIgreja(igrejaId: Long, apenasFuturas: Boolean = false): List<DiaComPostos> {
        val db = readableDatabase
        val formatoPeriodo = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR"))
        var periodoReferencia = LocalDate.now().format(formatoPeriodo).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString()
        }
        val cursorPeriodo = db.rawQuery(
            """
            SELECT id, referencia FROM periodo_escala
            WHERE igreja_id = ?
            ORDER BY importado_em DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(igrejaId.toString())
        )
        var periodoId: Long = -1
        cursorPeriodo.use { cp ->
            if (cp.moveToFirst()) {
                periodoId = cp.getLong(0)
                periodoReferencia = cp.getString(1)
            }
        }

        if (periodoId == -1L) return emptyList()

        val cursor = db.rawQuery(
            """
            SELECT escalacao.id, escalacao.data_servico, escalacao.pessoa_nome, posto.funcao
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            WHERE posto.periodo_escala_id = ?
            ORDER BY posto.ordem_na_planilha
            """.trimIndent(),
            arrayOf(periodoId.toString())
        )

        val hoje = LocalDate.now()
        val mapaDias = linkedMapOf<String, MutableList<PostoItem>>()
        val mapaDatasReais = mutableMapOf<String, Pair<String?, LocalDate?>>()

        cursor.use { c ->
            while (c.moveToNext()) {
                val escalacaoId = c.getLong(0)
                val dataTexto = c.getString(1)
                val pessoaNome = c.getString(2)
                val funcao = c.getString(3)

                if (!mapaDatasReais.containsKey(dataTexto)) {
                    val localDate = DateUtils.converterDataServico(dataTexto, periodoReferencia)
                    val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }
                    mapaDatasReais[dataTexto] = Pair(dataReal, localDate)
                }

                val (_, localDate) = mapaDatasReais[dataTexto] ?: Pair(null, null)
                if (apenasFuturas && localDate != null && localDate.isBefore(hoje)) {
                    continue
                }

                val list = mapaDias.getOrPut(dataTexto) { mutableListOf() }
                list.add(PostoItem(escalacaoId, funcao, pessoaNome))
            }
        }

        val resultado = mapaDias.map { (dataTexto, postos) ->
            val (dataReal, localDate) = mapaDatasReais[dataTexto] ?: Pair(null, null)
            DiaComPostos(
                dataServico = dataTexto,
                dataReal = dataReal,
                localDate = localDate,
                postos = postos
            )
        }.toMutableList()

        resultado.sortWith(compareBy({ it.localDate == null }, { it.localDate }))
        return resultado
    }

    fun atualizarEscalacao(escalacaoId: Long, novoNome: String, coordenadorId: Long): Boolean {
        if (coordenadorId <= 0) {
            Log.w(TAG, "Tentativa de atualizar escalação sem coordenador autenticado")
            return false
        }
        val sanitizado = sanitizeInput(novoNome, MAX_NOME_LENGTH)
        if (sanitizado.isEmpty()) return false

        val db = writableDatabase

        // Validação de segurança: autorização baseada em vínculo com a igreja daquela escalação
        // JOIN escalacao -> posto -> periodo_escala -> igreja -> coordenador_igreja
        val checkPermissaoQuery = """
            SELECT 1
            FROM escalacao e
            JOIN posto p ON p.id = e.posto_id
            JOIN periodo_escala pe ON pe.id = p.periodo_escala_id
            JOIN igreja i ON i.id = pe.igreja_id
            LEFT JOIN coordenador_igreja ci ON ci.igreja_id = i.id AND ci.coordenador_id = ?
            JOIN coordenador c ON c.id = ?
            WHERE e.id = ? AND (ci.coordenador_id IS NOT NULL OR c.is_admin = 1)
            LIMIT 1
        """.trimIndent()

        val temPermissao = try {
            val cursor = db.rawQuery(
                checkPermissaoQuery,
                arrayOf(coordenadorId.toString(), coordenadorId.toString(), escalacaoId.toString())
            )
            cursor.use { c -> c.moveToFirst() }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao validar permissões de atualização para escalação $escalacaoId e coordenador $coordenadorId", e)
            false
        }

        if (!temPermissao) {
            Log.e(TAG, "Acesso negado: coordenador $coordenadorId não possui vínculo com a igreja da escalação $escalacaoId")
            return false
        }

        val cv = ContentValues().apply {
            put("pessoa_nome", sanitizado)
        }
        val rows = db.update("escalacao", cv, "id = ?", arrayOf(escalacaoId.toString()))
        return rows > 0
    }

    fun listarPeriodos(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT DISTINCT referencia FROM periodo_escala ORDER BY importado_em DESC",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(c.getString(0))
            }
        }
        return list
    }

    fun importarEscalaCompleta(resultado: ParsedEscala): ImportResult {
        val periodoSanitizado = sanitizeInput(resultado.periodo, MAX_PERIODO_LENGTH)
        if (periodoSanitizado.isEmpty()) {
            return ImportResult.Error("Período da escala inválido ou não informado.")
        }

        val db = writableDatabase
        db.beginTransaction()
        try {
            var totalEscalacoes = 0
            var totalIgrejas = 0
            var totalPostos = 0

            for (igrejaParsed in resultado.igrejas) {
                val nomeIgreja = sanitizeInput(igrejaParsed.nome, MAX_IGREJA_LENGTH)
                if (nomeIgreja.isEmpty()) continue

                // 1. Obter ou criar igreja
                var igrejaId: Long = -1

                // Avaliação relacional: primeiro tenta correspondência exata para evitar falsos positivos
                // (ex: "São José" casando incorretamente com "São José do Rio").
                val cursorExato = db.rawQuery(
                    "SELECT id FROM igreja WHERE nome = ? COLLATE NOCASE",
                    arrayOf(nomeIgreja)
                )
                cursorExato.use { ce ->
                    if (ce.moveToFirst()) {
                        igrejaId = ce.getLong(0)
                    }
                }

                // Fallback seguro: se não encontrar correspondência exata, busca por LIKE com escape de %, _ e \
                if (igrejaId == -1L) {
                    val padraoEscapado = "%${escapeSqliteLike(nomeIgreja)}%"
                    val cursorLike = db.rawQuery(
                        "SELECT id FROM igreja WHERE nome LIKE ? ESCAPE '\\' COLLATE NOCASE",
                        arrayOf(padraoEscapado)
                    )
                    cursorLike.use { cl ->
                        if (cl.moveToFirst()) {
                            igrejaId = cl.getLong(0)
                        }
                    }
                }

                if (igrejaId != -1L) {
                    val cvUpdate = ContentValues().apply {
                        igrejaParsed.titulo?.let { put("titulo_escala", sanitizeInput(it, 150)) }
                        igrejaParsed.coordenadores?.let { put("coordenadores", sanitizeInput(it, 150)) }
                    }
                    if (cvUpdate.size() > 0) {
                        db.update("igreja", cvUpdate, "id = ?", arrayOf(igrejaId.toString()))
                    }
                } else {
                    val cvIgreja = ContentValues().apply {
                        put("nome", nomeIgreja)
                        put("titulo_escala", sanitizeInput(igrejaParsed.titulo, 150))
                        put("coordenadores", sanitizeInput(igrejaParsed.coordenadores, 150))
                    }
                    igrejaId = db.insert("igreja", null, cvIgreja)
                }

                if (igrejaId <= 0) continue
                totalIgrejas++

                // 2. Obter ou criar periodo_escala
                var periodoId: Long = -1
                val cursorPeriodo = db.rawQuery(
                    "SELECT id FROM periodo_escala WHERE igreja_id = ? AND referencia = ?",
                    arrayOf(igrejaId.toString(), periodoSanitizado)
                )
                cursorPeriodo.use { cp ->
                    if (cp.moveToFirst()) {
                        periodoId = cp.getLong(0)
                        // Limpa postos anteriores deste período para re-importação limpa
                        db.delete("posto", "periodo_escala_id = ?", arrayOf(periodoId.toString()))
                    }
                }

                val nowIso = java.time.LocalDateTime.now().toString()
                if (periodoId == -1L) {
                    val cvPeriodo = ContentValues().apply {
                        put("igreja_id", igrejaId)
                        put("referencia", periodoSanitizado)
                        put("arquivo_origem", sanitizeInput(resultado.arquivoOrigem, 120))
                        put("importado_em", nowIso)
                    }
                    periodoId = db.insert("periodo_escala", null, cvPeriodo)
                } else {
                    val cvPeriodo = ContentValues().apply {
                        put("arquivo_origem", sanitizeInput(resultado.arquivoOrigem, 120))
                        put("importado_em", nowIso)
                    }
                    db.update("periodo_escala", cvPeriodo, "id = ?", arrayOf(periodoId.toString()))
                }

                // 3. Inserir postos e escalações
                var ordem = 1
                for (postoParsed in igrejaParsed.postos) {
                    val funcaoSanitizada = sanitizeInput(DateUtils.normalizarFuncao(postoParsed.funcao), MAX_FUNCAO_LENGTH)
                    if (funcaoSanitizada.isEmpty()) continue

                    val cvPosto = ContentValues().apply {
                        put("periodo_escala_id", periodoId)
                        put("funcao", funcaoSanitizada)
                        put("ordem_na_planilha", ordem++)
                    }
                    val postoId = db.insert("posto", null, cvPosto)
                    if (postoId <= 0) continue
                    totalPostos++

                    for (esc in postoParsed.escalacoes) {
                        val pessoa = sanitizeInput(esc.pessoa, MAX_NOME_LENGTH)
                        val dataServico = sanitizeInput(esc.data, MAX_DATA_LENGTH)
                        if (pessoa.isEmpty() || dataServico.isEmpty()) continue

                        val cvEsc = ContentValues().apply {
                            put("posto_id", postoId)
                            put("data_servico", dataServico)
                            put("pessoa_nome", pessoa)
                        }
                        db.insertWithOnConflict("escalacao", null, cvEsc, SQLiteDatabase.CONFLICT_REPLACE)
                        totalEscalacoes++
                    }
                }
            }

            db.setTransactionSuccessful()
            return ImportResult.Success(
                periodo = periodoSanitizado,
                totalIgrejas = totalIgrejas,
                totalEscalacoes = totalEscalacoes,
                totalPostos = totalPostos
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro interno ao importar escala no SQLite", e)
            return ImportResult.Error("Não foi possível importar o arquivo. Verifique o formato e tente novamente.")
        } finally {
            db.endTransaction()
        }
    }
}
