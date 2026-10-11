package com.aistudio.escala.importacao.validacao

import com.aistudio.escala.importacao.dados.CadastroRepository
import com.aistudio.escala.importacao.dados.Confianca
import com.aistudio.escala.importacao.dados.LinhaEscala
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Validador puro e determinístico de linhas de escala importadas.
 *
 * REGRAS AVALIADAS:
 * 1. Data inválida ou fora de um intervalo razoável [anoAtual - 1 .. anoAtual + 2] (ERRO)
 * 2. Horário fora do formato HH:mm (ERRO)
 * 3. Função litúrgica vazia (ERRO)
 * 4. Igreja / comunidade vazia (ERRO)
 * 5. Nome vazio (ERRO)
 * 6. Nome inexistente no cadastro local via CadastroRepository (AVISO)
 * 7. Linhas duplicadas: mesma data + horário + igreja + função (ERRO)
 * 8. Conflito: mesma pessoa escalada em mais de uma função no mesmo horário e data (AVISO)
 * 9. Confiança BAIXA marcada pela IA (AVISO)
 *
 * Critério de liberação: NENHUM erro bloqueante (relatorio.podeImportar == true).
 */
class ValidadorEscala(
    private val anoBase: Int? = null
) {

    private val regexHorario = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Valida a lista de linhas e retorna o [RelatorioValidacao] consolidado.
     */
    suspend fun validar(
        linhas: List<LinhaEscala>,
        cadastroRepository: CadastroRepository? = null
    ): RelatorioValidacao {
        val anoAtual = anoBase ?: LocalDate.now().year
        val anoMinimo = anoAtual - 1
        val anoMaximo = anoAtual + 2

        // Cache de nomes conhecidos do cadastro local (se fornecido)
        val nomesCadastrados = cadastroRepository?.obterNomesCadastrados()

        // 1. Agrupamento para detecção de duplicidades exatas (mesma data + horário + igreja + função)
        val contagemDuplicatas = mutableMapOf<String, Int>()
        for (l in linhas) {
            val chave = gerarChaveDuplicata(l)
            contagemDuplicatas[chave] = (contagemDuplicatas[chave] ?: 0) + 1
        }

        // 2. Agrupamento para detecção de mesma pessoa em duas funções no mesmo horário e data
        val contagemPessoaMesmoHorario = mutableMapOf<String, Int>()
        for (l in linhas) {
            val nomeNormalizado = CadastroRepository.normalizar(l.nome)
            if (nomeNormalizado.isNotBlank() && l.data.isNotBlank() && l.horario.isNotBlank()) {
                val chavePessoa = "${l.data.trim()}|${l.horario.trim()}|$nomeNormalizado"
                contagemPessoaMesmoHorario[chavePessoa] = (contagemPessoaMesmoHorario[chavePessoa] ?: 0) + 1
            }
        }

        val linhasValidadas = linhas.mapIndexed { index, linha ->
            val problemas = mutableListOf<ProblemaValidacao>()

            // Regra 1: Data válida e no intervalo razoável
            validarData(linha.data, anoMinimo, anoMaximo, problemas)

            // Regra 2: Horário válido no formato HH:mm
            validarHorario(linha.horario, problemas)

            // Regra 3: Igreja não vazia
            if (linha.igreja.isBlank()) {
                problemas.add(
                    ProblemaValidacao(
                        severidade = Severidade.ERRO,
                        campo = CampoLinha.IGREJA,
                        mensagem = "Igreja / comunidade não pode ficar vazia."
                    )
                )
            }

            // Regra 4: Função não vazia
            if (linha.funcao.isBlank()) {
                problemas.add(
                    ProblemaValidacao(
                        severidade = Severidade.ERRO,
                        campo = CampoLinha.FUNCAO,
                        mensagem = "Função litúrgica não pode ficar vazia."
                    )
                )
            }

            // Regra 5 e 6: Nome vazio (ERRO) ou não cadastrado (AVISO)
            validarNome(linha.nome, nomesCadastrados, problemas)

            // Regra 7: Linhas duplicadas (ERRO)
            val chaveDup = gerarChaveDuplicata(linha)
            if ((contagemDuplicatas[chaveDup] ?: 0) > 1) {
                problemas.add(
                    ProblemaValidacao(
                        severidade = Severidade.ERRO,
                        campo = CampoLinha.GERAL,
                        mensagem = "Linha duplicada: mesma data, horário, igreja e função."
                    )
                )
            }

            // Regra 8: Mesma pessoa em múltiplas funções no mesmo horário (AVISO)
            val nomeNorm = CadastroRepository.normalizar(linha.nome)
            if (nomeNorm.isNotBlank() && linha.data.isNotBlank() && linha.horario.isNotBlank()) {
                val chavePessoa = "${linha.data.trim()}|${linha.horario.trim()}|$nomeNorm"
                if ((contagemPessoaMesmoHorario[chavePessoa] ?: 0) > 1) {
                    problemas.add(
                        ProblemaValidacao(
                            severidade = Severidade.AVISO,
                            campo = CampoLinha.NOME,
                            mensagem = "Mesma pessoa escalada em mais de uma função neste mesmo dia e horário."
                        )
                    )
                }
            }

            // Regra 9: Baixa confiança transcrita pela IA (AVISO)
            if (linha.confianca == Confianca.BAIXA) {
                val msgConfianca = if (linha.observacao.isNotBlank()) {
                    "Baixa confiança na leitura visual: ${linha.observacao}"
                } else {
                    "A IA identificou baixa confiança na transcrição visual desta linha."
                }
                problemas.add(
                    ProblemaValidacao(
                        severidade = Severidade.AVISO,
                        campo = CampoLinha.GERAL,
                        mensagem = msgConfianca
                    )
                )
            }

            LinhaValidada(
                index = index,
                linha = linha,
                problemas = problemas
            )
        }

        return RelatorioValidacao(linhasValidadas)
    }

    private fun validarData(
        dataStr: String,
        anoMinimo: Int,
        anoMaximo: Int,
        problemas: MutableList<ProblemaValidacao>
    ) {
        if (dataStr.isBlank()) {
            problemas.add(
                ProblemaValidacao(
                    severidade = Severidade.ERRO,
                    campo = CampoLinha.DATA,
                    mensagem = "Data não informada (deve ser no formato AAAA-MM-DD)."
                )
            )
            return
        }

        try {
            val parsedDate = LocalDate.parse(dataStr.trim(), dateFormatter)
            if (parsedDate.year < anoMinimo || parsedDate.year > anoMaximo) {
                problemas.add(
                    ProblemaValidacao(
                        severidade = Severidade.ERRO,
                        campo = CampoLinha.DATA,
                        mensagem = "Data fora do intervalo razoável (ano deve estar entre $anoMinimo e $anoMaximo)."
                    )
                )
            }
        } catch (_: DateTimeParseException) {
            problemas.add(
                ProblemaValidacao(
                    severidade = Severidade.ERRO,
                    campo = CampoLinha.DATA,
                    mensagem = "Data inválida: '$dataStr'. Utilize o formato ISO AAAA-MM-DD."
                )
            )
        }
    }

    private fun validarHorario(
        horarioStr: String,
        problemas: MutableList<ProblemaValidacao>
    ) {
        val limpo = horarioStr.trim()
        if (limpo.isBlank()) {
            problemas.add(
                ProblemaValidacao(
                    severidade = Severidade.ERRO,
                    campo = CampoLinha.HORARIO,
                    mensagem = "Horário não informado (deve ser no formato HH:mm)."
                )
            )
            return
        }

        if (!regexHorario.matches(limpo)) {
            problemas.add(
                ProblemaValidacao(
                    severidade = Severidade.ERRO,
                    campo = CampoLinha.HORARIO,
                    mensagem = "Horário inválido: '$horarioStr'. Utilize o formato 24h HH:mm (ex: 08:00, 19:30)."
                )
            )
            return
        }

        try {
            LocalTime.parse(limpo)
        } catch (_: Exception) {
            problemas.add(
                ProblemaValidacao(
                    severidade = Severidade.ERRO,
                    campo = CampoLinha.HORARIO,
                    mensagem = "Horário inválido: '$horarioStr'."
                )
            )
        }
    }

    private fun validarNome(
        nomeStr: String,
        nomesCadastrados: Set<String>?,
        problemas: MutableList<ProblemaValidacao>
    ) {
        if (nomeStr.isBlank()) {
            problemas.add(
                ProblemaValidacao(
                    severidade = Severidade.ERRO,
                    campo = CampoLinha.NOME,
                    mensagem = "Nome do servidor escalado não pode ficar vazio."
                )
            )
            return
        }

        if (nomesCadastrados != null && nomesCadastrados.isNotEmpty()) {
            val normalizado = CadastroRepository.normalizar(nomeStr)
            val encontrado = nomesCadastrados.any { CadastroRepository.normalizar(it) == normalizado }
            if (!encontrado) {
                problemas.add(
                    ProblemaValidacao(
                        severidade = Severidade.AVISO,
                        campo = CampoLinha.NOME,
                        mensagem = "Nome '$nomeStr' não consta no cadastro local de servidores."
                    )
                )
            }
        }
    }

    private fun gerarChaveDuplicata(linha: LinhaEscala): String {
        return "${linha.data.trim()}|${linha.horario.trim()}|${linha.igreja.trim().lowercase()}|${linha.funcao.trim().lowercase()}"
    }
}
