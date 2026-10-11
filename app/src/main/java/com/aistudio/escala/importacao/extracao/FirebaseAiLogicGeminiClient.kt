package com.aistudio.escala.importacao.extracao

import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementação nativa do cliente Gemini utilizando o SDK Firebase AI Logic (com.google.firebase:firebase-ai).
 *
 * Características de produção:
 * - Utiliza backend Google AI gerenciado: Firebase.ai(backend = GenerativeBackend.googleAI())
 * - Modelo determinístico: gemini-2.5-flash com temperatura 0.0
 * - System Instruction para extração estrita de dados litúrgicos e tratamento de incerteza
 * - Structured Outputs via responseMimeType "application/json" e responseSchema fortemente tipado
 * - Imagem passada diretamente como Bitmap decodificado a partir do ByteArray otimizado
 */
class FirebaseAiLogicGeminiClient : GeminiClient {

    companion object {
        private const val TAG = "FirebaseAiLogicClient"

        /**
         * Esquema estrito estruturado para a resposta do Gemini usando as classes Schema do SDK.
         * Equivalente a GeminiConfig.RESPONSE_SCHEMA_JSON.
         */
        val SCHEMA_LINHA: Schema = Schema.obj(
            properties = mapOf(
                "data" to Schema.string(description = "Data no formato YYYY-MM-DD"),
                "horario" to Schema.string(description = "Horário no formato HH:mm"),
                "igreja" to Schema.string(description = "Nome da comunidade ou paróquia"),
                "funcao" to Schema.string(description = "Função litúrgica correspondente"),
                "nome" to Schema.string(description = "Nome da pessoa escalada"),
                "confianca" to Schema.enumeration(
                    values = listOf("alta", "media", "baixa"),
                    description = "Nível de certeza visual da leitura"
                ),
                "observacao" to Schema.string(description = "Observação opcional em caso de incerteza")
            ),
            optionalProperties = listOf("observacao"),
            description = "Registro individual de escala litúrgica"
        )

        val SCHEMA_ESCALA: Schema = Schema.obj(
            properties = mapOf(
                "linhas" to Schema.array(
                    items = SCHEMA_LINHA,
                    description = "Lista de linhas litúrgicas identificadas no documento"
                )
            ),
            description = "Conjunto de linhas da escala extraída"
        )
    }

    private val generativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = GeminiConfig.MODEL_NAME,
            generationConfig = generationConfig {
                temperature = GeminiConfig.TEMPERATURE
                responseMimeType = "application/json"
                responseSchema = SCHEMA_ESCALA
            },
            systemInstruction = content {
                text(GeminiConfig.SYSTEM_PROMPT)
            }
        )
    }

    override suspend fun extrairJsonEscala(imagemBytes: ByteArray): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Iniciando extração via Firebase AI Logic (bytes: ${imagemBytes.size})...")

        val bitmap = BitmapFactory.decodeByteArray(imagemBytes, 0, imagemBytes.size)
            ?: throw IllegalArgumentException("Não foi possível decodificar os bytes da imagem para envio à IA.")

        try {
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text("Extraia a escala desta imagem conforme as instruções.")
                }
            )

            val jsonRetornado = response.text
            if (jsonRetornado.isNullOrBlank()) {
                throw IllegalStateException("A IA retornou uma resposta textual vazia.")
            }

            Log.i(TAG, "Extração concluída com sucesso via Firebase AI Logic (${jsonRetornado.length} caracteres).")
            jsonRetornado
        } finally {
            bitmap.recycle()
        }
    }
}
