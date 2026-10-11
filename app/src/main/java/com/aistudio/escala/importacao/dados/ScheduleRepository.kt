package com.aistudio.escala.importacao.dados

/**
 * Interface responsável pela Etapa B: gravação determinística das linhas validadas no banco de dados SQLite.
 *
 * REGRA ARQUITETURAL:
 * Esta interface NUNCA processa ou recebe imagens. Ela opera exclusivamente sobre linhas de dados já validadas e aprovadas.
 */
interface ScheduleRepository {

    /**
     * Verifica com antecedência se alguma das linhas a importar sobrescreverá uma escalação já existente
     * para a mesma igreja, data de serviço e posto/função.
     * Retorna a lista de conflitos onde a pessoa existente for DIFERENTE da nova pessoa proposta.
     */
    suspend fun verificarConflitosExistentes(linhas: List<LinhaEscala>): List<ConflitoEscala>

    /**
     * Persiste as linhas validadas na base de dados SQLite (merge / update com transação atômica).
     * @param linhas Lista de linhas aprovadas na revisão humana sem nenhum erro bloqueante.
     * @return [Result] contendo o [ResumoGravacao] com contagens reais de inseridos, atualizados e substituídos.
     */
    suspend fun salvarEscala(linhas: List<LinhaEscala>): Result<ResumoGravacao>
}

/**
 * Implementação padrão de exemplo em memória.
 */
class ExemploScheduleRepository : ScheduleRepository {

    override suspend fun verificarConflitosExistentes(linhas: List<LinhaEscala>): List<ConflitoEscala> {
        return emptyList()
    }

    override suspend fun salvarEscala(linhas: List<LinhaEscala>): Result<ResumoGravacao> {
        return Result.success(
            ResumoGravacao(
                inseridos = linhas.size,
                atualizados = 0,
                substituidos = 0
            )
        )
    }
}
