package br.com.borghi.estoquechocolate.core.relatorio

import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.StatusConferencia
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.core.regras.Estoque
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LinhaResumo(val rotulo: String, val ocorrencias: Int, val quantidade: Quantidade)

data class Relatorio(
    val titulo: String,
    val inicio: LocalDate,
    val fim: LocalDate,
    val movimentacoesPorTipo: List<LinhaResumo>,
    val perdasPorMotivo: List<LinhaResumo>,
    val perdasPorProduto: List<LinhaResumo>,
    val degustacoes: LinhaResumo,
    val totalEntradas: Quantidade,
    val totalSaidas: Quantidade,
    val totalPerdas: Quantidade,
    val lotesVencidosNoPeriodo: List<String>,
    val divergenciasAbertas: Int,
    val divergenciasResolvidas: Int,
    val produtosAbaixoDoMinimo: List<String>,
    val produtosSemEstoqueParaVenda: List<String>,
    val diasDoPeriodo: Int,
    val diasComConferencia: Int,
) {
    val diasSemConferencia: Int get() = diasDoPeriodo - diasComConferencia

    fun paraTexto(): String = buildString {
        appendLine(titulo.uppercase())
        appendLine("Periodo: ${formatar(inicio)} a ${formatar(fim)}")
        appendLine()
        appendLine("MOVIMENTACOES")
        if (movimentacoesPorTipo.isEmpty()) {
            appendLine("  Nenhuma movimentacao registrada no periodo.")
        } else {
            movimentacoesPorTipo.forEach {
                appendLine("  ${it.rotulo}: ${it.quantidade.formatar()} em ${it.ocorrencias} registro(s)")
            }
        }
        appendLine("  Total de entradas: ${totalEntradas.formatar()}")
        appendLine("  Total de saidas: ${totalSaidas.formatar()}")
        appendLine()
        appendLine("PERDAS")
        appendLine("  Total perdido: ${totalPerdas.formatar()}")
        perdasPorMotivo.forEach {
            appendLine("  ${it.rotulo}: ${it.quantidade.formatar()} em ${it.ocorrencias} registro(s)")
        }
        if (perdasPorProduto.isNotEmpty()) {
            appendLine("  Por produto:")
            perdasPorProduto.forEach { appendLine("    ${it.rotulo}: ${it.quantidade.formatar()}") }
        }
        appendLine("  Degustacao (nao contada como perda): ${degustacoes.quantidade.formatar()}")
        appendLine()
        appendLine("VALIDADE")
        if (lotesVencidosNoPeriodo.isEmpty()) {
            appendLine("  Nenhum lote venceu no periodo.")
        } else {
            lotesVencidosNoPeriodo.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("DIVERGENCIAS")
        appendLine("  Resolvidas no periodo: $divergenciasResolvidas")
        appendLine("  Ainda abertas: $divergenciasAbertas")
        appendLine()
        appendLine("REPOSICAO")
        if (produtosAbaixoDoMinimo.isEmpty()) {
            appendLine("  Nenhum produto abaixo do minimo.")
        } else {
            produtosAbaixoDoMinimo.forEach { appendLine("  $it") }
        }
        if (produtosSemEstoqueParaVenda.isNotEmpty()) {
            appendLine("  Sem estoque para venda:")
            produtosSemEstoqueParaVenda.forEach { appendLine("    $it") }
        }
        appendLine()
        appendLine("CONFERENCIA")
        appendLine("  Dias com conferencia fechada: $diasComConferencia de $diasDoPeriodo")
        appendLine("  Dias sem conferencia: $diasSemConferencia")
    }

    fun paraCsv(): String = buildString {
        appendLine("secao;item;ocorrencias;quantidade")
        movimentacoesPorTipo.forEach { appendLine("movimentacao;${it.rotulo};${it.ocorrencias};${it.quantidade.formatar()}") }
        perdasPorMotivo.forEach { appendLine("perda_motivo;${it.rotulo};${it.ocorrencias};${it.quantidade.formatar()}") }
        perdasPorProduto.forEach { appendLine("perda_produto;${it.rotulo};${it.ocorrencias};${it.quantidade.formatar()}") }
        appendLine("degustacao;${degustacoes.rotulo};${degustacoes.ocorrencias};${degustacoes.quantidade.formatar()}")
        lotesVencidosNoPeriodo.forEach { appendLine("lote_vencido;$it;1;") }
        appendLine("divergencia;abertas;$divergenciasAbertas;")
        appendLine("divergencia;resolvidas;$divergenciasResolvidas;")
        produtosAbaixoDoMinimo.forEach { appendLine("abaixo_do_minimo;$it;1;") }
        produtosSemEstoqueParaVenda.forEach { appendLine("sem_estoque_para_venda;$it;1;") }
        appendLine("conferencia;dias_com_conferencia;$diasComConferencia;")
        appendLine("conferencia;dias_sem_conferencia;$diasSemConferencia;")
    }

    private fun formatar(data: LocalDate) =
        "%02d/%02d/%04d".format(data.dayOfMonth, data.monthValue, data.year)
}

object GeradorRelatorio {

    /** Semana fechada de sete dias terminando em [fim] (inclusive). */
    fun semanal(
        fim: LocalDate,
        produtos: List<Produto>,
        lotes: List<Lote>,
        movimentacoes: List<Movimentacao>,
        divergencias: List<Divergencia>,
        conferencias: List<Conferencia>,
    ): Relatorio = doPeriodo(
        "Relatorio semanal", fim.minusDays(6), fim, produtos, lotes, movimentacoes, divergencias, conferencias,
    )

    /** Mes civil da data informada, do dia 1 ao ultimo dia. */
    fun mensal(
        referencia: LocalDate,
        produtos: List<Produto>,
        lotes: List<Lote>,
        movimentacoes: List<Movimentacao>,
        divergencias: List<Divergencia>,
        conferencias: List<Conferencia>,
    ): Relatorio {
        val inicio = referencia.withDayOfMonth(1)
        val fim = referencia.withDayOfMonth(referencia.lengthOfMonth())
        return doPeriodo("Relatorio mensal", inicio, fim, produtos, lotes, movimentacoes, divergencias, conferencias)
    }

    fun doPeriodo(
        titulo: String,
        inicio: LocalDate,
        fim: LocalDate,
        produtos: List<Produto>,
        lotes: List<Lote>,
        movimentacoes: List<Movimentacao>,
        divergencias: List<Divergencia>,
        conferencias: List<Conferencia>,
    ): Relatorio {
        val porCodigo = produtos.associateBy { it.codigo }
        fun nome(codigo: String) = porCodigo[codigo]?.nome ?: codigo

        val doPeriodo = movimentacoes.filter {
            val data = it.dataHora.toLocalDate()
            !data.isBefore(inicio) && !data.isAfter(fim)
        }

        fun agrupar(lista: List<Movimentacao>, rotulo: (Movimentacao) -> String): List<LinhaResumo> =
            lista.groupBy(rotulo).map { (chave, itens) ->
                LinhaResumo(chave, itens.size, Quantidade.soma(itens.map { it.quantidade }))
            }.sortedByDescending { it.quantidade.milesimos }

        val perdas = doPeriodo.filter { it.tipo.ehPerda }
        val degustacao = doPeriodo.filter { it.tipo == TipoMovimentacao.DEGUSTACAO }

        val vencidosNoPeriodo = lotes
            .filter { !it.validade.isBefore(inicio) && !it.validade.isAfter(fim) }
            .sortedBy { it.validade }
            .map { "${nome(it.produtoCodigo)} - lote ${it.codigoLote} venceu em ${it.validade}" }

        val resumos = Estoque.resumos(produtos, lotes, fim)

        val diasDoPeriodo = (ChronoUnit.DAYS.between(inicio, fim) + 1).toInt()
        val diasComConferencia = conferencias
            .filter { it.status == StatusConferencia.FECHADA }
            .map { it.data }
            .filter { !it.isBefore(inicio) && !it.isAfter(fim) }
            .distinct()
            .size

        return Relatorio(
            titulo = titulo,
            inicio = inicio,
            fim = fim,
            movimentacoesPorTipo = agrupar(doPeriodo) { it.tipo.rotulo },
            perdasPorMotivo = agrupar(perdas) { it.tipo.rotulo },
            perdasPorProduto = agrupar(perdas) { nome(it.produtoCodigo) },
            degustacoes = LinhaResumo(
                "Degustacao",
                degustacao.size,
                Quantidade.soma(degustacao.map { it.quantidade }),
            ),
            totalEntradas = Quantidade.soma(
                doPeriodo.filter { it.tipo == TipoMovimentacao.ENTRADA }.map { it.quantidade },
            ),
            totalSaidas = Quantidade.soma(
                doPeriodo.filter { it.tipo == TipoMovimentacao.SAIDA }.map { it.quantidade },
            ),
            totalPerdas = Quantidade.soma(perdas.map { it.quantidade }),
            lotesVencidosNoPeriodo = vencidosNoPeriodo,
            divergenciasAbertas = divergencias.count { it.estaAberta },
            divergenciasResolvidas = divergencias.count {
                !it.estaAberta && it.dataResolucao != null &&
                    !it.dataResolucao.isBefore(inicio) && !it.dataResolucao.isAfter(fim)
            },
            produtosAbaixoDoMinimo = resumos.filter { it.abaixoDoMinimo }.map {
                "${it.produto.nome}: disponivel ${it.disponivelParaVenda.formatar(it.produto.unidade)}, " +
                    "minimo ${it.produto.estoqueMinimo.formatar(it.produto.unidade)}"
            },
            produtosSemEstoqueParaVenda = resumos.filter { it.temEstoqueMasNaoPodeVender }.map {
                "${it.produto.nome}: ${it.fisico.formatar(it.produto.unidade)} no fisico, nada vendavel"
            },
            diasDoPeriodo = diasDoPeriodo,
            diasComConferencia = diasComConferencia,
        )
    }
}

internal fun descreverValidade(lote: Lote, hoje: LocalDate): String =
    "${lote.codigoLote} - ${ClassificacaoValidade.descreverPrazo(lote.validade, hoje)}"
