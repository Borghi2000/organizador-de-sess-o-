package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Quantidade guardada em milesimos de unidade, sempre como numero inteiro.
 *
 * Estoque nao aceita erro de arredondamento: 0,1 + 0,2 em ponto flutuante nao da 0,3, e num
 * controle de validade isso vira divergencia fantasma. Guardando milesimos em [Long] a conta
 * fecha sempre, e ainda assim da para registrar 1,5 kg de chocolate a granel.
 */
@Serializable
@JvmInline
value class Quantidade(val milesimos: Long) : Comparable<Quantidade> {

    val ehZero: Boolean get() = milesimos == 0L
    val ehPositiva: Boolean get() = milesimos > 0L
    val ehNegativa: Boolean get() = milesimos < 0L

    operator fun plus(outra: Quantidade) = Quantidade(milesimos + outra.milesimos)
    operator fun minus(outra: Quantidade) = Quantidade(milesimos - outra.milesimos)
    operator fun unaryMinus() = Quantidade(-milesimos)

    override fun compareTo(other: Quantidade): Int = milesimos.compareTo(other.milesimos)

    fun valorAbsoluto() = Quantidade(abs(milesimos))

    fun naoNegativa() = if (ehNegativa) ZERO else this

    fun menorEntre(outra: Quantidade) = if (this <= outra) this else outra

    /** Formata no padrao brasileiro, sem casas decimais inuteis: 3 / 1,5 / 0,25 */
    fun formatar(): String {
        val sinal = if (milesimos < 0) "-" else ""
        val absoluto = abs(milesimos)
        val inteiro = absoluto / 1000
        val resto = (absoluto % 1000).toInt()
        if (resto == 0) return "$sinal$inteiro"
        val decimais = resto.toString().padStart(3, '0').trimEnd('0')
        return "$sinal$inteiro,$decimais"
    }

    fun formatar(unidade: UnidadeMedida): String = "${formatar()} ${unidade.abreviacao}"

    override fun toString(): String = formatar()

    companion object {
        val ZERO = Quantidade(0)

        fun deInteiro(valor: Int) = Quantidade(valor.toLong() * 1000)

        /**
         * Le o que a pessoa digitou. Aceita virgula ou ponto como separador decimal.
         * Devolve null quando o texto nao e um numero valido, para a tela poder apontar o campo
         * em vez de gravar um palpite.
         */
        fun deTexto(texto: String): Quantidade? {
            val limpo = texto.trim().replace('.', ',')
            if (limpo.isEmpty()) return null
            val negativo = limpo.startsWith("-")
            val corpo = limpo.removePrefix("-")
            if (corpo.isEmpty()) return null
            val partes = corpo.split(",")
            if (partes.size > 2) return null
            val textoInteiro = partes[0].ifEmpty { "0" }
            val textoDecimal = partes.getOrNull(1) ?: ""
            if (textoDecimal.length > 3) return null
            if (!textoInteiro.all { it.isDigit() }) return null
            if (!textoDecimal.all { it.isDigit() }) return null
            val inteiro = textoInteiro.toLongOrNull() ?: return null
            val decimal = textoDecimal.padEnd(3, '0').toLong()
            val total = inteiro * 1000 + decimal
            return Quantidade(if (negativo) -total else total)
        }

        fun soma(quantidades: Iterable<Quantidade>): Quantidade =
            Quantidade(quantidades.sumOf { it.milesimos })
    }
}
