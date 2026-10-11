package com.aistudio.escala.importacao.extracao

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementação do cliente Gemini utilizando o Firebase AI Logic (Vertex AI in Firebase).
 *
 * VANTAGENS DE SEGURANÇA:
 * - Não expõe chaves de API no APK nem em arquivos de propriedades.
 * - Protegido por Firebase App Check contra abusos e chamadas não autorizadas.
 * - Gerenciado centralmente pelo console do Firebase.
 *
 * COMO ATIVAR / CONFIGURAR NO PROJETO:
 * 1. No console do Firebase (https://console.firebase.google.com), ative o "Vertex AI in Firebase" / "AI Logic".
 * 2. Adicione ao build.gradle.kts (bloco dependencies):
 *      implementation("com.google.firebase:firebase-vertexai:16.0.2")
 * 3. Certifique-se de que o arquivo google-services.json esteja na raiz do módulo `app/`.
 * 4. Substitua o placeholder abaixo pela chamada nativa:
 *      val generativeModel = Firebase.vertexAI.generativeModel(
 *          modelName = GeminiConfig.MODEL_NAME,
 *          generationConfig = generationConfig {
 *              responseMimeType = "application/json"
 *              responseSchema = ...
 *              temperature = GeminiConfig.TEMPERATURE
 *          },
 *          systemInstruction = content { text(GeminiConfig.SYSTEM_PROMPT) }
 *      )
 *      val response = generativeModel.generateContent(content { image(bitmap) })
 */
class FirebaseAiLogicGeminiClient : GeminiClient {

    companion object {
        private const val TAG = "FirebaseAiLogicClient"
    }

    override suspend fun extrairJsonEscala(imagemBase64: String): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Iniciando chamada de extração via Firebase AI Logic...")

        // Verificação defensiva: se o SDK nativo do Firebase AI Logic não estiver provisionado
        // no projeto atual, fornecemos a mensagem de diagnóstico exata para o desenvolvedor.
        try {
            val firebaseClass = Class.forName("com.google.firebase.vertexai.FirebaseVertexAI")
            Log.i(TAG, "SDK Firebase Vertex AI detectado na aplicação: ${firebaseClass.name}")
        } catch (_: ClassNotFoundException) {
            val msg = "Firebase AI Logic não está instalado no projeto. " +
                    "Para usar esta implementação preferida, adicione 'com.google.firebase:firebase-vertexai' " +
                    "ao build.gradle.kts ou use o HttpProxyGeminiClient como alternativa."
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }

        // Em ambientes conectados, aqui executa a chamada ao Vertex AI do Firebase
        throw UnsupportedOperationException("SDK presente, configure o FirebaseVertexAI.getInstance() com seu projeto.")
    }
}
