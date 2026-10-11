package com.aistudio.escala.importacao.repositorio

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.aistudio.escala.data.DatabaseHelper
import com.aistudio.escala.importacao.dados.ConflitoEscala
import com.aistudio.escala.importacao.dados.LinhaEscala
import com.aistudio.escala.importacao.dados.ResumoGravacao
import com.aistudio.escala.importacao.dados.ScheduleRepository
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.SearchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Implementação determinística de [ScheduleRepository] usando o [DatabaseHelper] com SQLCipher.
 *
 * REGRAS IMPLEMENTADAS:
 * 1. MERGE, NÃO REPLACE: Nunca apaga postos nem escalações existentes que não aparecem na importação.
 * 2. Transação atômica via writableDatabase: beginTransaction() / setTransactionSuccessful() / endTransaction().
 * 3. Formato estrito de `data_servico`: Converte a data ISO (ex: "2026-09-06") para o formato nativo
 *    armazenado no banco: "DiaSemana DD" (ex: "Domingo 06").
 * 4. Múltiplos períodos: Se as linhas cruzarem meses, agrupa e grava no respectivo `periodo_escala`
 *    (ex: "Setembro de 2026", "Outubro de 2026"), respeitando UNIQUE(igreja_id, referencia).
 * 5. Resolução de Posto & Horário:
 *    Dentro de uma mesma igreja e período, se uma função litúrgica tiver mais de um horário distinto,
 *    cria o posto com funcao = "<funcao> <HH:mm>" (ex: "Missal 19:00"); se tiver apenas um horário,
 *    usa só "<funcao>".
 * 6. Ordem na planilha: Em novos postos, usa ordem_na_planilha = último daquele período + 1.
 * 7. Deduplicação e Substituição:
 *    Se já existir escalação para o mesmo posto e data_servico:
 *      - Se pessoa_nome for igual: conta como ATUALIZADO.
 *      - Se pessoa_nome for diferente: conta como SUBSTITUÍDO, atualiza o nome e ZERA `pessoa_user_id` (NULL).
 *    Se não existir escalação prévia: insere novo registro e conta como INSERIDO.
 * 8. Auditoria: preenche arquivo_origem = "importacao_foto" e importado_em com data/hora ISO atual.
 */
