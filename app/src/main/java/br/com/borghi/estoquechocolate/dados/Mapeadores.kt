package br.com.borghi.estoquechocolate.dados

import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.Configuracao
import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.ItemConferencia
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.PosicaoEstoque
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.StatusConferencia
import br.com.borghi.estoquechocolate.core.modelo.StatusDivergencia
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import java.time.LocalDate
import java.time.LocalDateTime

private fun local(nome: String, tipo: String) = Localizacao(nome, TipoLocal.valueOf(tipo))

fun ProdutoEntity.paraModelo() = Produto(
    codigo = codigo,
    nome = nome,
    categoria = categoria,
    unidade = UnidadeMedida.valueOf(unidade),
    estoqueMinimo = Quantidade(estoqueMinimo),
    estoqueMaximo = Quantidade(estoqueMaximo),
    localPadrao = local(localPadraoNome, localPadraoTipo),
    observacoes = observacoes,
    codigoBarras = codigoBarras,
    foto = foto,
)

fun Produto.paraEntidade() = ProdutoEntity(
    codigo = codigo,
    nome = nome,
    categoria = categoria,
    unidade = unidade.name,
    estoqueMinimo = estoqueMinimo.milesimos,
    estoqueMaximo = estoqueMaximo.milesimos,
    localPadraoNome = localPadrao.nome,
    localPadraoTipo = localPadrao.tipo.name,
    observacoes = observacoes,
    codigoBarras = codigoBarras,
    foto = foto,
)

fun LoteComPosicoes.paraModelo() = Lote(
    id = lote.id,
    produtoCodigo = lote.produtoCodigo,
    codigoLote = lote.codigoLote,
    validade = LocalDate.parse(lote.validade),
    quantidadeRecebida = Quantidade(lote.quantidadeRecebida),
    posicoes = posicoes.map { PosicaoEstoque(local(it.localNome, it.localTipo), Quantidade(it.quantidade)) },
    dataUltimaConferencia = lote.dataUltimaConferencia?.let { LocalDate.parse(it) },
    observacoes = lote.observacoes,
    segregado = lote.segregado,
    motivoSegregacao = lote.motivoSegregacao?.let { MotivoSegregacao.valueOf(it) },
    fotoEtiqueta = lote.fotoEtiqueta,
)

fun Lote.paraEntidade() = LoteEntity(
    id = id,
    produtoCodigo = produtoCodigo,
    codigoLote = codigoLote,
    validade = validade.toString(),
    quantidadeRecebida = quantidadeRecebida.milesimos,
    dataUltimaConferencia = dataUltimaConferencia?.toString(),
    observacoes = observacoes,
    segregado = segregado,
    motivoSegregacao = motivoSegregacao?.name,
    fotoEtiqueta = fotoEtiqueta,
)

fun Lote.posicoesParaEntidade() = posicoes.map {
    PosicaoEntity(
        loteId = id,
        localNome = it.localizacao.nome,
        localTipo = it.localizacao.tipo.name,
        quantidade = it.quantidade.milesimos,
    )
}

fun MovimentacaoEntity.paraModelo() = Movimentacao(
    id = id,
    dataHora = LocalDateTime.parse(dataHora),
    produtoCodigo = produtoCodigo,
    loteId = loteId,
    codigoLote = codigoLote,
    tipo = TipoMovimentacao.valueOf(tipo),
    quantidade = Quantidade(quantidade),
    localOrigem = if (origemNome != null && origemTipo != null) local(origemNome, origemTipo) else null,
    localDestino = if (destinoNome != null && destinoTipo != null) local(destinoNome, destinoTipo) else null,
    observacao = observacao,
    divergenciaId = divergenciaId,
)

fun Movimentacao.paraEntidade() = MovimentacaoEntity(
    id = id,
    dataHora = dataHora.toString(),
    produtoCodigo = produtoCodigo,
    loteId = loteId,
    codigoLote = codigoLote,
    tipo = tipo.name,
    quantidade = quantidade.milesimos,
    origemNome = localOrigem?.nome,
    origemTipo = localOrigem?.tipo?.name,
    destinoNome = localDestino?.nome,
    destinoTipo = localDestino?.tipo?.name,
    observacao = observacao,
    divergenciaId = divergenciaId,
)

fun DivergenciaEntity.paraModelo() = Divergencia(
    id = id,
    data = LocalDate.parse(data),
    produtoCodigo = produtoCodigo,
    loteId = loteId,
    codigoLote = codigoLote,
    localizacao = local(localNome, localTipo),
    quantidadeEsperada = Quantidade(quantidadeEsperada),
    quantidadeContada = Quantidade(quantidadeContada),
    observacao = observacao,
    status = StatusDivergencia.valueOf(status),
    dataResolucao = dataResolucao?.let { LocalDate.parse(it) },
    observacaoResolucao = observacaoResolucao,
)

fun Divergencia.paraEntidade() = DivergenciaEntity(
    id = id,
    data = data.toString(),
    produtoCodigo = produtoCodigo,
    loteId = loteId,
    codigoLote = codigoLote,
    localNome = localizacao.nome,
    localTipo = localizacao.tipo.name,
    quantidadeEsperada = quantidadeEsperada.milesimos,
    quantidadeContada = quantidadeContada.milesimos,
    observacao = observacao,
    status = status.name,
    dataResolucao = dataResolucao?.toString(),
    observacaoResolucao = observacaoResolucao,
)

fun ConferenciaComItens.paraModelo() = Conferencia(
    id = conferencia.id,
    data = LocalDate.parse(conferencia.data),
    itens = itens.map {
        ItemConferencia(
            loteId = it.loteId,
            codigoLote = it.codigoLote,
            produtoCodigo = it.produtoCodigo,
            localizacao = local(it.localNome, it.localTipo),
            quantidadeEsperada = Quantidade(it.quantidadeEsperada),
            quantidadeContada = Quantidade(it.quantidadeContada),
            divergenciaId = it.divergenciaId,
        )
    },
    status = StatusConferencia.valueOf(conferencia.status),
    observacao = conferencia.observacao,
)

fun Conferencia.paraEntidade() = ConferenciaEntity(
    id = id,
    data = data.toString(),
    status = status.name,
    observacao = observacao,
)

fun Conferencia.itensParaEntidade() = itens.map {
    ItemConferenciaEntity(
        conferenciaId = id,
        loteId = it.loteId,
        codigoLote = it.codigoLote,
        produtoCodigo = it.produtoCodigo,
        localNome = it.localizacao.nome,
        localTipo = it.localizacao.tipo.name,
        quantidadeEsperada = it.quantidadeEsperada.milesimos,
        quantidadeContada = it.quantidadeContada.milesimos,
        divergenciaId = it.divergenciaId,
    )
}

fun LocalEntity.paraModelo() = local(nome, tipo)

fun Localizacao.paraEntidade() = LocalEntity(nome = nome, tipo = tipo.name)

fun configuracaoDe(entidade: ConfiguracaoEntity?, locais: List<LocalEntity>) = Configuracao(
    diasSemConferencia = entidade?.diasSemConferencia ?: Configuracao().diasSemConferencia,
    locais = locais.map { it.paraModelo() }.ifEmpty { Configuracao.LOCAIS_PADRAO },
)
