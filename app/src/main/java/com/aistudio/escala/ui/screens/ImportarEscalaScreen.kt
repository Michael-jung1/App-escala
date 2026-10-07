package com.aistudio.escala.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.escala.data.EscalaRepository
import com.aistudio.escala.data.ImportResult
import com.aistudio.escala.data.ParsedEscala
import com.aistudio.escala.parser.EscalaDocumentManager
import com.aistudio.escala.parser.GeminiScheduleParser
import com.aistudio.escala.ui.components.PreviewMultiEscalaDialog
import com.aistudio.escala.ui.theme.getErrorColor
import com.aistudio.escala.ui.theme.getSuccessColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportarEscalaScreen(
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
                    Log.e("ImportarEscalaScreen", "Falha ao processar $fileName", e)
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
                    Log.e("ImportarEscalaScreen", "Falha ao abrir seletor múltiplo de fotos", e)
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
                    Log.e("ImportarEscalaScreen", "Falha ao abrir seletor de documentos", e)
                    errorMessage = "Não foi possível abrir o seletor de arquivos no dispositivo."
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Atualização de escalas",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Importar Escala Mensal",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = "Envie a planilha ou documento com a nova escala mensal para atualizar o sistema para todas as comunidades.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // Upload Zone Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("importar_escala_screen_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = "Upload",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Selecione o arquivo da escala",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Suporte para fotos/imagens (.jpg, .png), planilhas Excel (.xlsx), documentos PDF (.pdf) e listas CSV/Texto (.txt)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Format Shortcuts
                    Text(
                        text = "Filtrar por formato específico:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ImportFormatBadge(
                            label = "Fotos / Imagens",
                            icon = Icons.Filled.AddPhotoAlternate,
                            enabled = !isProcessing,
                            onClick = { launchFilePicker("foto") }
                        )
                        ImportFormatBadge(
                            label = "Excel (.xlsx)",
                            icon = Icons.Filled.TableChart,
                            enabled = !isProcessing,
                            onClick = { launchFilePicker("excel") }
                        )
                        ImportFormatBadge(
                            label = "PDF (.pdf)",
                            icon = Icons.Filled.PictureAsPdf,
                            enabled = !isProcessing,
                            onClick = { launchFilePicker("pdf") }
                        )
                        ImportFormatBadge(
                            label = "CSV / TXT",
                            icon = Icons.Filled.Description,
                            enabled = !isProcessing,
                            onClick = { launchFilePicker("text") }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Action Button
                    Button(
                        onClick = { launchFilePicker(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_selecionar_arquivo_escala"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = processingStep, fontSize = 14.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Procurar Arquivo no Dispositivo",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Success banner
        if (successResult != null) {
            item {
                val res = successResult!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(successColors.background)
                        .border(1.dp, successColors.border, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = successColors.text,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Escala de \"${res.periodo}\" importada com sucesso!",
                                color = successColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${res.totalEscalacoes} escalações salvas em ${res.totalIgrejas} igrejas/comunidades.",
                                color = successColors.text.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                if (res.avisosConflito.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Avisos de escalas no mesmo dia (${res.avisosConflito.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            res.avisosConflito.take(5).forEach { aviso ->
                                Text(
                                    text = "• $aviso",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            if (res.avisosConflito.size > 5) {
                                Text(
                                    text = "+ mais ${res.avisosConflito.size - 5} aviso(s)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error banner
        if (errorMessage != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(errorColors.background)
                        .border(1.dp, errorColors.border, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = errorColors.text,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Falha ao processar arquivo",
                                color = errorColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = errorMessage ?: "",
                                color = errorColors.text,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "Dica: certifique-se de que a planilha contém as colunas de data/missa e nomes, ou tente salvar como .xlsx.",
                                color = errorColors.text.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Fechar aviso",
                                tint = errorColors.text,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Informative guidance box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Como funciona o processamento?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. O sistema lê as tabelas ou textos identificando os nomes das igrejas (Ex: Santuário, Sagrado Coração, São José).\n" +
                                "2. Associa datas, horários de missas e funções de coroinhas/acólitos.\n" +
                                "3. Uma tela de confirmação é exibida para você revisar antes de salvar os dados no aplicativo.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
private fun ImportFormatBadge(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
