package com.aistudio.escala.ui.components

import android.net.Uri
import android.util.Log
import retrofit2.HttpException
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Church
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    var parsedEscalasList by remember { mutableStateOf<List<ParsedEscala>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successResult by remember { mutableStateOf<ImportResult.Success?>(null) }

    fun processarListaArquivos(uris: List<Uri>) {
        if (uris.isEmpty()) return

        isProcessing = true
        errorMessage = null
        successResult = null

        coroutineScope.launch {
            val parsedList = mutableListOf<ParsedEscala>()
            val erros = mutableListOf<String>()
            val total = uris.size

            for ((index, uri) in uris.withIndex()) {
                val fileName = EscalaDocumentManager.obterNomeArquivo(context, uri)
                processingStep = if (total > 1) {
                    "Processando foto ${index + 1} de $total ($fileName)..."
                } else {
                    "Lendo e processando arquivo..."
                }

                val fileSize = GeminiScheduleParser.getFileSize(context, uri)
                if (fileSize > GeminiScheduleParser.MAX_FILE_SIZE_BYTES) {
                    erros.add("$fileName: ${GeminiScheduleParser.ERROR_FILE_TOO_LARGE}")
                    continue
                }

                try {
                    val result = EscalaDocumentManager.processarArquivo(context, uri)
                    parsedList.add(result)
                } catch (e: Exception) {
                    val httpCode = (e as? HttpException)?.code() ?: (e.cause as? HttpException)?.code()
                    val httpBody = try {
                        (e as? HttpException)?.response()?.errorBody()?.string()
                            ?: (e.cause as? HttpException)?.response()?.errorBody()?.string()
                    } catch (_: Throwable) { null }
                    Log.e("ImportarEscalaSheet", "Falha técnica ao processar $fileName (HTTP $httpCode, body='$httpBody'): ${e.message}", e)
                    erros.add("$fileName: ${traduzirErroImportacao(e)}")
                }
            }

            isProcessing = false

            if (parsedList.isNotEmpty()) {
                parsedEscalasList = parsedList
                if (erros.isNotEmpty()) {
                    errorMessage = "Atenção: alguns arquivos não puderam ser lidos:\n" + erros.joinToString("\n")
                }
            } else {
                errorMessage = if (erros.isNotEmpty()) {
                    erros.joinToString("\n")
                } else {
                    "Nenhum arquivo pôde ser processado."
                }
            }
        }
    }

    // Launcher for multiple document selection (Photos / multiple images)
    val openMultipleDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            processarListaArquivos(uris)
        }
    }

    // Fallback launcher for multiple contents
    val getMultipleContentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            processarListaArquivos(uris)
        }
    }

    // Launcher for file picker supporting all document formats via OpenDocument
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            processarListaArquivos(listOf(uri))
        }
    }

    // Fallback getContent launcher
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processarListaArquivos(listOf(uri))
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
            "foto", "imagem" -> arrayOf(
                "image/jpeg",
                "image/png",
                "image/webp"
            )
            else -> arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/pdf",
                "text/plain",
                "text/csv",
                "image/jpeg",
                "image/png",
                "image/*",
                "application/octet-stream",
                "*/*"
            )
        }

        if (specificMime == "foto" || specificMime == "imagem") {
            try {
                openMultipleDocumentsLauncher.launch(mimeTypes)
            } catch (_: Exception) {
                try {
                    getMultipleContentsLauncher.launch("image/*")
                } catch (e: Exception) {
                    Log.e("ImportarEscalaSheet", "Falha ao abrir seletor múltiplo de fotos", e)
                    errorMessage = "Não foi possível abrir o seletor de fotos no dispositivo."
                }
            }
        } else {
            try {
                openDocumentLauncher.launch(mimeTypes)
            } catch (_: Exception) {
                try {
                    getContentLauncher.launch(
                        when (specificMime) {
                            "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            "pdf" -> "application/pdf"
                            "text" -> "text/*"
                            else -> "*/*"
                        }
                    )
                } catch (e: Exception) {
                    Log.e("ImportarEscalaSheet", "Falha ao abrir seletor de documentos", e)
                    errorMessage = "Não foi possível abrir o seletor de arquivos no dispositivo."
                }
            }
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
                label = "Fotos / Imagens",
                icon = Icons.Filled.AddPhotoAlternate,
                enabled = !isProcessing,
                onClick = { launchFilePicker("foto") }
            )
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

    // Confirmation & Preview Dialog (suporta importação individual ou lote de fotos por igreja)
    if (parsedEscalasList.isNotEmpty()) {
        PreviewMultiEscalaDialog(
            escalasList = parsedEscalasList,
            repository = repository,
            onDismiss = { parsedEscalasList = emptyList() },
            onItemSaved = { _, periodo, result ->
                successResult = result
                errorMessage = null
                onImportSuccess(periodo)
            },
            onAllFinished = {
                parsedEscalasList = emptyList()
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

                if (escala.avisos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Avisos de leitura / baixa confiança (${escala.avisos.size}):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            escala.avisos.forEach { aviso ->
                                Text(
                                    text = "• $aviso",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
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
fun StatPill(label: String, value: String) {
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

class ReviewItemState(
    val escala: ParsedEscala,
    initialPeriodo: String
) {
    var periodo by mutableStateOf(initialPeriodo)
    var isSalvo by mutableStateOf(false)
    var isSalvando by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successResult by mutableStateOf<ImportResult.Success?>(null)
    var isExpanded by mutableStateOf(true)
}

@Composable
fun PreviewMultiEscalaDialog(
    escalasList: List<ParsedEscala>,
    repository: EscalaRepository,
    onDismiss: () -> Unit,
    onItemSaved: (ParsedEscala, String, ImportResult.Success) -> Unit = { _, _, _ -> },
    onAllFinished: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successColors = getSuccessColor(isDark)
    val errorColors = getErrorColor(isDark)

    val reviewItems = remember(escalasList) {
        escalasList.map { escala ->
            ReviewItemState(
                escala = escala,
                initialPeriodo = escala.periodo.ifBlank { "Escala Atual" }
            )
        }
    }
    var isSavingAll by remember { mutableStateOf(false) }

    fun salvarItem(item: ReviewItemState) {
        if (item.isSalvo || item.isSalvando) return
        item.isSalvando = true
        item.errorMessage = null
        coroutineScope.launch {
            try {
                val finalPeriodo = item.periodo.trim().ifEmpty { "Escala Atual" }
                val finalEscala = item.escala.copy(periodo = finalPeriodo)
                val result = repository.importarEscala(finalEscala)
                item.isSalvando = false
                when (result) {
                    is ImportResult.Success -> {
                        item.isSalvo = true
                        item.successResult = result
                        onItemSaved(item.escala, finalPeriodo, result)
                    }
                    is ImportResult.Error -> {
                        item.errorMessage = result.message
                    }
                }
            } catch (e: Exception) {
                val httpCode = (e as? HttpException)?.code() ?: (e.cause as? HttpException)?.code()
                val httpBody = try {
                    (e as? HttpException)?.response()?.errorBody()?.string()
                        ?: (e.cause as? HttpException)?.response()?.errorBody()?.string()
                } catch (_: Throwable) { null }
                Log.e("ImportarEscalaSheet", "Erro ao salvar escala (HTTP $httpCode, body='$httpBody'): ${e.message}", e)
                item.isSalvando = false
                item.errorMessage = traduzirErroImportacao(e)
            }
        }
    }

    fun salvarTodasPendentes() {
        val pendentes = reviewItems.filter { !it.isSalvo }
        if (pendentes.isEmpty()) return
        isSavingAll = true
        coroutineScope.launch {
            for (item in pendentes) {
                item.isSalvando = true
                item.errorMessage = null
                try {
                    val finalPeriodo = item.periodo.trim().ifEmpty { "Escala Atual" }
                    val finalEscala = item.escala.copy(periodo = finalPeriodo)
                    val result = repository.importarEscala(finalEscala)
                    item.isSalvando = false
                    when (result) {
                        is ImportResult.Success -> {
                            item.isSalvo = true
                            item.successResult = result
                            onItemSaved(item.escala, finalPeriodo, result)
                        }
                        is ImportResult.Error -> {
                            item.errorMessage = result.message
                        }
                    }
                } catch (e: Exception) {
                    val httpCode = (e as? HttpException)?.code() ?: (e.cause as? HttpException)?.code()
                    val httpBody = try {
                        (e as? HttpException)?.response()?.errorBody()?.string()
                            ?: (e.cause as? HttpException)?.response()?.errorBody()?.string()
                    } catch (_: Throwable) { null }
                    Log.e("ImportarEscalaSheet", "Erro ao salvar escala em lote (HTTP $httpCode, body='$httpBody'): ${e.message}", e)
                    item.isSalvando = false
                    item.errorMessage = traduzirErroImportacao(e)
                }
            }
            isSavingAll = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (reviewItems.any { it.isSalvo }) onAllFinished() else onDismiss()
        },
        modifier = Modifier.fillMaxWidth(0.96f),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (reviewItems.size > 1) Icons.Filled.AddPhotoAlternate else Icons.Filled.TableChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (reviewItems.size > 1) "Conferir Escalas (${reviewItems.size} fotos/igrejas)" else "Conferir Escala Detectada",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = if (reviewItems.size > 1) {
                        "Revise e confirme cada comunidade individualmente antes de salvar no banco:"
                    } else {
                        "Arquivo: ${reviewItems.firstOrNull()?.escala?.arquivoOrigem ?: ""}"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = reviewItems,
                        key = { index, item -> item.escala.arquivoOrigem + "_" + index }
                    ) { index, item ->
                        val churchName = item.escala.igrejas.firstOrNull()?.nome ?: "Comunidade"
                        val totalEscalacoes = item.escala.igrejas.sumOf { ig -> ig.postos.sumOf { it.escalacoes.size } }
                        val totalPostos = item.escala.igrejas.sumOf { it.postos.size }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_preview_igreja_$index"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isSalvo) successColors.background.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (item.isSalvo) successColors.border else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Church,
                                            contentDescription = null,
                                            tint = if (item.isSalvo) successColors.text else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = churchName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (item.isSalvo) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(successColors.text.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = successColors.text,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Salvo",
                                                    color = successColors.text,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = item.escala.arquivoOrigem,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (item.isSalvo) {
                                    Text(
                                        text = "Escala de \"${item.periodo}\" gravada com sucesso! ${item.successResult?.totalEscalacoes ?: totalEscalacoes} escalações registradas.",
                                        fontSize = 12.sp,
                                        color = successColors.text,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (item.successResult?.avisosConflito?.isNotEmpty() == true) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Avisos de escalas no mesmo dia: ${item.successResult?.avisosConflito?.joinToString("; ")}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = item.periodo,
                                        onValueChange = { item.periodo = it },
                                        label = { Text("Mês / Período da Escala") },
                                        supportingText = { Text("Ex: Outubro de 2026") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_periodo_importado_$index")
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        StatPill(label = "Igrejas", value = item.escala.igrejas.size.toString())
                                        StatPill(label = "Funções", value = totalPostos.toString())
                                        StatPill(label = "Escalações", value = totalEscalacoes.toString())
                                    }

                                    if (item.escala.avisos.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Avisos de leitura / baixa confiança (${item.escala.avisos.size}):",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                item.escala.avisos.forEach { aviso ->
                                                    Text(
                                                        text = "• $aviso",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { item.isExpanded = !item.isExpanded }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (item.isExpanded) "Ocultar detalhes da escala" else "Ver detalhes da escala (${totalPostos} funções)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (item.isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = item.isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp, bottom = 8.dp)
                                        ) {
                                            item.escala.igrejas.forEach { ig ->
                                                Text(
                                                    text = "Dias de serviço: ${ig.datas.joinToString(", ")}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Funções: ${ig.postos.map { it.funcao }.distinct().joinToString(", ")}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (item.errorMessage != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = item.errorMessage!!,
                                            color = errorColors.text,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { salvarItem(item) },
                                        enabled = !item.isSalvando && !isSavingAll,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_salvar_igreja_$index")
                                    ) {
                                        if (item.isSalvando) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Gravando dados no banco...", fontSize = 13.sp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (reviewItems.size > 1) "Confirmar e Salvar Esta Igreja" else "Confirmar e Salvar Escala",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (reviewItems.all { it.isSalvo }) {
                Button(
                    onClick = onAllFinished,
                    modifier = Modifier.testTag("btn_concluir_preview")
                ) {
                    Text("Concluir")
                }
            } else if (reviewItems.size > 1 && reviewItems.any { !it.isSalvo }) {
                Button(
                    onClick = { salvarTodasPendentes() },
                    enabled = !isSavingAll,
                    modifier = Modifier.testTag("btn_salvar_todas_pendentes")
                ) {
                    if (isSavingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gravando todas...", fontSize = 13.sp)
                    } else {
                        Text("Salvar Todas as Pendentes", fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (reviewItems.any { it.isSalvo }) onAllFinished() else onDismiss()
                },
                modifier = Modifier.testTag("btn_cancelar_preview")
            ) {
                Text(if (reviewItems.any { it.isSalvo }) "Fechar" else "Cancelar")
            }
        }
    )
}
