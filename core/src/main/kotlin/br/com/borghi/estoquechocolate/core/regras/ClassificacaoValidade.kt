package br.com.borghi.estoquechocolate.core.regras

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Faixas de validade. A prioridade e o que ordena o painel: quanto menor, mais em cima aparece.
 */
enum class ClassificacaoValidade(
    val rotulo: String,
    val prioridade: Int,
    val descricao: String,
) {
    VENCIDO("Vencido", 0, "Validade anterior a hoje"),
    CRITICO("Critico", 1, "Vence em ate 7 dias"),
    URGENTE("Urgente", 2, "Vence entre 8 e 15 dias"),
    ATENCAO("Atencao", 3, "Vence entre 16 e 30 dias"),
    MONITORAR("Monitorar", 4, "Vence entre 31 e 60 dias"),
    NORMAL("Normal", 5, "Vence em mais de 60 dias");

    val exigeAcao: Boolean get() = this == VENCIDO || this == CRITICO || this == URGENTE

    companion object {

        fun diasAteVencer(validade: LocalDate, hoje: LocalDate): Long =
            ChronoUnit.DAYS.between(hoje, validade)

        /**
         * Produto que vence hoje ainda esta dentro da validade: a regra diz "vencido e validade
         * ANTERIOR a data atual". Ele entra como critico, com zero dia de prazo.
         */
        fun de(validade: LocalDate, hoje: LocalDate): ClassificacaoValidade =
            when (val dias = diasAteVencer(validade, hoje)) {
                in Long.MIN_VALUE..-1L -> VENCIDO
                in 0L..7L -> CRITICO
                in 8L..15L -> URGENTE
                in 16L..30L -> ATENCAO
                in 31L..60L -> MONITORAR
                else -> {
                    check(dias > 60)
                    NORMAL
                }
            }

        /** Texto curto para a lista: "vencido ha 3 dias", "vence hoje", "vence em 12 dias". */
        fun descreverPrazo(validade: LocalDate, hoje: LocalDate): String =
            when (val dias = diasAteVencer(validade, hoje)) {
                0L -> "vence hoje"
                1L -> "vence amanha"
                -1L -> "vencido ha 1 dia"
                in Long.MIN_VALUE..-2L -> "vencido ha ${-dias} dias"
                else -> "vence em $dias dias"
            }
    }
}
