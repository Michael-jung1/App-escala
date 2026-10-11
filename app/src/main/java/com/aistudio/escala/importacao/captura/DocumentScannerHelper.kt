package com.aistudio.escala.importacao.captura

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Utilitário para inicialização e obtenção do IntentSender do ML Kit Document Scanner.
 * Configurado conforme os requisitos:
 * - Modo FULL (ajuste de bordas, rotação, filtros)
 * - Formato de resultado: JPEG
 * - Limite de páginas: 1 (uma escala mensal completa por documento)
 * - Importação da galeria permitida
 */
object DocumentScannerHelper {

    fun criarOpcoesScanner(): GmsDocumentScannerOptions {
        return GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setPageLimit(1)
            .setGalleryImportAllowed(true)
            .build()
    }

    fun obterClienteScanner(context: Context): GmsDocumentScanner {
        return GmsDocumentScanning.getClient(criarOpcoesScanner())
    }

    /**
     * Inicia a requisição do scanner com o Activity informado e retorna o IntentSenderRequest
     * para lançamento via ActivityResultContracts.StartIntentSenderForResult.
     */
    suspend fun obterIntentSenderRequest(activity: Activity): IntentSenderRequest =
        suspendCancellableCoroutine { continuation ->
            val scanner = obterClienteScanner(activity)
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    val request = IntentSenderRequest.Builder(intentSender).build()
                    continuation.resume(request)
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    /**
     * Extrai o Uri da página capturada a partir do Intent de resultado retornado pelo scanner.
     */
    fun extrairUriResultado(data: Intent?): Uri? {
        if (data == null) return null
        val resultado = GmsDocumentScanningResult.fromActivityResultIntent(data)
        return resultado?.pages?.firstOrNull()?.imageUri
    }
}
