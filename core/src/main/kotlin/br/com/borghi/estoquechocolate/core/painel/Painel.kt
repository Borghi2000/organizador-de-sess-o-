package br.com.borghi.estoquechocolate.core.painel

import br.com.borghi.estoquechocolate.core.modelo.Configuracao
import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.core.regras.Estoque
import br.com.borghi.estoquechocolate.core.regras.Pvps
import java.time.LocalDate

/**
 * Os oito cartoes do painel, na ordem em que devem ser resolvidos.
 *
 * "Vence em ate 7 dias" e "vence entre 8 e 15 dias" nao se sobrepoem de proposito: cada lote
 * aparece em um cartao so, entao os numeros podem ser somados sem contar nada duas vezes.
 */
enum class TipoCartao(val titulo: String, val prioridade: Int, val acao: String) {
    VENCIDOS("Produtos vencidos", 0, "Retirar da venda e segregar agora"),
    VENCE_EM_7_DIAS("Vence em ate 7 dias", 1, "Priorizar a saida destes lotes"),
    VENCE_EM_8_A_15_DIAS("Vence entre 8 e 15 dias", 2, "Planejar a saida"),
    ABAIXO_DO_MINIMO("Abaixo do estoque minimo", 3, "Repor do deposito ou pedir"),
    SEM_CONFERENCIA_RECENTE("Sem conferencia recente", 4, "Conferir na proxima passagem"),
    DIVERGENCIAS_ABERTAS("Divergencias abertas", 5, "Recontar ou ajustar o inventario"),
    PERDAS_DO_MES("Perdas do mes", 6, "Acompanhar o que esta sendo descartado"),
    SEM_ESTOQUE_PARA_VENDA("Sem estoque para venda", 7, "Existe no sistema, mas nao pode ser vendido"),
}

data class ItemPainel(
    val titulo: String,
    val detalhe: String,
    val produtoCodigo: String? = null,
    val loteId: String? = null,
    val divergenciaId: String? = null,
    val classificacao: ClassificacaoValidade? = null,
)

data class CartaoPainel(
    val tipo: TipoCartao,
    val itens: List<ItemPainel> = emptyList(),
    val resumo: String = "",
) {
    val quantidade: Int get() = itens.size
    val temPendencia: Boolean get() = itens.isNotEmpty()
}

data class Painel(val data: LocalDate, val cartoes: List<CartaoPainel>) {

    /** So o que exige acao hoje. E isso que a tela inicial mostra. */
    val pendencias: List<CartaoPainel>
        get() = cartoes.filter { it.temPendencia }.sortedBy { it.tipo.prioridade }

    val tudoEmOrdem: Boolean get() = pendencias.isEmpty()

    val totalDePendencias: Int get() = pendencias.sumOf { it.quantidade }

    fun cartao(tipo: TipoCartao): CartaoPainel = cartoes.first { it.tipo == tipo }
}

object MontadorPainel {

