package com.aistudio.escala.importacao.dados

import java.text.Normalizer

/**
 * Interface para consulta ao cadastro local de acólitos/coroinhas/servidores.
 * Utilizada pela validação para alertar quando um nome transcrito da escala não for encontrado na base cadastral.
 */
interface CadastroRepository {
    /**
     * Retorna a lista de nomes conhecidos cadastrados localmente no app.
     */
    suspend fun obterNomesCadastrados(): Set<String>

    /**
     * Verifica se determinado nome existe no cadastro, desconsiderando acentos e maiúsculas/minúsculas.
     */
    suspend fun nomeExiste(nome: String): Boolean {
        val limpo = normalizar(nome)
        if (limpo.isBlank()) return false
        return obterNomesCadastrados().any { normalizar(it) == limpo }
    }

    companion object {
        fun normalizar(texto: String): String {
            return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase()
        }
    }
}

/**
 * Implementação padrão de exemplo em memória ou pronta para ligar com DatabaseHelper.
 */
class ExemploCadastroRepository(
    private val nomesMock: Set<String> = emptySet()
) : CadastroRepository {

    constructor(nomesCadastrados: Collection<String>) : this(nomesCadastrados.toSet())

    override suspend fun obterNomesCadastrados(): Set<String> {
        // TODO: Integrar com a tabela de coroinhas/acólitos do DatabaseHelper.kt se existir,
        // ex: "SELECT nome FROM acolitos"
        return nomesMock
    }
}
