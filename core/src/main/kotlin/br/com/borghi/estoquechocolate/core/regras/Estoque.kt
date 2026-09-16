package br.com.borghi.estoquechocolate.core.regras

import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import java.time.LocalDate

/**
 * Os numeros de um produto, separados por natureza.
 *
 * Existir no sistema nao e a mesma coisa que estar disponivel para venda: um lote vencido continua
 * no [fisico] ate alguem descartar, mas sai do [disponivelParaVenda] na hora em que vence.
 */
data class ResumoEstoque(
    val produto: Produto,
    /** Tudo que esta fisicamente na secao, inclusive vencido e segregado. */
    val fisico: Quantidade,
    /** O que esta na gondola ou vitrine, vendavel ou nao. */
    val exposto: Quantidade,
    /** O que esta guardado no deposito. */
    val deposito: Quantidade,
    /** O que ja foi separado por vencimento ou avaria. */
    val segregado: Quantidade,
    /** Vencido que ainda nao foi segregado: esta na loja e nao deveria estar. */
    val vencidoSolto: Quantidade,
    /** O unico numero que pode ser vendido. */
    val disponivelParaVenda: Quantidade,
) {
    val abaixoDoMinimo: Boolean get() = disponivelParaVenda < produto.estoqueMinimo
    val semEstoqueParaVenda: Boolean get() = !disponivelParaVenda.ehPositiva
    val existeNoSistema: Boolean get() = fisico.ehPositiva
    val acimaDoMaximo: Boolean get() = disponivelParaVenda > produto.estoqueMaximo

    /**
     * Caso classico de estoque que engana: o sistema mostra produto, mas nada dele pode ser
     * vendido. E o cartao "sem estoque para venda" do painel.
     */
    val temEstoqueMasNaoPodeVender: Boolean get() = semEstoqueParaVenda && existeNoSistema

    /** Quanto pedir para voltar ao maximo. */
    val sugestaoDeCompra: Quantidade get() = (produto.estoqueMaximo - disponivelParaVenda).naoNegativa()

    /** Quanto falta para sair do vermelho do minimo. */
    val faltaParaOMinimo: Quantidade get() = (produto.estoqueMinimo - disponivelParaVenda).naoNegativa()
}

object Estoque {

    fun resumo(produto: Produto, lotes: List<Lote>, hoje: LocalDate): ResumoEstoque {
        val doProduto = lotes.filter { it.produtoCodigo == produto.codigo }
        return ResumoEstoque(
            produto = produto,
            fisico = Quantidade.soma(doProduto.map { it.quantidadeAtual }),
            exposto = Quantidade.soma(doProduto.map { it.quantidadeEmTipo(TipoLocal.EXPOSICAO) }),
            deposito = Quantidade.soma(doProduto.map { it.quantidadeEmTipo(TipoLocal.DEPOSITO) }),
            segregado = Quantidade.soma(doProduto.filter { it.segregado }.map { it.quantidadeAtual }),
            vencidoSolto = Quantidade.soma(
                doProduto.filter { it.estaVencido(hoje) && !it.segregado }.map { it.quantidadeAtual },
            ),
            disponivelParaVenda = Quantidade.soma(doProduto.map { it.quantidadeDisponivelParaVenda(hoje) }),
        )
    }

    fun resumos(produtos: List<Produto>, lotes: List<Lote>, hoje: LocalDate): List<ResumoEstoque> {
        val porProduto = lotes.groupBy { it.produtoCodigo }
        return produtos.map { resumo(it, porProduto[it.codigo].orEmpty(), hoje) }
    }

    fun abaixoDoMinimo(produtos: List<Produto>, lotes: List<Lote>, hoje: LocalDate): List<ResumoEstoque> =
        resumos(produtos, lotes, hoje).filter { it.abaixoDoMinimo }
            .sortedBy { it.disponivelParaVenda.milesimos }

    fun semEstoqueParaVenda(produtos: List<Produto>, lotes: List<Lote>, hoje: LocalDate): List<ResumoEstoque> =
        resumos(produtos, lotes, hoje).filter { it.semEstoqueParaVenda }.sortedBy { it.produto.nome }
}
