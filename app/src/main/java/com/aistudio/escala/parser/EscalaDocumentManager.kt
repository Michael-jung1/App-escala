package com.aistudio.escala.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.aistudio.escala.data.ParsedEscala
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EscalaDocumentManager {
    private const val TAG = "EscalaDocumentManager"

    fun obterNomeArquivo(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "arquivo_escala"
    }

    suspend fun processarArquivo(context: Context, uri: Uri): ParsedEscala = withContext(Dispatchers.IO) {
        val fileSize = GeminiScheduleParser.getFileSize(context, uri)
        if (fileSize > GeminiScheduleParser.MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException(GeminiScheduleParser.ERROR_FILE_TOO_LARGE)
        }

        val fileName = obterNomeArquivo(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val lowerName = fileName.lowercase()

        Log.d(TAG, "Processando arquivo: $fileName (MIME: $mimeType)")

        // 1. Se for Excel .xlsx, usa o parser nativo ultra rápido
        if (lowerName.endsWith(".xlsx") ||
            mimeType.contains("spreadsheetml") ||
            mimeType.contains("excel")
        ) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Não foi possível abrir o arquivo Excel.")
                val parsed = inputStream.use { stream ->
                    XlsxScheduleParser.parse(stream, fileName)
                }

                val totalEsc = parsed.igrejas.sumOf { ig -> ig.postos.sumOf { it.escalacoes.size } }
                if (totalEsc > 0) {
                    Log.d(TAG, "Parser XLSX nativo extraiu $totalEsc escalações com sucesso!")
                    return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Parser nativo XLSX encontrou formato atípico, tentando Gemini...", e)
            }
        }

        // 2. Se for arquivo de texto ou CSV, tenta parser textual local primeiro
        if (lowerName.endsWith(".csv") || lowerName.endsWith(".txt") ||
            mimeType.contains("text/csv") || mimeType.contains("text/plain")
        ) {
            val textParsed = TextScheduleParser.parse(context, uri, fileName)
            if (textParsed != null && textParsed.igrejas.any { it.postos.isNotEmpty() }) {
                Log.d(TAG, "Parser de texto local extraiu a escala com sucesso!")
                return@withContext textParsed
            }
        }

        // 3. Se for PDF, Imagem, Texto complexo ou se o XLSX precisou de fallback: Gemini Multimodal
        GeminiScheduleParser.parseDocumentWithGemini(
            context = context,
            uri = uri,
            fileName = fileName,
            mimeType = mimeType
        )
    }
}
