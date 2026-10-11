package com.aistudio.escala.importacao.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.escala.importacao.captura.DocumentScannerHelper
import com.aistudio.escala.importacao.dados.LinhaEscala
import com.aistudio.escala.importacao.validacao.LinhaValidada
import com.aistudio.escala.importacao.validacao.Severidade
import kotlinx.coroutines.launch

/**
 * Tela de importação e revisão humana determinística de escalas litúrgicas (Etapa A -> CSV -> Revisão -> Etapa B).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportacaoRevisaoScreen(
    viewModel: ImportacaoViewModel,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Launcher para o ML Kit Document Scanner
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = DocumentScannerHelper.extrairUriResultado(result.data)
            if (uri != null) {
                viewModel.processarImagemCapturada(context, uri)
            } else {
                Toast.makeText(context, "Nenhuma página capturada pelo scanner.", Toast.LENGTH_SHORT).show()
                viewModel.reiniciarFluxo()
            }
        } else {
            // Cancelado pelo usuário
            viewModel.reiniciarFluxo()
        }
    }

    fun dispararScanner() {
        viewModel.iniciarScanner()
        val activity = context as? Activity
        if (activity != null) {
            coroutineScope.launch {
                try {
                    val request = DocumentScannerHelper.obterIntentSenderRequest(activity)
                    scannerLauncher.launch(request)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Não foi possível abrir o scanner de documentos no dispositivo.",
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.reiniciarFluxo()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Importar Escala por Foto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.reiniciarFluxo()
                            onVoltar()
                        },
                        modifier = Modifier.testTag("btn_voltar_importacao")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fechar importação"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ImportacaoUiState.Ocioso -> {
                    TelaOciosa(
                        onEscanearClick = { dispararScanner() }
                    )
                }

                is ImportacaoUiState.Escaneando -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Aguardando captura do scanner...")
                        }
                    }
                }

                is ImportacaoUiState.Processando -> {
                    TelaProcessando(etapa = state.etapa)
                }

                is ImportacaoUiState.Revisando -> {
                    TelaRevisaoTabela(
                        state = state,
                        onEditarLinha = { idx, linha -> viewModel.editarLinha(context, idx, linha) },
                        onAdicionarLinha = { viewModel.adicionarLinha(context) },
                        onRemoverLinha = { idx -> viewModel.removerLinha(context, idx) },
                        onReescanear = { dispararScanner() },
                        onExportarCsv = {
                            val intent = viewModel.criarIntentExportarCsv(context)
                            if (intent != null) {
                                context.startActivity(Intent.createChooser(intent, "Compartilhar Escala (CSV)"))
                            } else {
                                Toast.makeText(context, "Arquivo CSV não disponível para exportação.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onConfirmarImportacao = { viewModel.confirmarImportacaoFinal() }
                    )
                }

                is ImportacaoUiState.Importando -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Gravando escala no banco de dados local...",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                is ImportacaoUiState.Concluido -> {
                    TelaConcluido(
                        totalSalvas = state.totalSalvas,
                        onConcluir = {
                            viewModel.reiniciarFluxo()
                            onVoltar()
                        }
                    )
                }

                is ImportacaoUiState.Erro -> {
                    TelaErro(
                        mensagem = state.mensagemAmigavel,
                        onTentarNovamente = { dispararScanner() },
                        onVoltar = {
                            viewModel.reiniciarFluxo()
                            onVoltar()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TelaOciosa(
    onEscanearClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Escanear Escala Litúrgica",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Capture a foto da escala impressa ou escolha uma imagem da galeria. O scanner corrigirá perspectiva, recorte e nitidez.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onEscanearClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_iniciar_scanner"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.DocumentScanner,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Iniciar Scanner de Documento",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TelaProcessando(etapa: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Processando Escala",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = etapa,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun TelaRevisaoTabela(
    state: ImportacaoUiState.Revisando,
    onEditarLinha: (Int, LinhaEscala) -> Unit,
    onAdicionarLinha: () -> Unit,
    onRemoverLinha: (Int) -> Unit,
    onReescanear: () -> Unit,
    onExportarCsv: () -> Unit,
    onConfirmarImportacao: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Banner de status e validação
        BannerValidacao(
            totalLinhas = state.linhas.size,
            totalErros = state.relatorio.totalErros,
            totalAvisos = state.relatorio.totalAvisos,
            podeImportar = state.podeImportar
        )

        // Lista de linhas editáveis
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            itemsIndexed(state.relatorio.linhasValidadas) { index, linhaValidada ->
                ItemLinhaEscalaCard(
                    index = index,
                    linhaValidada = linhaValidada,
                    onSalvarEdicao = { novaLinha -> onEditarLinha(index, novaLinha) },
                    onRemover = { onRemoverLinha(index) }
                )
            }

            item {
                OutlinedButton(
                    onClick = onAdicionarLinha,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("btn_adicionar_linha"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar Nova Linha")
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Barra inferior com ações
        BarraAcoesInferior(
            podeImportar = state.podeImportar,
            onReescanear = onReescanear,
            onExportarCsv = onExportarCsv,
            onConfirmarImportacao = onConfirmarImportacao
        )
    }
}

@Composable
private fun BannerValidacao(
    totalLinhas: Int,
    totalErros: Int,
    totalAvisos: Int,
    podeImportar: Boolean
) {
    val corFundo = if (totalErros > 0) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    } else if (totalAvisos > 0) {
        Color(0xFFFFF3CD)
    } else {
        Color(0xFFE8F5E9)
    }

    val corBorda = if (totalErros > 0) {
        MaterialTheme.colorScheme.error
    } else if (totalAvisos > 0) {
        Color(0xFFFFC107)
    } else {
        Color(0xFF4CAF50)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(corFundo)
            .border(1.dp, corBorda, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (totalErros > 0) Icons.Filled.Error else if (totalAvisos > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (totalErros > 0) MaterialTheme.colorScheme.error else if (totalAvisos > 0) Color(0xFFB78103) else Color(0xFF2E7D32),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Total de registros: $totalLinhas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (totalErros > 0) {
                    BadgeStatus(
                        texto = "$totalErros ${if (totalErros == 1) "erro" else "erros"}",
                        corFundo = MaterialTheme.colorScheme.error,
                        corTexto = MaterialTheme.colorScheme.onError
                    )
                }
                if (totalAvisos > 0) {
                    BadgeStatus(
                        texto = "$totalAvisos ${if (totalAvisos == 1) "aviso" else "avisos"}",
                        corFundo = Color(0xFFFFB300),
                        corTexto = Color.Black
                    )
                }
                if (totalErros == 0 && totalAvisos == 0) {
                    BadgeStatus(
                        texto = "100% Válido",
                        corFundo = Color(0xFF4CAF50),
                        corTexto = Color.White
                    )
                }
            }
        }

        if (!podeImportar) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Corrija os campos destacados em vermelho para liberar a importação no banco de dados.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BadgeStatus(texto: String, corFundo: Color, corTexto: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(corFundo)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = texto,
            color = corTexto,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ItemLinhaEscalaCard(
    index: Int,
    linhaValidada: LinhaValidada,
    onSalvarEdicao: (LinhaEscala) -> Unit,
    onRemover: () -> Unit
) {
    val linha = linhaValidada.linha
    val temErros = linhaValidada.temErros
    val temAvisos = linhaValidada.temAvisos

    val corBorda = when {
        temErros -> MaterialTheme.colorScheme.error
        temAvisos -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val corFundo = when {
        temErros -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        temAvisos -> Color(0xFFFFFDE7)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_linha_$index"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = corFundo),
        border = androidx.compose.foundation.BorderStroke(if (temErros || temAvisos) 1.5.dp else 1.dp, corBorda)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cabeçalho da linha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (temErros) {
                        Text(
                            text = "Atenção: contém erros",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (temAvisos) {
                        Text(
                            text = "Aviso de revisão",
                            color = Color(0xFFB78103),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onRemover,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_remover_linha_$index")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remover linha",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Células editáveis: Data e Horário
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = linha.data,
                    onValueChange = { onSalvarEdicao(linha.copy(data = it)) },
                    label = { Text("Data (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("input_data_$index")
                )

                OutlinedTextField(
                    value = linha.horario,
                    onValueChange = { onSalvarEdicao(linha.copy(horario = it)) },
                    label = { Text("Horário") },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("input_horario_$index")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Células editáveis: Igreja e Função
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = linha.igreja,
                    onValueChange = { onSalvarEdicao(linha.copy(igreja = it)) },
                    label = { Text("Igreja / Paróquia") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_igreja_$index")
                )

                OutlinedTextField(
                    value = linha.funcao,
                    onValueChange = { onSalvarEdicao(linha.copy(funcao = it)) },
                    label = { Text("Função") },
                    placeholder = { Text("Missal") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_funcao_$index")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Célula editável: Nome
            OutlinedTextField(
                value = linha.nome,
                onValueChange = { onSalvarEdicao(linha.copy(nome = it)) },
                label = { Text("Nome do Acólito / Coroinha") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_nome_$index")
            )

            // Exibição de problemas da linha (Erros e Avisos com motivo legível)
            AnimatedVisibility(visible = linhaValidada.problemas.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    linhaValidada.erros.forEach { problema ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = problema.mensagem,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    linhaValidada.avisos.forEach { aviso ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFB78103),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = aviso.mensagem,
                                color = Color(0xFF7A5600),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarraAcoesInferior(
    podeImportar: Boolean,
    onReescanear: () -> Unit,
    onExportarCsv: () -> Unit,
    onConfirmarImportacao: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReescanear,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_reescanear"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reescanear", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onExportarCsv,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_exportar_csv"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar CSV", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onConfirmarImportacao,
                enabled = podeImportar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_confirmar_importacao"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            ) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (podeImportar) "Confirmar e Importar Escala" else "Corrija os erros para importar",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TelaConcluido(
    totalSalvas: Int,
    onConcluir: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Escala Importada com Sucesso!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$totalSalvas registros litúrgicos foram validados e gravados com segurança na base de dados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConcluir,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_concluir_importacao"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Concluir e Voltar", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TelaErro(
    mensagem: String,
    onTentarNovamente: () -> Unit,
    onVoltar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Não foi possível extrair a escala",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onTentarNovamente,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_erro_tentar_novamente"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tentar Novamente", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onVoltar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voltar ao Início")
        }
    }
}
