package br.com.borghi.estoquechocolate.core.regras

import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.StatusDivergencia
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * O que cada operacao produziu: lotes ja com a nova quantidade e as movimentacoes a gravar.
 *
 * O core decide; o app apenas persiste. Assim toda regra de estoque fica testavel fora do Android.
 */
data class ResultadoOperacao(
    val lotesAtualizados: List<Lote> = emptyList(),
    val movimentacoes: List<Movimentacao> = emptyList(),
    val divergenciasAtualizadas: List<Divergencia> = emptyList(),
    val avisos: List<String> = emptyList(),
    val naoAtendido: Quantidade = Quantidade.ZERO,
) {
    val atendeuTudo: Boolean get() = naoAtendido.ehZero
}

sealed interface ResultadoEntrada {
    data class Sucesso(val resultado: ResultadoOperacao, val loteNovo: Boolean) : ResultadoEntrada

    /**
     * Mesmo codigo de lote com validade diferente da que ja esta gravada. Pode ser erro de
     * digitacao ou lote realmente novo; o app pergunta em vez de escolher sozinho.
     */
    data class ConflitoDeValidade(
        val loteExistente: Lote,
        val validadeInformada: LocalDate,
    ) : ResultadoEntrada
}

object Operacoes {

    /**
     * Entrada de mercadoria. Se ja existe lote com o mesmo codigo e a mesma validade, soma no
     * proprio lote; se a validade nao bate, devolve conflito para a tela perguntar.
     */
    fun entrada(
        produto: Produto,
        lotesDoProduto: List<Lote>,
        codigoLote: String,
        validade: LocalDate,
        quantidade: Quantidade,
        localizacao: Localizacao,
        agora: LocalDateTime,
        observacao: String = "",
        fotoEtiqueta: String = "",
        gerarId: () -> String,
    ): ResultadoEntrada {
        val existente = lotesDoProduto.firstOrNull {
            it.produtoCodigo == produto.codigo && it.codigoLote.equals(codigoLote, ignoreCase = true)
        }
        if (existente != null && existente.validade != validade) {
            return ResultadoEntrada.ConflitoDeValidade(existente, validade)
        }
        val loteNovo = existente == null
        val base = existente ?: Lote(
            id = gerarId(),
            produtoCodigo = produto.codigo,
            codigoLote = codigoLote,
            validade = validade,
            quantidadeRecebida = Quantidade.ZERO,
            observacoes = observacao,
        )
        val atualizado = base
            .copy(
                quantidadeRecebida = base.quantidadeRecebida + quantidade,
                // Nao sobrescreve a foto de uma entrada anterior do mesmo lote.
                fotoEtiqueta = base.fotoEtiqueta.ifBlank { fotoEtiqueta },
            )
            .comVariacao(localizacao, quantidade)
        val movimentacao = Movimentacao(
            id = gerarId(),
            dataHora = agora,
            produtoCodigo = produto.codigo,
            loteId = atualizado.id,
            codigoLote = atualizado.codigoLote,
            tipo = TipoMovimentacao.ENTRADA,
            quantidade = quantidade,
            localDestino = localizacao,
            observacao = observacao,
        )
        val avisos = buildList {
            if (!loteNovo) add("Quantidade somada ao lote ${atualizado.codigoLote}, que ja existia.")
        }
        return ResultadoEntrada.Sucesso(
            ResultadoOperacao(listOf(atualizado), listOf(movimentacao), avisos = avisos),
            loteNovo,
        )
    }

    /**
     * Repoe a gondola tirando do deposito, sempre pelo lote que vence primeiro.
     * Quando um lote nao cobre o pedido, gera uma movimentacao por lote em vez de somar tudo.
     */
    fun reposicao(
        lotesDoProduto: List<Lote>,
        quantidade: Quantidade,
        destino: Localizacao,
        hoje: LocalDate,
        agora: LocalDateTime,
        observacao: String = "",
        gerarId: () -> String,
    ): ResultadoOperacao = moverEntreLocais(
        lotesDoProduto = lotesDoProduto,
        quantidade = quantidade,
        tipoOrigem = TipoLocal.DEPOSITO,
        destino = destino,
        tipo = TipoMovimentacao.REPOSICAO,
        hoje = hoje,
        agora = agora,
        observacao = observacao,
        gerarId = gerarId,
    )

