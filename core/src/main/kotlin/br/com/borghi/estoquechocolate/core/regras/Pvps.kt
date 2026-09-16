package br.com.borghi.estoquechocolate.core.regras

import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import java.time.LocalDate

/**
 * PVPS: primeiro que vence, primeiro que sai.
 *
 * Tudo que lista lote passa por aqui, entao a ordem por validade nao depende de a tela lembrar
 * de ordenar.
 */
object Pvps {

    /**
     * Validade primeiro; empate resolvido pelo codigo do lote e depois pelo id, para a ordem ser
     * sempre a mesma entre uma abertura do app e outra.
     */
    val COMPARADOR: Comparator<Lote> = compareBy({ it.validade }, { it.codigoLote }, { it.id })

    fun ordenar(lotes: List<Lote>): List<Lote> = lotes.sortedWith(COMPARADOR)

    /** Lotes que podem sair para venda, ja na ordem de consumo. */
    fun disponiveis(lotes: List<Lote>, hoje: LocalDate): List<Lote> =
        ordenar(lotes.filter { it.disponivelParaVenda(hoje) && it.quantidadeDisponivelParaVenda(hoje).ehPositiva })

    /** O lote que deve ser usado agora. Null quando nao ha nada vendavel. */
    fun proximoLote(lotes: List<Lote>, hoje: LocalDate): Lote? = disponiveis(lotes, hoje).firstOrNull()

    data class ItemConsumo(val lote: Lote, val quantidade: Quantidade)

    /**
     * Plano de consumo em cascata. Cada item e um lote separado com a sua propria quantidade:
     * o app grava uma movimentacao por item, nunca uma quantidade somada de lotes diferentes.
     */
    data class PlanoConsumo(val itens: List<ItemConsumo>, val faltante: Quantidade) {
        val atendeTotalmente: Boolean get() = faltante.ehZero
        val quantidadePlanejada: Quantidade get() = Quantidade.soma(itens.map { it.quantidade })
        val usaMaisDeUmLote: Boolean get() = itens.size > 1
    }

    /**
     * Distribui [quantidade] pelos lotes em ordem de validade.
     *
     * [tipoLocal] limita a origem: uma reposicao so pode tirar do deposito, uma saida de gondola
     * so pode tirar da exposicao. Sem ele, considera todo o estoque vendavel do lote.
     */
    fun planejarConsumo(
        lotes: List<Lote>,
        quantidade: Quantidade,
        hoje: LocalDate,
        tipoLocal: TipoLocal? = null,
    ): PlanoConsumo {
        if (!quantidade.ehPositiva) return PlanoConsumo(emptyList(), Quantidade.ZERO)
        var restante = quantidade
        val itens = mutableListOf<ItemConsumo>()
        for (lote in disponiveis(lotes, hoje)) {
            if (!restante.ehPositiva) break
            val disponivelNoLote = when (tipoLocal) {
                null -> lote.quantidadeDisponivelParaVenda(hoje)
                else -> lote.quantidadeEmTipo(tipoLocal)
            }
            if (!disponivelNoLote.ehPositiva) continue
            val usar = restante.menorEntre(disponivelNoLote)
            itens += ItemConsumo(lote, usar)
            restante -= usar
        }
        return PlanoConsumo(itens, restante)
    }
}
