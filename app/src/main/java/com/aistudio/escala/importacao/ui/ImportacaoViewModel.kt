package com.aistudio.escala.importacao.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.aistudio.escala.importacao.extracao.FirebaseAiLogicGeminiClient
import com.aistudio.escala.importacao.repositorio.SqliteScheduleRepository
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
 * ViewModel gerenciador do fluxo determinístico de importação da escala:
 * 1. Captura e compressão de imagem (ImageResizer)
 * 2. Extração via IA com Firebase AI Logic padrão (FirebaseAiLogicGeminiClient)
 * 3. Geração de CSV intermediário em UTF-8 com BOM
 * 4. Validação pura com relatório de erros e avisos
 * 5. Detecção de substituições e avisos de agregação de horário em posto
 * 6. Edição com estado local debounce/perda de foco na UI
 * 7. Exportação de CSV via FileProvider
 * 8. Gravação determinística no SQLite via ScheduleRepository
 * 9. Limpeza de arquivos residuais no cache (privacidade e segurança)
 */
class ImportacaoViewModel(
    private val extratorEscala: ExtratorEscala = ExtratorEscala(FirebaseAiLogicGeminiClient()),
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
     * Inicia o fluxo de scanner de documento.
     */
    fun iniciarCaptura() {
        _uiState.value = ImportacaoUiState.Escaneando
    }

    /**
     * Processa a foto escaneada pelo ML Kit Document Scanner:
     * 1. Redimensiona preservando proporção (lado maior <= 2048 px) e comprime em JPEG 85.
     * 2. Envia os bytes ao Gemini (Firebase AI Logic).
     * 3. Salva o CSV intermediário inicial.
     * 4. Valida os dados e abre a tela de revisão humana.
     */
    fun processarImagemCapturada(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportacaoUiState.Processando("Otimizando imagem para leitura...")

            // Redimensionamento e compressão
            val resultadoProcessamento = try {
                ImageResizer.processarEComprimir(context, imageUri)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao processar e comprimir imagem", e)
                null
            }
            if (resultadoProcessamento == null) {
                _uiState.value = ImportacaoUiState.Erro(
                    "Não foi possível processar a imagem escaneada. Tente novamente.",
                    TipoErro.IMAGEM_ILEGIVEL
                )
                return@launch
            }

            arquivoImagemTemporario = resultadoProcessamento.tempFile

            _uiState.value = ImportacaoUiState.Processando("Identificando tabelas e escalas litúrgicas com IA...")

            // Extração multimodal estruturada com Firebase AI Logic
            val resultadoExtracao = extratorEscala.extrair(
                context = context,
                imagemBytes = resultadoProcessamento.imageBytes
            )

            when (resultadoExtracao) {
                is ResultadoExtracao.Sucesso -> {
                    val csvFile = File(resultadoExtracao.csvPath)
                    arquivoCsvTemporario = csvFile

                    _uiState.value = ImportacaoUiState.Processando("Validando dados e identificando regras...")
                    revalidarLinhasEAtualizarUi(context, resultadoExtracao.linhas, resultadoExtracao.csvPath)
                }
                is ResultadoExtracao.Erro -> {
                    val mensagem = traduzirErro(resultadoExtracao.tipo)
                    _uiState.value = ImportacaoUiState.Erro(
                        mensagemAmigavel = mensagem,
                        tipo = resultadoExtracao.tipo
                    )
                }
            }
        }
    }

    /**
     * Revalida a lista de linhas e atualiza o estado com relatório, conflitos e avisos de posto.
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

        // Verifica conflitos com escalações já existentes na base de dados
        val conflitos = try {
            scheduleRepository.verificarConflitosExistentes(linhas)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar conflitos prévios: ${e.message}")
            emptyList()
        }

        // Lista igrejas cadastradas se o repositório for SqliteScheduleRepository
        val igrejasCadastradas = try {
            (scheduleRepository as? SqliteScheduleRepository)?.listarIgrejasCadastradas()?.map { it.second }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Gera avisos quando postos embutem horário devido a horários distintos para a mesma função
        val avisosPostos = gerarAvisosPostoComHorario(linhas)

        _uiState.value = ImportacaoUiState.Revisando(
            linhas = linhas,
            relatorio = relatorio,
            csvPath = csvPath,
            podeImportar = relatorio.podeImportar,
            conflitos = conflitos,
            igrejasCadastradas = igrejasCadastradas,
            avisosPostoComHorario = avisosPostos
        )
    }

    private fun gerarAvisosPostoComHorario(linhas: List<LinhaEscala>): List<String> {
        val avisos = mutableListOf<String>()
        val agrupado = linhas.groupBy {
            val ref = SqliteScheduleRepository.formatarPeriodoReferencia(it.data)
            Triple(it.igreja.trim(), ref, it.funcao.trim())
        }

        for ((chave, grupo) in agrupado) {
            val (igreja, periodo, funcao) = chave
            val horarios = grupo.map { it.horario.trim() }.filter { it.isNotBlank() }.distinct()
            if (horarios.size > 1) {
                avisos.add("Na igreja '$igreja' ($periodo), a função '$funcao' possui ${horarios.size} horários distintos (${horarios.joinToString(", ")}). Postos específicos com horário serão criados (ex: '$funcao ${horarios.first()}').")
            }
        }
        return avisos
    }

    /**
     * Atualiza a lista completa de linhas com a edição consolidada (após foco ou debounce).
     */
    fun atualizarTodasLinhas(context: Context, novasLinhas: List<LinhaEscala>) {
        val estadoAtual = _uiState.value as? ImportacaoUiState.Revisando ?: return
        viewModelScope.launch {
            revalidarLinhasEAtualizarUi(context, novasLinhas, estadoAtual.csvPath)
        }
    }

    /**
     * Edita uma linha existente. Dispara revalidação e persistência de CSV.
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
                val resumo = resultado.getOrNull() ?: com.aistudio.escala.importacao.dados.ResumoGravacao(
                    inseridos = estadoAtual.linhas.size
                )
                // Privacidade: apaga os arquivos de imagem e CSV temporários após o sucesso
                limparCacheTemporario()
                _uiState.value = ImportacaoUiState.Concluido(resumo = resumo)
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
            TipoErro.LIMITE_EXCEDIDO -> "Muitas tentativas em pouco tempo ou cota excedida. Aguarde alguns minutos antes de tentar novamente."
            TipoErro.RESPOSTA_INVALIDA -> "Não conseguimos estruturar a tabela a partir da foto. Tente tirar uma foto mais nítida, com boa iluminação."
            TipoErro.IMAGEM_ILEGIVEL -> "A imagem está ilegível ou muito escura para leitura automática. Experimente alinhar e focar bem a tabela antes de capturar."
            TipoErro.SERVICO_OCUPADO -> "O serviço de leitura está ocupado agora. Tente novamente em alguns minutos."
            TipoErro.PERMISSAO_NEGADA -> "Acesso não autorizado ao serviço de IA. Verifique as permissões de acesso do projeto."
            TipoErro.API_NAO_HABILITADA -> "A API do serviço de IA ainda não está ativada no projeto. Contate o administrador para ativá-la no console."
            TipoErro.APP_CHECK_FALHOU -> "Falha na verificação de integridade do Firebase App Check. O dispositivo não pôde ser atestado."
            TipoErro.DESCONHECIDO -> "Não foi possível importar a escala agora. Verifique a foto e tente novamente."
        }
    }
}

/**
 * Factory padrão para instanciação do [ImportacaoViewModel] com injeção de dependências limpa.
 *
 * Como usar no Jetpack Compose:
 * ```kotlin
 * val context = LocalContext.current
 * val viewModel: ImportacaoViewModel = viewModel(
 *     factory = ImportacaoViewModelFactory(
 *         extratorEscala = ExtratorEscala(FirebaseAiLogicGeminiClient()),
 *         validadorEscala = ValidadorEscala(),
 *         scheduleRepository = SqliteScheduleRepository(context),
 *         cadastroRepository = SqliteCadastroRepository(context)
 *     )
 * )
 * ```
 */
class ImportacaoViewModelFactory(
    private val extratorEscala: ExtratorEscala = ExtratorEscala(FirebaseAiLogicGeminiClient()),
    private val validadorEscala: ValidadorEscala = ValidadorEscala(),
    private val scheduleRepository: ScheduleRepository = ExemploScheduleRepository(),
    private val cadastroRepository: CadastroRepository? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImportacaoViewModel::class.java)) {
            return ImportacaoViewModel(
                extratorEscala = extratorEscala,
                validadorEscala = validadorEscala,
                scheduleRepository = scheduleRepository,
                cadastroRepository = cadastroRepository
            ) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida: ${modelClass.name}")
    }
}
