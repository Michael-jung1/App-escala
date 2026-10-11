package com.aistudio.escala.importacao.extracao

/**
 * Interface abstrata para o cliente Gemini multimodal.
 *
 * REGRA DE SEGURANÇA E ARQUITETURA:
 * A chave de API nunca deve ficar gravada em código ou em BuildConfig do aplicativo.
 * A chamada real é delegada ou para o Firebase AI Logic (backend gerenciado com autenticação)
 * ou para um proxy HTTP intermediário configurável.
 */
interface GeminiClient {
    /**
     * Envia a imagem em base64 e retorna a resposta JSON bruta estruturada retornada pelo modelo.
     * @param imagemBase64 Imagem pré-processada codificada em JPEG Base64.
     * @return String contendo o JSON com a chave "linhas".
     * @throws Exception Caso ocorra erro de rede, timeout, resposta inválida ou autorização.
     */
    suspend fun extrairJsonEscala(imagemBase64: String): String
}
