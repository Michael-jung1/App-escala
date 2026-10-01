package com.aistudio.escala.ui.components

import android.net.Uri
import android.util.Log
import com.aistudio.escala.util.traduzirErroImportacao
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import com.aistudio.escala.data.EscalaRepository
import com.aistudio.escala.data.ImportResult
import com.aistudio.escala.data.ParsedEscala
import com.aistudio.escala.data.ParsedIgreja
import com.aistudio.escala.parser.EscalaDocumentManager
import com.aistudio.escala.parser.GeminiScheduleParser
import com.aistudio.escala.ui.theme.getChurchColor
import com.aistudio.escala.ui.theme.getErrorColor
import com.aistudio.escala.ui.theme.getSuccessColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportarEscalaCard(
    repository: EscalaRepository,
    onImportSuccess: (periodo: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successColors = getSuccessColor(isDark)
    val errorColors = getErrorColor(isDark)

    var isProcessing by remember { mutableStateOf(false) }
    var processingStep by remember { mutableStateOf("") }
    var parsedEscala by remember { mutableStateOf<ParsedEscala?>(null) }
    var editedPeriodo by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successResult by remember { mutableStateOf<ImportResult.Success?>(null) }

    // Launcher for file picker supporting all document formats via OpenDocument
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileSize = GeminiScheduleParser.getFileSize(context, uri)
            if (fileSize > GeminiScheduleParser.MAX_FILE_SIZE_BYTES) {
                errorMessage = GeminiScheduleParser.ERROR_FILE_TOO_LARGE
                return@rememberLauncherForActivityResult
            }

            isProcessing = true
            errorMessage = null
            processingStep = "Lendo e processando arquivo..."

            coroutineScope.launch {
                try {
                    val result = EscalaDocumentManager.processarArquivo(context, uri)
                    parsedEscala = result
                    editedPeriodo = result.periodo
                    isProcessing = false
                } catch (e: Exception) {
                    Log.e("ImportarEscalaSheet", "Falha interna ao processar documento", e)
                    isProcessing = false
                    errorMessage = traduzirErroImportacao(e)
                }
            }
        }
    }

    // Fallback getContent launcher
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileSize = GeminiScheduleParser.getFileSize(context, uri)
            if (fileSize > GeminiScheduleParser.MAX_FILE_SIZE_BYTES) {
                errorMessage = GeminiScheduleParser.ERROR_FILE_TOO_LARGE
                return@rememberLauncherForActivityResult
            }

            isProcessing = true
            errorMessage = null
            processingStep = "Lendo e processando arquivo..."

            coroutineScope.launch {
                try {
                    val result = EscalaDocumentManager.processarArquivo(context, uri)
                    parsedEscala = result
                    editedPeriodo = result.periodo
                    isProcessing = false
                } catch (e: Exception) {
                    Log.e("ImportarEscalaSheet", "Falha interna ao processar documento via fallback picker", e)
                    isProcessing = false
                    errorMessage = traduzirErroImportacao(e)
                }
            }
        }
    }

    fun launchFilePicker(specificMime: String? = null) {
        val mimeTypes = when (specificMime) {
            "excel" -> arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/octet-stream"
            )
            "pdf" -> arrayOf(
                "application/pdf"
            )
            "text" -> arrayOf(
                "text/csv",
                "text/plain",
                "text/comma-separated-values"
            )
            else -> arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/pdf",
                "text/plain",
                "text/csv",
                "application/octet-stream",
                "*/*"
            )
        }

        try {
            openDocumentLauncher.launch(mimeTypes)
        } catch (_: Exception) {
            getContentLauncher.launch(
                when (specificMime) {
                    "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "pdf" -> "application/pdf"
                    "text" -> "text/*"
                    else -> "*/*"
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("importar_escala_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.UploadFile,
                    contentDescription = "Importar Escala",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Importar Nova Escala Mensal",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Atualize as escalas enviando o arquivo do mês",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Format pills (clickable to filter directly by format)
        Text(
            text = "Toque em um formato ou selecione qualquer arquivo:",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FormatBadge(
                label = "Excel (.xlsx)",
                icon = Icons.Filled.TableChart,
                enabled = !isProcessing,
                onClick = { launchFilePicker("excel") }
            )
            FormatBadge(
                label = "PDF (.pdf)",
                icon = Icons.Filled.PictureAsPdf,
                enabled = !isProcessing,
                onClick = { launchFilePicker("pdf") }
            )
            FormatBadge(
                label = "CSV / Texto (.txt)",
                icon = Icons.Filled.Description,
                enabled = !isProcessing,
                onClick = { launchFilePicker("text") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                launchFilePicker(null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_selecionar_arquivo_escala"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            enabled = !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = processingStep, fontSize = 14.sp)
            } else {
                Icon(
                    imageVector = Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Selecionar Arquivo da Escala",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }

        // Success banner if just imported
        AnimatedVisibility(visible = successResult != null) {
            successResult?.let { res ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(successColors.background)
                        .border(1.dp, successColors.border, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = successColors.text,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Escala de \"${res.periodo}\" importada com sucesso! ${res.totalEscalacoes} escalações em ${res.totalIgrejas} igrejas salvas.",
                            color = successColors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (res.avisosConflito.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Avisos de escalas no mesmo dia (${res.avisosConflito.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            res.avisosConflito.take(4).forEach { aviso ->
                                Text(
                                    text = "• $aviso",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                            if (res.avisosConflito.size > 4) {
                                Text(
                                    text = "+ mais ${res.avisosConflito.size - 4} aviso(s)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message if any
        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(errorColors.background)
                        .border(1.dp, errorColors.border, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = errorColors.text,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            color = errorColors.text,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Confirmation & Preview Dialog
    parsedEscala?.let { escala ->
        PreviewEscalaDialog(
            escala = escala,
            initialPeriodo = editedPeriodo,
            onDismiss = { parsedEscala = null },
            onConfirm = { finalPeriodo ->
                val finalEscala = escala.copy(periodo = finalPeriodo)
                coroutineScope.launch {
                    try {
                        isProcessing = true
                        processingStep = "Gravando dados no banco de dados..."
                        parsedEscala = null
                        val result = repository.importarEscala(finalEscala)
                        isProcessing = false
                        when (result) {
                            is ImportResult.Success -> {
                                successResult = result
                                errorMessage = null
                                onImportSuccess(result.periodo)
                            }
                            is ImportResult.Error -> {
                                Log.e("ImportarEscalaSheet", "Erro ao gravar escala no banco: ${result.message}")
                                errorMessage = "Não foi possível importar a escala agora. Tente novamente ou entre em contato com o suporte."
                                successResult = null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ImportarEscalaSheet", "Erro ao gravar escala no banco de dados", e)
                        isProcessing = false
                        errorMessage = traduzirErroImportacao(e)
                    }
                }
            }
        )
    }
}

@Composable
private fun FormatBadge(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PreviewEscalaDialog(
    escala: ParsedEscala,
    initialPeriodo: String,
    onDismiss: () -> Unit,
    onConfirm: (finalPeriodo: String) -> Unit
) {
    var periodo by remember { mutableStateOf(initialPeriodo) }
    var expandedChurch by remember { mutableStateOf<String?>(escala.igrejas.firstOrNull()?.nome) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val totalEscalacoes = escala.igrejas.sumOf { ig -> ig.postos.sumOf { it.escalacoes.size } }
    val totalPostos = escala.igrejas.sumOf { it.postos.size }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Column {
                Text(
                    text = "Conferir Escala Detectada",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Arquivo: ${escala.arquivoOrigem}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                // Periodo input
                OutlinedTextField(
                    value = periodo,
                    onValueChange = { periodo = it },
                    label = { Text("Mês / Período de Referência") },
                    supportingText = { Text("Ex: Outubro de 2026") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_periodo_importado")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatPill(label = "Igrejas", value = escala.igrejas.size.toString())
                    StatPill(label = "Funções", value = totalPostos.toString())
                    StatPill(label = "Escalações", value = totalEscalacoes.toString())
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Comunidades / Igrejas encontradas:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(escala.igrejas) { igreja ->
                        val isExpanded = expandedChurch == igreja.nome
                        val churchEscalacoes = igreja.postos.sumOf { it.escalacoes.size }
                        val churchColor = getChurchColor(igreja.nome, isDark)
                        val borderClr = churchColor.text
                        val bgClr = churchColor.background

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgClr.copy(alpha = 0.35f))
                                .border(1.dp, borderClr.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable {
                                    expandedChurch = if (isExpanded) null else igreja.nome
                                }
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = igreja.nome,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = borderClr
                                    )
                                    Text(
                                        text = "${igreja.datas.size} dias de serviço • ${igreja.postos.size} funções • $churchEscalacoes escalações",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = borderClr,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        text = "Dias: ${igreja.datas.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Funções: ${igreja.postos.map { it.funcao }.distinct().joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPeriodo = periodo.trim().ifEmpty { "Escala Atual" }
                    onConfirm(finalPeriodo)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirmar_importacao")
            ) {
                Text("Confirmar e Salvar Escala")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancelar_importacao")
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun StatPill(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
