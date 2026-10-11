package com.aistudio.escala.importacao.dados

import kotlinx.serialization.Serializable

/**
 * Nível de confiança na transcrição de uma linha da escala.
 */
@Serializable
enum class Confianca {
    ALTA,
    MEDIA,
    BAIXA
}

/**
 * Contrato de dados fundamental entre a Etapa A (Extração/CSV) e a Etapa B (Importação no SQLite).
 * Representa uma alocação de serviço litúrgico extraída do documento.
 */
@Serializable
data class LinhaEscala(
    val data: String,
    val horario: String,
    val igreja: String,
    val funcao: String,
    val nome: String,
    val confianca: Confianca,
    val observacao: String = ""
)

/**
 * Tipos de erros categorizados para tratamento amigável de UI sem expor detalhes técnicos.
 */
enum class TipoErro {
    SEM_INTERNET,
    TIMEOUT,
    RESPOSTA_INVALIDA,
    LIMITE_EXCEDIDO,
    IMAGEM_ILEGIVEL,
    DESCONHECIDO
}

/**
 * Resultado da etapa de extração (Imagem -> JSON -> Linhas e CSV).
 */
sealed interface ResultadoExtracao {
    data class Sucesso(
        val linhas: List<LinhaEscala>,
        val csvPath: String
    ) : ResultadoExtracao

    data class Erro(
        val tipo: TipoErro,
        val mensagemDetalhada: String = ""
    ) : ResultadoExtracao
}
