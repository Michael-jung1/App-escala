package com.aistudio.escala.importacao.repositorio

import android.content.Context
import com.aistudio.escala.data.DatabaseHelper
import com.aistudio.escala.importacao.dados.CadastroRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementação do [CadastroRepository] conectada à base de dados real SQLite (via SQLCipher).
 *
 * Conforme especificado, extrai a lista de nomes distintos já conhecidos
 * na tabela `escalacao` (coluna `pessoa_nome`).
 */
class SqliteCadastroRepository(
    private val databaseHelper: DatabaseHelper
) : CadastroRepository {

    constructor(context: Context) : this(DatabaseHelper.getInstance(context.applicationContext))

    override suspend fun obterNomesCadastrados(): Set<String> = withContext(Dispatchers.IO) {
        val nomes = mutableSetOf<String>()
        val db = databaseHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT DISTINCT pessoa_nome 
            FROM escalacao 
            WHERE pessoa_nome IS NOT NULL AND TRIM(pessoa_nome) != ''
            ORDER BY pessoa_nome COLLATE NOCASE
            """.trimIndent(),
            null
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val nome = c.getString(0)?.trim()
                if (!nome.isNullOrBlank()) {
                    nomes.add(nome)
                }
            }
        }

        nomes
    }
}