class SqliteScheduleRepository(
    private val databaseHelper: DatabaseHelper
) : ScheduleRepository {

    constructor(context: Context) : this(DatabaseHelper.getInstance(context.applicationContext))

    companion object {
        private const val TAG = "SqliteScheduleRepo"
        private val ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * Converte uma data ISO (ex: "2026-09-06") no formato padrão do banco (ex: "Domingo 06").
         */
        fun formatarDataServicoNativo(isoDateStr: String): String {
            val localDate = try {
                LocalDate.parse(isoDateStr.trim(), ISO_DATE_FORMAT)
            } catch (e: Exception) {
                return isoDateStr.trim()
            }

            val nomeDiaSemana = when (localDate.dayOfWeek.value) {
                1 -> "Segunda"
                2 -> "Terça"
                3 -> "Quarta"
                4 -> "Quinta"
                5 -> "Sexta"
                6 -> "Sábado"
                7 -> "Domingo"
                else -> ""
            }

            return "%s %02d".format(Locale.ROOT, nomeDiaSemana, localDate.dayOfMonth)
        }

        /**
         * Gera a referência do período com padrão de capitalização idêntico ao banco (ex: "Setembro de 2026").
         */
        fun formatarPeriodoReferencia(isoDateStr: String): String {
            val localDate = try {
                LocalDate.parse(isoDateStr.trim(), ISO_DATE_FORMAT)
            } catch (e: Exception) {
                return "Período Importado"
            }

            val nomeMes = when (localDate.monthValue) {
                1 -> "Janeiro"
                2 -> "Fevereiro"
                3 -> "Março"
                4 -> "Abril"
                5 -> "Maio"
                6 -> "Junho"
                7 -> "Julho"
                8 -> "Agosto"
                9 -> "Setembro"
                10 -> "Outubro"
                11 -> "Novembro"
                12 -> "Dezembro"
                else -> ""
            }

            return "$nomeMes de ${localDate.year}"
        }

        /**
         * Calcula a menor distância de edição Levenshtein para sugestão amigável de igrejas.
         */
        fun calcularDistanciaLevenshtein(s1: String, s2: String): Int {
            val str1 = SearchUtils.normalizarTexto(s1)
            val str2 = SearchUtils.normalizarTexto(s2)
            val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }

            for (i in 0..str1.length) dp[i][0] = i
            for (j in 0..str2.length) dp[0][j] = j

            for (i in 1..str1.length) {
                for (j in 1..str2.length) {
                    val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                }
            }
            return dp[str1.length][str2.length]
        }
    }

    /**
     * Retorna a lista de todas as igrejas atualmente cadastradas no banco.
     */
    suspend fun listarIgrejasCadastradas(): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        val igrejas = mutableListOf<Pair<Long, String>>()
        val db = databaseHelper.readableDatabase
        val cursor = db.rawQuery("SELECT id, nome FROM igreja ORDER BY nome COLLATE NOCASE", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                igrejas.add(Pair(c.getLong(0), c.getString(1)))
            }
        }
        igrejas
    }

    /**
     * Sugere a igreja existente mais compatível, se houver aproximação por acento ou Levenshtein.
     */
    suspend fun sugerirIgrejaExistente(nomeIgrejaPlanilha: String): String? = withContext(Dispatchers.IO) {
        val existentes = listarIgrejasCadastradas()
        if (existentes.isEmpty()) return@withContext null

        val normPlanilha = SearchUtils.normalizarTexto(nomeIgrejaPlanilha)

        // 1. Procura match exato normalizado
        val matchExato = existentes.firstOrNull { SearchUtils.normalizarTexto(it.second) == normPlanilha }
        if (matchExato != null) return@withContext matchExato.second

        // 2. Procura a menor distância Levenshtein <= 3
        var menorDistancia = Int.MAX_VALUE
        var melhorSugestao: String? = null

        for ((_, nomeExistente) in existentes) {
            val dist = calcularDistanciaLevenshtein(normPlanilha, nomeExistente)
            if (dist < menorDistancia && dist <= 3) {
                menorDistancia = dist
                melhorSugestao = nomeExistente
            }
        }

        melhorSugestao
    }

    override suspend fun verificarConflitosExistentes(linhas: List<LinhaEscala>): List<ConflitoEscala> = withContext(Dispatchers.IO) {
        val conflitos = mutableListOf<ConflitoEscala>()
        if (linhas.isEmpty()) return@withContext conflitos

        val db = databaseHelper.readableDatabase

        // Analisa se alguma função precisa embutir horário devido a múltiplos horários
        val mapaFuncoesHorarios = mapearNomesFuncoes(linhas)

        linhas.forEachIndexed { index, linha ->
            val dataServico = formatarDataServicoNativo(linha.data)
            val nomeFuncaoResolvida = mapaFuncoesHorarios[linha] ?: linha.funcao.trim()

            // Consulta se já existe uma pessoa escalada nessa igreja, período, data e função
            val cursor = db.rawQuery(
                """
                SELECT escalacao.pessoa_nome
                FROM escalacao
                JOIN posto ON posto.id = escalacao.posto_id
                JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
                JOIN igreja ON igreja.id = periodo_escala.igreja_id
                WHERE (igreja.nome = ? COLLATE NOCASE OR TRIM(igreja.nome) = TRIM(?))
                  AND posto.funcao = ? COLLATE NOCASE
                  AND escalacao.data_servico = ? COLLATE NOCASE
                LIMIT 1
                """.trimIndent(),
                arrayOf(linha.igreja.trim(), linha.igreja.trim(), nomeFuncaoResolvida, dataServico)
            )

            cursor.use { c ->
                if (c.moveToNext()) {
                    val pessoaExistente = c.getString(0)
                    if (!pessoaExistente.isNullOrBlank() &&
                        !pessoaExistente.trim().equals(linha.nome.trim(), ignoreCase = true)
                    ) {
                        conflitos.add(
                            ConflitoEscala(
                                indexLinha = index,
                                linha = linha,
                                pessoaExistente = pessoaExistente.trim(),
                                nomeNovo = linha.nome.trim()
                            )
                        )
                    }
                }
            }
        }

        conflitos
    }

    override suspend fun salvarEscala(linhas: List<LinhaEscala>): Result<ResumoGravacao> = withContext(Dispatchers.IO) {
        if (linhas.isEmpty()) {
            return@withContext Result.success(ResumoGravacao(0, 0, 0))
        }

        val db = databaseHelper.writableDatabase
        var inseridos = 0
        var atualizados = 0
        var substituidos = 0

        val dataHoraAtualIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val mapaFuncoesHorarios = mapearNomesFuncoes(linhas)

        db.beginTransaction()
        try {
            // Agrupa as linhas por Igreja e Período (mês/ano de referência)
            val gruposPorIgrejaEPeriodo = linhas.groupBy {
                val refPeriodo = formatarPeriodoReferencia(it.data)
                Pair(it.igreja.trim(), refPeriodo)
            }

            for ((chaveIgrejaPeriodo, linhasDoGrupo) in gruposPorIgrejaEPeriodo) {
                val (nomeIgreja, refPeriodo) = chaveIgrejaPeriodo

                // 1. Obter ou criar Igreja (evitando duplicar por acento ou caixa)
                val igrejaId = obterOuCriarIgreja(db, nomeIgreja, dataHoraAtualIso)

                // 2. Obter ou criar Período de Escala
                val periodoId = obterOuCriarPeriodoEscala(db, igrejaId, refPeriodo, dataHoraAtualIso)

                // 3. Processar cada linha litúrgica
                for (linha in linhasDoGrupo) {
                    val nomeFuncaoResolvida = mapaFuncoesHorarios[linha] ?: linha.funcao.trim()
                    val dataServico = formatarDataServicoNativo(linha.data)

                    // Obter ou criar Posto
                    val postoId = obterOuCriarPosto(db, periodoId, nomeFuncaoResolvida)

                    // Verificar existência prévia da escalação
                    val cursorExistente = db.rawQuery(
                        """
                        SELECT id, pessoa_nome 
                        FROM escalacao 
                        WHERE posto_id = ? AND data_servico = ? COLLATE NOCASE
                        LIMIT 1
                        """.trimIndent(),
                        arrayOf(postoId.toString(), dataServico)
                    )

                    var escalacaoIdExistente: Long? = null
                    var pessoaNomeExistente: String? = null

                    cursorExistente.use { c ->
                        if (c.moveToNext()) {
                            escalacaoIdExistente = c.getLong(0)
                            pessoaNomeExistente = c.getString(1)
                        }
                    }

                    if (escalacaoIdExistente == null) {
                        // Inserção nova
                        val cv = ContentValues().apply {
                            put("posto_id", postoId)
                            put("data_servico", dataServico)
                            put("pessoa_nome", linha.nome.trim())
                            putNull("pessoa_user_id")
                        }
                        db.insertOrThrow("escalacao", null, cv)
                        inseridos++
                    } else {
                        // Já existe: verificar se é substituição ou mesmo nome
                        val mesmoNome = pessoaNomeExistente?.trim().equals(linha.nome.trim(), ignoreCase = true)

                        val cv = ContentValues().apply {
                            put("pessoa_nome", linha.nome.trim())
                            // Ao substituir pessoa_nome, zera pessoa_user_id para não vincular a usuário indevido
                            putNull("pessoa_user_id")
                        }

                        db.update(
                            "escalacao",
                            cv,
                            "id = ?",
                            arrayOf(escalacaoIdExistente.toString())
                        )

                        if (mesmoNome) {
                            atualizados++
                        } else {
                            substituidos++
                            Log.i(
                                TAG,
                                "Substituição realizada em $dataServico: '$pessoaNomeExistente' -> '${linha.nome.trim()}'"
                            )
                        }
                    }
                }
            }

            db.setTransactionSuccessful()
            val resumo = ResumoGravacao(
                inseridos = inseridos,
                atualizados = atualizados,
                substituidos = substituidos
            )
            Log.i(TAG, "Importação determinística concluída: $resumo")
            Result.success(resumo)
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal durante a transação de importação no SQLite", e)
            Result.failure(e)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Mapeia as funções conforme a regra estabelecida:
     * Dentro da mesma igreja e mês/ano, se uma função possuir mais de um horário distinto,
     * agrega o horário no posto: "<funcao> <HH:mm>". Se possuir horário único, usa só "<funcao>".
     */
    private fun mapearNomesFuncoes(linhas: List<LinhaEscala>): Map<LinhaEscala, String> {
        val resultado = mutableMapOf<LinhaEscala, String>()

        val agrupado = linhas.groupBy {
            val refPeriodo = formatarPeriodoReferencia(it.data)
            Triple(it.igreja.trim().lowercase(), refPeriodo, it.funcao.trim().lowercase())
        }

        for (linha in linhas) {
            val refPeriodo = formatarPeriodoReferencia(linha.data)
            val chave = Triple(linha.igreja.trim().lowercase(), refPeriodo, linha.funcao.trim().lowercase())
            val grupoMesmaFuncao = agrupado[chave] ?: emptyList()

            val horariosDistintos = grupoMesmaFuncao.map { it.horario.trim() }.filter { it.isNotBlank() }.distinct()

            if (horariosDistintos.size > 1 && linha.horario.isNotBlank()) {
                resultado[linha] = "${linha.funcao.trim()} ${linha.horario.trim()}"
            } else {
                resultado[linha] = linha.funcao.trim()
            }
        }

        return resultado
    }

    private fun obterOuCriarIgreja(db: SQLiteDatabase, nomeIgreja: String, dataHoraIso: String): Long {
        val norm = SearchUtils.normalizarTexto(nomeIgreja)

        // 1. Tentar encontrar por match exato ou case-insensitive
        val cursor = db.rawQuery(
            "SELECT id, nome FROM igreja WHERE nome = ? COLLATE NOCASE",
            arrayOf(nomeIgreja)
        )
        cursor.use { c ->
            if (c.moveToNext()) return c.getLong(0)
        }

        // 2. Tentar encontrar comparando normalizado sem acento
        val cursorTodos = db.rawQuery("SELECT id, nome FROM igreja", null)
        cursorTodos.use { c ->
            while (c.moveToNext()) {
                val nomeExistente = c.getString(1)
                if (SearchUtils.normalizarTexto(nomeExistente) == norm) {
                    return c.getLong(0)
                }
            }
        }

        // 3. Criar nova igreja
        val cv = ContentValues().apply {
            put("nome", nomeIgreja)
            put("titulo_escala", "Escala $nomeIgreja")
            put("criado_em", dataHoraIso)
        }
        return db.insertOrThrow("igreja", null, cv)
    }

    private fun obterOuCriarPeriodoEscala(
        db: SQLiteDatabase,
        igrejaId: Long,
        referencia: String,
        dataHoraIso: String
    ): Long {
        val cursor = db.rawQuery(
            "SELECT id FROM periodo_escala WHERE igreja_id = ? AND referencia = ? COLLATE NOCASE LIMIT 1",
            arrayOf(igrejaId.toString(), referencia)
        )
        cursor.use { c ->
            if (c.moveToNext()) return c.getLong(0)
        }

        val cv = ContentValues().apply {
            put("igreja_id", igrejaId)
            put("referencia", referencia)
            put("arquivo_origem", "importacao_foto")
            put("importado_em", dataHoraIso)
        }
        return db.insertOrThrow("periodo_escala", null, cv)
    }

    private fun obterOuCriarPosto(
        db: SQLiteDatabase,
        periodoId: Long,
        funcao: String
    ): Long {
        val cursor = db.rawQuery(
            "SELECT id FROM posto WHERE periodo_escala_id = ? AND funcao = ? COLLATE NOCASE LIMIT 1",
            arrayOf(periodoId.toString(), funcao)
        )
        cursor.use { c ->
            if (c.moveToNext()) return c.getLong(0)
        }

        // Determinar a próxima ordem_na_planilha (último + 1)
        val cursorOrdem = db.rawQuery(
            "SELECT COALESCE(MAX(ordem_na_planilha), 0) FROM posto WHERE periodo_escala_id = ?",
            arrayOf(periodoId.toString())
        )
        val proximaOrdem = cursorOrdem.use { c ->
            if (c.moveToNext()) c.getInt(0) + 1 else 1
        }

        val cv = ContentValues().apply {
            put("periodo_escala_id", periodoId)
            put("funcao", funcao)
            put("ordem_na_planilha", proximaOrdem)
        }
        return db.insertOrThrow("posto", null, cv)
    }
}
