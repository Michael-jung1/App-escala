package com.aistudio.escala.importacao.extracao

/**
 * Interface abstrata para o cliente Gemini multimodal.
 *
 * Recebe a imagem já otimizada (redimensionada e comprimida em JPEG) como [ByteArray].
 */
interface GeminiClient {
    /**
     * Envia os bytes da imagem (JPEG) e retorna o JSON estruturado bruto gerado pelo modelo.
     *
     * @param imagemBytes Bytes compactados em JPEG da escala escaneada.
     * @return String contendo o JSON com a chave "linhas".
     * @throws Exception Em caso de falha de rede, timeout, resposta inválida ou autorização.
     */
    suspend fun extrairJsonEscala(imagemBytes: ByteArray): String
}