    fun montar(
        produtos: List<Produto>,
        lotes: List<Lote>,
        movimentacoes: List<Movimentacao>,
        divergencias: List<Divergencia>,
        hoje: LocalDate,
        configuracao: Configuracao = Configuracao(),
    ): Painel {
        val porCodigo = produtos.associateBy { it.codigo }
        fun nome(codigo: String) = porCodigo[codigo]?.nome ?: codigo
        fun unidade(codigo: String) = porCodigo[codigo]?.unidade ?: UnidadeMedida.UNIDADE

        val comEstoque = lotes.filter { it.quantidadeAtual.ehPositiva }
        val porClassificacao = Pvps.ordenar(comEstoque).groupBy { ClassificacaoValidade.de(it.validade, hoje) }

        fun cartaoDeValidade(tipo: TipoCartao, classificacao: ClassificacaoValidade): CartaoPainel {
            val itens = porClassificacao[classificacao].orEmpty().map { lote ->
                ItemPainel(
                    titulo = nome(lote.produtoCodigo),
                    detalhe = "Lote ${lote.codigoLote} - ${lote.quantidadeAtual.formatar(unidade(lote.produtoCodigo))} - " +
                        ClassificacaoValidade.descreverPrazo(lote.validade, hoje) +
                        if (lote.segregado) " - segregado" else "",
                    produtoCodigo = lote.produtoCodigo,
                    loteId = lote.id,
                    classificacao = classificacao,
                )
            }
            return CartaoPainel(tipo, itens)
        }

        val resumos = Estoque.resumos(produtos, lotes, hoje)

        val abaixoDoMinimo = resumos.filter { it.abaixoDoMinimo }
            .sortedBy { it.disponivelParaVenda.milesimos }
            .map {
                ItemPainel(
                    titulo = it.produto.nome,
                    detalhe = "Disponivel ${it.disponivelParaVenda.formatar(it.produto.unidade)}, " +
                        "minimo ${it.produto.estoqueMinimo.formatar(it.produto.unidade)} - " +
                        "repor ${it.sugestaoDeCompra.formatar(it.produto.unidade)}",
                    produtoCodigo = it.produto.codigo,
                )
            }

        // Um produto que nunca teve entrada nao e uma pendencia de hoje: e cadastro esperando
        // mercadoria. O cartao mostra o caso que engana de verdade, o estoque que existe mas nao vende.
        val semEstoqueParaVenda = resumos.filter { it.temEstoqueMasNaoPodeVender }
            .sortedBy { it.produto.nome }
            .map {
                ItemPainel(
                    titulo = it.produto.nome,
                    detalhe = "Fisico ${it.fisico.formatar(it.produto.unidade)}, nada disponivel para venda " +
                        "(segregado ${it.segregado.formatar()}, vencido solto ${it.vencidoSolto.formatar()})",
                    produtoCodigo = it.produto.codigo,
                )
            }

        val semConferencia = comEstoque
            .filter { it.semConferenciaDesde(hoje, configuracao.diasSemConferencia) }
            .sortedWith(compareBy({ it.dataUltimaConferencia ?: LocalDate.MIN }, { it.validade }))
            .map { lote ->
                val ultima = lote.dataUltimaConferencia
                ItemPainel(
                    titulo = nome(lote.produtoCodigo),
                    detalhe = "Lote ${lote.codigoLote} - " +
                        if (ultima == null) "nunca conferido"
                        else "ultima conferencia ha ${java.time.temporal.ChronoUnit.DAYS.between(ultima, hoje)} dias",
                    produtoCodigo = lote.produtoCodigo,
                    loteId = lote.id,
                )
            }

        val abertas = divergencias.filter { it.estaAberta }.sortedBy { it.data }.map {
            ItemPainel(
                titulo = nome(it.produtoCodigo),
                detalhe = it.descricao(unidade(it.produtoCodigo)),
                produtoCodigo = it.produtoCodigo,
                loteId = it.loteId,
                divergenciaId = it.id,
            )
        }

        val inicioDoMes = hoje.withDayOfMonth(1)
        val perdasDoMes = movimentacoes
            .filter { it.tipo.ehPerda && !it.dataHora.toLocalDate().isBefore(inicioDoMes) }
            .sortedByDescending { it.dataHora }
        val perdasPorProduto = perdasDoMes.groupBy { it.produtoCodigo }.map { (codigo, lista) ->
            ItemPainel(
                titulo = nome(codigo),
                detalhe = "${Quantidade.soma(lista.map { it.quantidade }).formatar(unidade(codigo))} " +
                    "em ${lista.size} registro(s)",
                produtoCodigo = codigo,
            )
        }.sortedByDescending { it.titulo }
        val totalPerdido = Quantidade.soma(perdasDoMes.map { it.quantidade })

        val cartoes = listOf(
            cartaoDeValidade(TipoCartao.VENCIDOS, ClassificacaoValidade.VENCIDO),
            cartaoDeValidade(TipoCartao.VENCE_EM_7_DIAS, ClassificacaoValidade.CRITICO),
            cartaoDeValidade(TipoCartao.VENCE_EM_8_A_15_DIAS, ClassificacaoValidade.URGENTE),
            CartaoPainel(TipoCartao.ABAIXO_DO_MINIMO, abaixoDoMinimo),
            CartaoPainel(TipoCartao.SEM_CONFERENCIA_RECENTE, semConferencia),
            CartaoPainel(TipoCartao.DIVERGENCIAS_ABERTAS, abertas),
            CartaoPainel(
                TipoCartao.PERDAS_DO_MES,
                perdasPorProduto,
                resumo = if (perdasDoMes.isEmpty()) "" else "Total perdido no mes: ${totalPerdido.formatar()}",
            ),
            CartaoPainel(TipoCartao.SEM_ESTOQUE_PARA_VENDA, semEstoqueParaVenda),
        )
        return Painel(hoje, cartoes)
    }
}