    /** Saida de venda ou consumo, tirando da exposicao pelo PVPS. */
    fun saida(
        lotesDoProduto: List<Lote>,
        quantidade: Quantidade,
        hoje: LocalDate,
        agora: LocalDateTime,
        tipo: TipoMovimentacao = TipoMovimentacao.SAIDA,
        tipoOrigem: TipoLocal = TipoLocal.EXPOSICAO,
        observacao: String = "",
        gerarId: () -> String,
    ): ResultadoOperacao {
        val plano = Pvps.planejarConsumo(lotesDoProduto, quantidade, hoje, tipoOrigem)
        val lotes = mutableListOf<Lote>()
        val movimentacoes = mutableListOf<Movimentacao>()
        for (item in plano.itens) {
            val origem = localComMaisQuantidade(item.lote, tipoOrigem) ?: continue
            lotes += item.lote.comVariacao(origem, -item.quantidade)
            movimentacoes += Movimentacao(
                id = gerarId(),
                dataHora = agora,
                produtoCodigo = item.lote.produtoCodigo,
                loteId = item.lote.id,
                codigoLote = item.lote.codigoLote,
                tipo = tipo,
                quantidade = item.quantidade,
                localOrigem = origem,
                observacao = observacao,
            )
        }
        val avisos = buildList {
            if (plano.usaMaisDeUmLote) {
                add("Atendido com ${plano.itens.size} lotes, do que vence primeiro para o que vence depois.")
            }
            if (!plano.atendeTotalmente) {
                add("Faltaram ${plano.faltante.formatar()} sem lote disponivel para atender.")
            }
        }
        return ResultadoOperacao(lotes, movimentacoes, avisos = avisos, naoAtendido = plano.faltante)
    }

    /**
     * Perda, avaria ou degustacao de um lote especifico. A observacao e obrigatoria nos tipos que
     * exigem justificativa; quem valida isso e [Validacao], antes de chegar aqui.
     */
    fun baixaDeLote(
        lote: Lote,
        tipo: TipoMovimentacao,
        quantidade: Quantidade,
        origem: Localizacao,
        agora: LocalDateTime,
        observacao: String,
        gerarId: () -> String,
    ): ResultadoOperacao {
        val disponivelNoLocal = lote.quantidadeEm(origem)
        val baixar = quantidade.menorEntre(disponivelNoLocal)
        val faltante = (quantidade - baixar).naoNegativa()
        if (!baixar.ehPositiva) {
            return ResultadoOperacao(
                avisos = listOf("Nao ha quantidade deste lote em ${origem.nome} para dar baixa."),
                naoAtendido = quantidade,
            )
        }
        val atualizado = lote.comVariacao(origem, -baixar)
        val movimentacao = Movimentacao(
            id = gerarId(),
            dataHora = agora,
            produtoCodigo = lote.produtoCodigo,
            loteId = lote.id,
            codigoLote = lote.codigoLote,
            tipo = tipo,
            quantidade = baixar,
            localOrigem = origem,
            observacao = observacao,
        )
        val avisos = buildList {
            if (faltante.ehPositiva) {
                add("Baixado apenas ${baixar.formatar()}: e o que existia deste lote em ${origem.nome}.")
            }
        }
        return ResultadoOperacao(listOf(atualizado), listOf(movimentacao), avisos = avisos, naoAtendido = faltante)
    }

    /** Transferencia entre locais do mesmo lote. O total da secao nao muda. */
    fun transferencia(
        lote: Lote,
        quantidade: Quantidade,
        origem: Localizacao,
        destino: Localizacao,
        agora: LocalDateTime,
        observacao: String = "",
        gerarId: () -> String,
    ): ResultadoOperacao {
        val disponivel = lote.quantidadeEm(origem)
        val mover = quantidade.menorEntre(disponivel)
        if (!mover.ehPositiva) {
            return ResultadoOperacao(
                avisos = listOf("Nao ha quantidade deste lote em ${origem.nome} para transferir."),
                naoAtendido = quantidade,
            )
        }
        val atualizado = lote.comVariacao(origem, -mover).comVariacao(destino, mover)
        val movimentacao = Movimentacao(
            id = gerarId(),
            dataHora = agora,
            produtoCodigo = lote.produtoCodigo,
            loteId = lote.id,
            codigoLote = lote.codigoLote,
            tipo = TipoMovimentacao.TRANSFERENCIA,
            quantidade = mover,
            localOrigem = origem,
            localDestino = destino,
            observacao = observacao,
        )
        return ResultadoOperacao(
            listOf(atualizado),
            listOf(movimentacao),
            naoAtendido = (quantidade - mover).naoNegativa(),
        )
    }

