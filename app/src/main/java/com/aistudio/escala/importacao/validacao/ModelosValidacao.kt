package com.aistudio.escala.importacao.validacao

import com.aistudio.escala.importacao.dados.LinhaEscala

enum class Severidade {
    ERRO,
    AVISO
}

enum class CampoLinha {
    DATA,
    HORARIO,
    IGREJA,
    FUNCAO,
    NOME,
    GERAL
}

data class ProblemaValidacao(
    val severidade: Severidade,
    val campo: CampoLinha,
    val mensagem: String
)

data class LinhaValidada(
    val index: Int,
    val linha: LinhaEscala,
    val problemas: List<ProblemaValidacao> = emptyList()
) {
    val temErros: Boolean get() = problemas.any { it.severidade == Severidade.ERRO }
    val temAvisos: Boolean get() = problemas.any { it.severidade == Severidade.AVISO }
    val erros: List<ProblemaValidacao> get() = problemas.filter { it.severidade == Severidade.ERRO }
    val avisos: List<ProblemaValidacao> get() = problemas.filter { it.severidade == Severidade.AVISO }
}

data class RelatorioValidacao(
    val linhasValidadas: List<LinhaValidada>
) {
    /**
     * Regra central: a importação só pode ser liberada quando não houver NENHUM erro bloqueante.
     */
    val podeImportar: Boolean
        get() = linhasValidadas.isNotEmpty() && linhasValidadas.none { it.temErros }

    val totalErros: Int
        get() = linhasValidadas.sumOf { it.erros.size }

    val totalAvisos: Int
        get() = linhasValidadas.sumOf { it.avisos.size }
}
