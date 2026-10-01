package com.aistudio.escala.util

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    private val REGEX_DIA = Regex("""\b(\d{1,2})\b""")

    private val MESES_PT = mapOf(
        "janeiro" to 1, "fevereiro" to 2, "março" to 3, "marco" to 3, "abril" to 4,
        "maio" to 5, "junho" to 6, "julho" to 7, "agosto" to 8, "setembro" to 9,
        "outubro" to 10, "novembro" to 11, "dezembro" to 12
    )

    private val DIAS_SEMANA_COMPLETO = mapOf(
        "domingo" to "Domingo",
        "segunda" to "Segunda-feira",
        "terça" to "Terça-feira",
        "terca" to "Terça-feira",
        "quarta" to "Quarta-feira",
        "quinta" to "Quinta-feira",
        "sexta" to "Sexta-feira",
        "sábado" to "Sábado",
        "sabado" to "Sábado"
    )

    fun extrairMesAno(referenciaPeriodo: String): Pair<Int, Int>? {
        val partes = referenciaPeriodo.lowercase().replace(" de ", " ").split(" ")
        var mes: Int? = null
        var ano: Int? = null
        for (p in partes) {
            val trimmed = p.trim()
            if (MESES_PT.containsKey(trimmed)) {
                mes = MESES_PT[trimmed]
            } else if (trimmed.length == 4 && trimmed.all { it.isDigit() }) {
                ano = trimmed.toIntOrNull()
            }
        }
        return if (mes != null && ano != null) Pair(mes, ano) else null
    }

    fun converterDataServico(dataServico: String, referenciaPeriodo: String): LocalDate? {
        val mesAno = extrairMesAno(referenciaPeriodo) ?: return null
        val (mes, ano) = mesAno
        val primeiroNumero = Regex("""\b(\d{1,2})\b""").find(dataServico)?.value
        val dia = primeiroNumero?.toIntOrNull() ?: return null

        if (dia !in 1..31) return null

        return try {
            LocalDate.of(ano, mes, dia)
        } catch (e: DateTimeException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun formatarISO(ano: Int, mes: Int, dia: Int): String {
        return "%04d-%02d-%02d".format(Locale.ROOT, ano, mes, dia)
    }

    fun formatarDataExtenso(localDate: LocalDate): String {
        val diaSemana = when (localDate.dayOfWeek.value) {
            1 -> "Segunda-feira"
            2 -> "Terça-feira"
            3 -> "Quarta-feira"
            4 -> "Quinta-feira"
            5 -> "Sexta-feira"
            6 -> "Sábado"
            7 -> "Domingo"
            else -> ""
        }
        val nomeMes = when (localDate.monthValue) {
            1 -> "janeiro"
            2 -> "fevereiro"
            3 -> "março"
            4 -> "abril"
            5 -> "maio"
            6 -> "junho"
            7 -> "julho"
            8 -> "agosto"
            9 -> "setembro"
            10 -> "outubro"
            11 -> "novembro"
            12 -> "dezembro"
            else -> ""
        }
        return "$diaSemana, ${localDate.dayOfMonth} de $nomeMes"
    }

    fun humanizarDataISO(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            val parsed = LocalDate.parse(isoDate)
            formatarDataExtenso(parsed)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun normalizarFuncao(funcao: String): String {
        val trim = funcao.trim()
        if (trim.equals("cerimonário", ignoreCase = true)) return "Cerimoniário"
        return trim
    }

    fun isDataPassada(localDate: LocalDate?, hoje: LocalDate = LocalDate.now()): Boolean {
        if (localDate == null) return false
        return localDate.isBefore(hoje)
    }

    fun isDataPassada(isoDate: String?, hoje: LocalDate = LocalDate.now()): Boolean {
        if (isoDate.isNullOrBlank()) return false
        return try {
            val parsed = LocalDate.parse(isoDate)
            parsed.isBefore(hoje)
        } catch (e: Exception) {
            false
        }
    }
}