    /**
     * Segrega o lote inteiro: marca como nao vendavel e leva tudo para a area de segregacao,
     * gerando uma transferencia por local de origem.
     */
    fun segregar(
        lote: Lote,
        motivo: MotivoSegregacao,
        localSegregacao: Localizacao = Localizacao.SEGREGACAO_PADRAO,
        agora: LocalDateTime,
        observacao: String,
        gerarId: () -> String,
    ): ResultadoOperacao {
        val origens = lote.posicoes.filter { it.localizacao != localSegregacao && it.quantidade.ehPositiva }
        var atualizado = lote.copy(segregado = true, motivoSegregacao = motivo)
        val movimentacoes = mutableListOf<Movimentacao>()
        for (posicao in origens) {
            atualizado = atualizado
                .comVariacao(posicao.localizacao, -posicao.quantidade)
                .comVariacao(localSegregacao, posicao.quantidade)
            movimentacoes += Movimentacao(
                id = gerarId(),
                dataHora = agora,
                produtoCodigo = lote.produtoCodigo,
                loteId = lote.id,
                codigoLote = lote.codigoLote,
                tipo = TipoMovimentacao.TRANSFERENCIA,
                quantidade = posicao.quantidade,
                localOrigem = posicao.localizacao,
                localDestino = localSegregacao,
                observacao = "Segregacao por ${motivo.rotulo.lowercase()}. $observacao".trim(),
            )
        }
        return ResultadoOperacao(listOf(atualizado), movimentacoes)
    }

    /**
     * Resolve uma divergencia aceitando a contagem fisica. Este e o unico caminho pelo qual uma
     * contagem altera o estoque, e ele sempre grava movimentacao com data e justificativa.
     */
    fun ajusteDeInventario(
        lote: Lote,
        divergencia: Divergencia,
        hoje: LocalDate,
        agora: LocalDateTime,
        observacao: String,
        gerarId: () -> String,
    ): ResultadoOperacao {
        val diferenca = divergencia.quantidadeContada - lote.quantidadeEm(divergencia.localizacao)
        val atualizado = lote.comVariacao(divergencia.localizacao, diferenca)
        val movimentacao = Movimentacao(
            id = gerarId(),
            dataHora = agora,
            produtoCodigo = lote.produtoCodigo,
            loteId = lote.id,
            codigoLote = lote.codigoLote,
            tipo = TipoMovimentacao.AJUSTE_INVENTARIO,
            quantidade = diferenca.valorAbsoluto(),
            localOrigem = if (diferenca.ehNegativa) divergencia.localizacao else null,
            localDestino = if (diferenca.ehPositiva) divergencia.localizacao else null,
            observacao = observacao,
            divergenciaId = divergencia.id,
        )
        val resolvida = divergencia.copy(
            status = StatusDivergencia.RESOLVIDA,
            dataResolucao = hoje,
            observacaoResolucao = observacao,
        )
        return ResultadoOperacao(listOf(atualizado), listOf(movimentacao), listOf(resolvida))
    }

    private fun moverEntreLocais(
        lotesDoProduto: List<Lote>,
        quantidade: Quantidade,
        tipoOrigem: TipoLocal,
        destino: Localizacao,
        tipo: TipoMovimentacao,
        hoje: LocalDate,
        agora: LocalDateTime,
        observacao: String,
        gerarId: () -> String,
    ): ResultadoOperacao {
        val plano = Pvps.planejarConsumo(lotesDoProduto, quantidade, hoje, tipoOrigem)
        val lotes = mutableListOf<Lote>()
        val movimentacoes = mutableListOf<Movimentacao>()
        for (item in plano.itens) {
            val origem = localComMaisQuantidade(item.lote, tipoOrigem) ?: continue
            lotes += item.lote.comVariacao(origem, -item.quantidade).comVariacao(destino, item.quantidade)
            movimentacoes += Movimentacao(
                id = gerarId(),
                dataHora = agora,
                produtoCodigo = item.lote.produtoCodigo,
                loteId = item.lote.id,
                codigoLote = item.lote.codigoLote,
                tipo = tipo,
                quantidade = item.quantidade,
                localOrigem = origem,
                localDestino = destino,
                observacao = observacao,
            )
        }
        val avisos = buildList {
            if (plano.usaMaisDeUmLote) {
                add("Reposicao feita com ${plano.itens.size} lotes, do que vence primeiro para o que vence depois.")
            }
            if (!plano.atendeTotalmente) {
                add("Faltaram ${plano.faltante.formatar()} no ${tipoOrigem.rotulo.lowercase()}.")
            }
        }
        return ResultadoOperacao(lotes, movimentacoes, avisos = avisos, naoAtendido = plano.faltante)
    }

    private fun localComMaisQuantidade(lote: Lote, tipo: TipoLocal): Localizacao? =
        lote.posicoes
            .filter { it.localizacao.tipo == tipo && it.quantidade.ehPositiva }
            .maxByOrNull { it.quantidade.milesimos }
            ?.localizacao
}
