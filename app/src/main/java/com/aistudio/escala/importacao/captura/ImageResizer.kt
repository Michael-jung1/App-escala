package com.aistudio.escala.importacao.captura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * Utilitário para redimensionamento e compressão de imagens escaneadas antes do envio à IA:
 * - Lado maior reduzido para até 2048 px (preservando proporções originais)
 * - Compressão JPEG qualidade 85
 * - Retorno de ByteArray direto e arquivo temporário no cache interno do app
 */
object ImageResizer {
    private const val TAG = "ImageResizer"
    const val MAX_DIMENSION = 2048
    const val JPEG_QUALITY = 85

    /**
     * Processa a imagem apontada por [imageUri]:
     * Reduz dimensões, comprime em JPEG 85, salva arquivo temporário em [context.cacheDir]
     * e retorna os bytes otimizados e o arquivo gerado.
     */
    suspend fun processarEComprimir(
        context: Context,
        imageUri: Uri
    ): ProcessamentoImagemResult = withContext(Dispatchers.IO) {
        val inputStream: InputStream = context.contentResolver.openInputStream(imageUri)
            ?: throw IllegalArgumentException("Não foi possível abrir o arquivo da imagem selecionada.")

        // 1. Decodifica dimensões sem carregar pixels completos na memória
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val origWidth = options.outWidth
        val origHeight = options.outHeight

        // 2. Calcula sample size para economia de memória
        var sampleSize = 1
        val maxOrig = max(origWidth, origHeight)
        while (maxOrig / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }

        // 3. Decodifica bitmap reduzido
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val streamForDecode = context.contentResolver.openInputStream(imageUri)
            ?: throw IllegalArgumentException("Falha ao recarregar stream da imagem.")
        val decodedBitmap = BitmapFactory.decodeStream(streamForDecode, null, decodeOptions)
            ?: throw IllegalStateException("Falha ao decodificar a imagem capturada.")
        streamForDecode.close()

        // 4. Redimensiona exatamente para caber em 2048 px no maior lado
        val currentMax = max(decodedBitmap.width, decodedBitmap.height)
        val finalBitmap = if (currentMax > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / currentMax.toFloat()
            val targetW = (decodedBitmap.width * scale).toInt()
            val targetH = (decodedBitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(decodedBitmap, targetW, targetH, true)
            if (scaled != decodedBitmap) {
                decodedBitmap.recycle()
            }
            scaled
        } else {
            decodedBitmap
        }

        // 5. Comprime para JPEG qualidade 85
        val baos = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        val jpegBytes = baos.toByteArray()
        finalBitmap.recycle()

        // 6. Grava arquivo temporário no cacheDir
        val tempFile = File(context.cacheDir, "scan_temp_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { fos ->
            fos.write(jpegBytes)
            fos.flush()
        }

        ProcessamentoImagemResult(
            tempFile = tempFile,
            imageBytes = jpegBytes,
            bytes = jpegBytes.size
        )
    }

    /**
     * Limpa com segurança todos os arquivos temporários de scan no cacheDir.
     */
    fun limparArquivosTemporarios(context: Context) {
        try {
            val cacheFiles = context.cacheDir.listFiles { _, name ->
                name.startsWith("scan_temp_") && (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            }
            cacheFiles?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha não-crítica ao limpar cache de imagens: ${e.message}")
        }
    }
}

data class ProcessamentoImagemResult(
    val tempFile: File,
    val imageBytes: ByteArray,
    val bytes: Int
) {
    val base64: String
        get() = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
}
