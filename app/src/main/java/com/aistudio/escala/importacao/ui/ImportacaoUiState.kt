package com.aistudio.escala.importacao.ui

import com.aistudio.escala.importacao.dados.LinhaEscala
import com.aistudio.escala.importacao.dados.TipoErro
import com.aistudio.escala.importacao.validacao.LinhaValidada
import com.aistudio.escala.importacao.validacao.RelatorioValidacao

/**
 * Estados da interface do usuário do fluxo de importação.
 */
sealed interface ImportacaoUiState {
    /**
     * Estado inicial aguardando o usuário tocar em escanear/importar.
     */
    data object Ocioso : ImportacaoUiState

    /**
     * O scanner de documentos do ML Kit está em execução ou aguardando captura.
     */
    data object Escaneando : ImportacaoUiState

    /**
     * Imagem sendo redimensionada, enviada à IA e processada em CSV intermediário.
     */
    data class Processando(
        val etapa: String = "Analisando escala com inteligência artificial..."
    ) : ImportacaoUiState

    /**
     * Tela principal de revisão humana:
     * Exibe a lista de linhas editáveis, relatório de validação reativo (erros e avisos),
     * e botões de ação (adicionar, remover, exportar CSV, reescanear, importar).
     */
    data class Revisando(
        val linhas: List<LinhaEscala>,
        val relatorio: RelatorioValidacao,
        val csvPath: String,
        val podeImportar: Boolean
    ) : ImportacaoUiState

    /**
     * Gravação final sendo executada determinística no SQLite via ScheduleRepository.
     */
    data object Importando : ImportacaoUiState

    /**
     * Fluxo concluído com sucesso e dados salvos no SQLite.
     */
    data class Concluido(
        val totalSalvas: Int
    ) : ImportacaoUiState

    /**
     * Erro amigável apresentado ao usuário com opção de reescanear ou tentar novamente.
     */
    data class Erro(
        val mensagemAmigavel: String,
        val tipo: TipoErro? = null
    ) : ImportacaoUiState
}
