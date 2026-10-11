package com.aistudio.escala.importacao.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.escala.importacao.captura.ImageResizer
import com.aistudio.escala.importacao.csv.CsvWriter
import com.aistudio.escala.importacao.dados.CadastroRepository
import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.dados.ExemploScheduleRepository
import com.aistudio.escala.importacao.dados.LinhaEscala
import com.aistudio.escala.importacao.dados.ResultadoExtracao
import com.aistudio.escala.importacao.dados.ScheduleRepository
import com.aistudio.escala.importacao.dados.TipoErro
import com.aistudio.escala.importacao.extracao.ExtratorEscala
import com.aistudio.escala.importacao.extracao.HttpProxyGeminiClient
import com.aistudio.escala.importacao.validacao.ValidadorEscala
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * ViewModel gerenciador de todo o fluxo de importação da escala:
 * 1. Captura e compressão de imagem
 * 2. Extração via IA (Gemini)
 * 3. Geração de CSV intermediário
 * 4. Validação pura e reativa a cada edição humana
 * 5. Exportação de CSV via FileProvider
 * 6. Gravação determinística no SQLite via ScheduleRepository
 * 7. Limpeza rigorosa de arquivos temporários de imagem e CSV (privacidade)
 */
class ImportacaoViewModel(
    private val extratorEscala: ExtratorEscala = ExtratorEscala(HttpProxyGeminiClient()),
    private val validadorEscala: ValidadorEscala = ValidadorEscala(),
    private val scheduleRepository: ScheduleRepository = ExemploScheduleRepository(),
    private val cadastroRepository: CadastroRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "ImportacaoViewModel"
    }

    private val _uiState = MutableStateFlow<ImportacaoUiState>(ImportacaoUiState.Ocioso)
    val uiState: StateFlow<ImportacaoUiState> = _uiState.asStateFlow()

    private var arquivoImagemTemporario: File? = null
    private var arquivoCsvTemporario: File? = null

    /**
     * Prepara a interface para o escaneamento de documento.
     */
    fun iniciarScanner() {
        _uiState.value = ImportacaoUiState.Escaneando
    }

    /**
     * Processa a imagem obtida do scanner ou da galeria.
     */
    fun processarImagemCapturada(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportacaoUiState.Processando("Otimizando e comprimindo imagem...")

            try {
                // Etapa 0: compressão e redimensionamento da imagem (max 2048 px, JPEG 85)
                val resultadoImagem = ImageResizer.processarEComprimir(context, imageUri)
                arquivoImagemTemporario = resultadoImagem.tempFile

                _uiState.value = ImportacaoUiState.Processando("Extraindo tabela litúrgica com a inteligência artificial...")

                // Etapa A: chamada Gemini e geração de CSV intermediário no cache
                val resultadoExtracao = extratorEscala.extrair(context, resultadoImagem.base64)

                when (resultadoExtracao) {
                    is ResultadoExtracao.Sucesso -> {
                        arquivoCsvTemporario = File(resultadoExtracao.csvPath)
                        revalidarLinhasEAtualizarUi(
                            context = context,
                            linhas = resultadoExtracao.linhas,
                            csvPath = resultadoExtracao.csvPath
                        )
                    }
                    is ResultadoExtracao.Erro -> {
                        val msg = traduzirErro(resultadoExtracao.tipo)
                        _uiState.value = ImportacaoUiState.Erro(
                            mensagemAmigavel = msg,
                            tipo = resultadoExtracao.tipo
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha durante o processamento da imagem", e)
                val tipo = ExtratorEscala.categorizarErro(e)
                _uiState.value = ImportacaoUiState.Erro(
                    mensagemAmigavel = traduzirErro(tipo),
                    tipo = tipo
                )
            }
        }
    }

    /**
     * Revalida a lista de linhas e atualiza o estado de UI.
     */
    private suspend fun revalidarLinhasEAtualizarUi(
        context: Context,
        linhas: List<LinhaEscala>,
        csvPath: String
    ) {
        val relatorio = validadorEscala.validar(linhas, cadastroRepository)

        // Atualiza arquivo CSV no cache com as alterações atuais
        withContext(Dispatchers.IO) {
            try {
                arquivoCsvTemporario?.let { csvFile ->
                    CsvWriter.salvarCsvNoCache(
                        diretorioCache = context.cacheDir,
                        nomeArquivo = csvFile.name,
                        linhas = linhas,
                        incluirColunasExtras = false
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha não-crítica ao atualizar CSV em cache: ${e.message}")
            }
        }

        _uiState.value = ImportacaoUiState.Revisando(
            linhas = linhas,
            relatorio = relatorio,
            csvPath = csvPath,
            podeImportar = relatorio.podeImportar
        )
    }

    /**
     * Edita uma linha existente. Dispara revalidação automática imediata.
     */
    fun editarLinha(context: Context, index: Int, novaLinha: LinhaEscala) {
        val estadoAtual = _uiState.value as? ImportacaoUiState.Revisando ?: return
        if (index !in estadoAtual.linhas.indices) return

        val novasLinhas = estadoAtual.linhas.toMutableList()
        novasLinhas[index] = novaLinha

        viewModelScope.launch {
            revalidarLinhasEAtualizarUi(context, novasLinhas, estadoAtual.csvPath)
        }
    }

    /**
     * Adiciona uma nova linha manual à tabela de revisão.
     */
    fun adicionarLinha(context: Context) {
        val estadoAtual = _uiState.value as? ImportacaoUiState.Revisando ?: return
        val dataPadrao = estadoAtual.linhas.lastOrNull()?.data?.ifBlank { LocalDate.now().toString() } ?: LocalDate.now().toString()
        val igrejaPadrao = estadoAtual.linhas.lastOrNull()?.igreja ?: ""

        val novaLinha = LinhaEscala(
            data = dataPadrao,
            horario = "08:00",
            igreja = igrejaPadrao,
            funcao = "",
            nome = "",
            confianca = Confianca.ALTA,
            observacao = "Adicionada manualmente"
        )

        val novasLinhas = estadoAtual.linhas + novaLinha
        viewModelScope.launch {
            revalidarLinhasEAtualizarUi(context, novasLinhas, estadoAtual.csvPath)
        }
    }

    /**
     * Remove uma linha da tabela de revisão.
     */
    fun removerLinha(context: Context, index: Int) {
        val estadoAtual = _uiState.value as? ImportacaoUiState.Revisando ?: return
        if (index !in estadoAtual.linhas.indices) return

        val novasLinhas = estadoAtual.linhas.toMutableList().apply { removeAt(index) }
        viewModelScope.launch {
            revalidarLinhasEAtualizarUi(context, novasLinhas, estadoAtual.csvPath)
        }
    }

    /**
     * Confirma a importação final das linhas validadas no banco de dados SQLite (Etapa B).
     */
    fun confirmarImportacaoFinal() {
        val estadoAtual = _uiState.value as? ImportacaoUiState.Revisando ?: return
        if (!estadoAtual.podeImportar) return

        viewModelScope.launch {
            _uiState.value = ImportacaoUiState.Importando

            val resultado = scheduleRepository.salvarEscala(estadoAtual.linhas)
            if (resultado.isSuccess) {
                val total = resultado.getOrDefault(estadoAtual.linhas.size)
                // Privacidade: apaga os arquivos de imagem e CSV temporários após o sucesso
                limparCacheTemporario()
                _uiState.value = ImportacaoUiState.Concluido(totalSalvas = total)
            } else {
                val erroMsg = "Falha ao gravar registros no banco de dados local. Tente novamente."
                _uiState.value = ImportacaoUiState.Erro(erroMsg, TipoErro.DESCONHECIDO)
            }
        }
    }

    /**
     * Cria a Intent de compartilhamento do arquivo CSV atual via FileProvider.
     */
    fun criarIntentExportarCsv(context: Context): Intent? {
        val csvFile = arquivoCsvTemporario ?: return null
        if (!csvFile.exists()) return null

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, csvFile)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Escala Litúrgica - Exportação CSV")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Cancela o fluxo atual e limpa os arquivos temporários.
     */
    fun reiniciarFluxo() {
        limparCacheTemporario()
        _uiState.value = ImportacaoUiState.Ocioso
    }

    private fun limparCacheTemporario() {
        try {
            arquivoImagemTemporario?.delete()
            arquivoImagemTemporario = null
            arquivoCsvTemporario?.delete()
            arquivoCsvTemporario = null
        } catch (e: Exception) {
            Log.w(TAG, "Falha não-crítica ao limpar arquivos temporários: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        limparCacheTemporario()
    }

    private fun traduzirErro(tipo: TipoErro): String {
        return when (tipo) {
            TipoErro.SEM_INTERNET -> "Sem conexão com a internet. Verifique sua rede e tente novamente."
            TipoErro.TIMEOUT -> "A conexão com o serviço de inteligência artificial demorou demais para responder. Tente novamente em instantes."
            TipoErro.LIMITE_EXCEDIDO -> "Muitas tentativas em pouco tempo. Aguarde alguns minutos antes de tentar novamente."
            TipoErro.RESPOSTA_INVALIDA -> "Não conseguimos estruturar a tabela a partir da foto. Tente tirar uma foto mais nítida, com boa iluminação."
            TipoErro.IMAGEM_ILEGIVEL -> "A imagem está ilegível ou muito escura para leitura automática. Experimente alinhar e focar bem a tabela antes de capturar."
            TipoErro.DESCONHECIDO -> "Não foi possível importar a escala agora. Verifique a foto e tente novamente."
        }
    }
}
