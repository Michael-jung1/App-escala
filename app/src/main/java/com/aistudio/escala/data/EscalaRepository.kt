package com.aistudio.escala.data

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EscalaRepository(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    suspend fun getIgrejas(): List<Igreja> = withContext(Dispatchers.IO) {
        dbHelper.listarIgrejas()
    }

    suspend fun getPessoas(): List<String> = withContext(Dispatchers.IO) {
        dbHelper.listarPessoas()
    }

    suspend fun getEscalaPessoa(nome: String, apenasFuturas: Boolean = false): List<EscalacaoPessoa> = withContext(Dispatchers.IO) {
        dbHelper.buscarEscalaPessoa(nome, apenasFuturas)
    }

    suspend fun getEscalaDoDia(dataServico: String): List<EscalacaoDia> = withContext(Dispatchers.IO) {
        dbHelper.buscarEscalaDoDia(dataServico)
    }

    suspend fun getDatasDisponiveis(): List<DataDisponivel> = withContext(Dispatchers.IO) {
        dbHelper.listarDatasDisponiveis()
    }

    suspend fun autenticarCoordenador(codigoDigitado: String): Coordenador? = withContext(Dispatchers.IO) {
        dbHelper.autenticarCoordenador(codigoDigitado)
    }

    suspend fun loginCoordenador(chaveAcesso: String): Coordenador? = withContext(Dispatchers.IO) {
        dbHelper.autenticarCoordenador(chaveAcesso)
    }

    suspend fun validarSenha(chaveAcesso: String): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        val res = dbHelper.validarSenhaCoordenador(chaveAcesso)
        Pair(res.isValid, res.isAdmin)
    }

    suspend fun validarSenhaResult(chaveAcesso: String): ValidacaoSenhaResult = withContext(Dispatchers.IO) {
        dbHelper.validarSenhaCoordenador(chaveAcesso)
    }

    suspend fun getSenhaCoordenador(): String = withContext(Dispatchers.IO) {
        dbHelper.obterSenhaCoordenador()
    }

    suspend fun getCoordenadoresAcessos(): List<Coordenador> = withContext(Dispatchers.IO) {
        dbHelper.listarTodosCoordenadores()
    }

    suspend fun salvarCoordenadorAcesso(nome: String, chave: String, isAdmin: Boolean): Boolean = withContext(Dispatchers.IO) {
        dbHelper.salvarCoordenadorAcesso(nome, chave, isAdmin)
    }

    suspend fun atualizarCoordenadorAcesso(id: Long, nome: String, chave: String, isAdmin: Boolean): Boolean = withContext(Dispatchers.IO) {
        dbHelper.atualizarCoordenadorAcesso(id, nome, chave, isAdmin)
    }

    suspend fun excluirCoordenadorAcesso(id: Long): Boolean = withContext(Dispatchers.IO) {
        dbHelper.excluirCoordenadorAcesso(id)
    }

    suspend fun getPostosDaIgreja(igrejaId: Long, apenasFuturas: Boolean): List<DiaComPostos> = withContext(Dispatchers.IO) {
        dbHelper.listarPostosDaIgreja(igrejaId, apenasFuturas)
    }

    suspend fun atualizarEscalacao(escalacaoId: Long, novoNome: String, coordenadorId: Long): Boolean = withContext(Dispatchers.IO) {
        dbHelper.atualizarEscalacao(escalacaoId, novoNome, coordenadorId)
    }

    suspend fun verificarOutrasEscalacoesNaData(
        nome: String,
        dataServico: LocalDate,
        igrejaId: Long,
        escalacaoIdAtual: Long? = null
    ): List<String> = withContext(Dispatchers.IO) {
        dbHelper.verificarOutrasEscalacoesNaData(nome, dataServico, igrejaId, escalacaoIdAtual)
    }

    suspend fun getPeriodos(): List<String> = withContext(Dispatchers.IO) {
        dbHelper.listarPeriodos()
    }

    suspend fun importarEscala(resultado: ParsedEscala): ImportResult = withContext(Dispatchers.IO) {
        dbHelper.importarEscalaCompleta(resultado)
    }
}
