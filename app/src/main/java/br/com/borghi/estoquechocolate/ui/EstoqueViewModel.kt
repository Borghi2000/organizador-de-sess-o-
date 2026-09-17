package br.com.borghi.estoquechocolate.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.borghi.estoquechocolate.core.backup.ResultadoImportacao
import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.Configuracao
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.StatusConferencia
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import br.com.borghi.estoquechocolate.core.regras.Contagens
import br.com.borghi.estoquechocolate.core.regras.Operacoes
import br.com.borghi.estoquechocolate.core.regras.RascunhoContagem
import br.com.borghi.estoquechocolate.core.regras.RascunhoEntrada
import br.com.borghi.estoquechocolate.core.regras.RascunhoMovimentacao
import br.com.borghi.estoquechocolate.core.regras.RascunhoProduto
import br.com.borghi.estoquechocolate.core.regras.ResultadoEntrada
import br.com.borghi.estoquechocolate.core.regras.ResultadoOperacao
import br.com.borghi.estoquechocolate.core.relatorio.GeradorRelatorio
import br.com.borghi.estoquechocolate.core.relatorio.Relatorio
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque
import br.com.borghi.estoquechocolate.dados.EstoqueRepositorio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

sealed interface ResultadoAcao {
    data class Sucesso(val mensagem: String, val avisos: List<String> = emptyList()) : ResultadoAcao
    data class Bloqueado(val faltantes: List<String>) : ResultadoAcao
    data class Conflito(val mensagem: String) : ResultadoAcao
}

class EstoqueViewModel(private val repositorio: EstoqueRepositorio) : ViewModel() {

    val estado: StateFlow<EstadoDoEstoque> = repositorio.estado
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoDoEstoque())

    /** Ultima mensagem para mostrar na tela. A tela limpa depois de exibir. */
    var mensagem by mutableStateOf<String?>(null)
        private set

    /** Conferencia do dia mantida em memoria para contagens seguidas nao se perderem. */
    private var conferenciaAberta: Conferencia? = null

    /**
     * A ultima acao repetitiva, guardada para poder ser desfeita.
     *
     * Para acao rara e grave — entrada, perda, ajuste, limpar tudo — a confirmacao antes vale a
     * pena. Para acao repetitiva — contagem, reposicao — o dialogo vira obstaculo que se aprende a
     * apertar no automatico, e ai ele nao protege mais nada. Nessas, desfazer protege mais.
     */
    private var ultimaAcao: AcaoDesfazivel? = null

    /** Descricao do que da para desfazer agora, ou null quando nao ha nada. */
    var desfazerDisponivel by mutableStateOf<String?>(null)
        private set

    private data class AcaoDesfazivel(
        val descricao: String,
        val lotesAnteriores: List<br.com.borghi.estoquechocolate.core.modelo.Lote> = emptyList(),
        val movimentacoes: List<String> = emptyList(),
        val divergencias: List<String> = emptyList(),
        val conferenciaAnterior: Conferencia? = null,
        val conferenciaEmMemoria: Conferencia? = null,
    )

    private fun guardarParaDesfazer(acao: AcaoDesfazivel) {
        ultimaAcao = acao
        desfazerDisponivel = acao.descricao
    }

    fun esquecerDesfazer() {
        ultimaAcao = null
        desfazerDisponivel = null
    }

    fun desfazerUltima(aoTerminar: (ResultadoAcao) -> Unit) {
        val acao = ultimaAcao ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Nao ha nada para desfazer")))
            return
        }
        viewModelScope.launch {
            repositorio.desfazer(
                lotesAnteriores = acao.lotesAnteriores,
                movimentacoes = acao.movimentacoes,
                divergencias = acao.divergencias,
                conferenciaAnterior = acao.conferenciaAnterior,
            )
            conferenciaAberta = acao.conferenciaEmMemoria
            esquecerDesfazer()
            aoTerminar(ResultadoAcao.Sucesso("Desfeito: ${acao.descricao}."))
        }
    }

    init {
        viewModelScope.launch { repositorio.garantirLocaisPadrao() }
    }

    fun hoje(): LocalDate = LocalDate.now()

    fun agora(): LocalDateTime = LocalDateTime.now()

    fun limparMensagem() {
        mensagem = null
    }

    fun avisar(texto: String) {
        mensagem = texto
    }

    private fun novoId(): String = UUID.randomUUID().toString()

    private fun unidadeDe(produtoCodigo: String): UnidadeMedida =
        estado.value.produto(produtoCodigo)?.unidade ?: UnidadeMedida.UNIDADE

    // ----------------------------------------------------------------- produtos

    fun salvarProduto(
        rascunho: RascunhoProduto,
        editando: Boolean = false,
        aoTerminar: (ResultadoAcao) -> Unit,
    ) {
        val outros = estado.value.produtos.filterNot { it.codigo == rascunho.codigo.trim() }
        val validacao = rascunho.validar(
            codigosExistentes = if (editando) emptySet() else estado.value.produtos.map { it.codigo }.toSet(),
            codigosDeBarrasExistentes = outros
                .filter { it.codigoBarras.isNotBlank() }
                .associate { it.codigoBarras to it.nome },
        )
        if (!validacao.valido) {
            aoTerminar(ResultadoAcao.Bloqueado(validacao.faltantes.map { it.mensagem }))
            return
        }
        val produto = Produto(
            codigo = rascunho.codigo.trim(),
            nome = rascunho.nome.trim(),
            categoria = rascunho.categoria.trim(),
            unidade = rascunho.unidade!!,
            estoqueMinimo = Quantidade.deTexto(rascunho.estoqueMinimo)!!,
            estoqueMaximo = Quantidade.deTexto(rascunho.estoqueMaximo)!!,
            localPadrao = rascunho.localPadrao!!,
            observacoes = rascunho.observacoes.trim(),
            codigoBarras = rascunho.codigoBarras.trim(),
            foto = rascunho.foto,
        )
        viewModelScope.launch {
            repositorio.salvarProduto(produto)
            aoTerminar(ResultadoAcao.Sucesso("Produto ${produto.nome} salvo."))
        }
    }

    // ----------------------------------------------------------------- entrada

    fun registrarEntrada(
        rascunho: RascunhoEntrada,
        aoTerminar: (ResultadoAcao) -> Unit,
    ) {
        val atual = estado.value
        val validacao = rascunho.validar(hoje())
        if (!validacao.valido) {
            aoTerminar(ResultadoAcao.Bloqueado(validacao.faltantes.map { it.mensagem }))
            return
        }
        val produto = atual.produto(rascunho.produtoCodigo) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Produto nao encontrado. Cadastre-o primeiro.")))
            return
        }
        val quantidade = Quantidade.deTexto(rascunho.quantidade)!!
        val lotesDoProduto = atual.lotesDoProduto(produto.codigo)

        val resultado = Operacoes.entrada(
            produto = produto,
            lotesDoProduto = lotesDoProduto,
            codigoLote = rascunho.codigoLote.trim(),
            validade = rascunho.validade!!,
            quantidade = quantidade,
            localizacao = rascunho.localizacao!!,
            agora = agora(),
            observacao = rascunho.observacoes.trim(),
            fotoEtiqueta = rascunho.fotoEtiqueta,
            gerarId = ::novoId,
        )

        when (resultado) {
            is ResultadoEntrada.ConflitoDeValidade -> aoTerminar(
                ResultadoAcao.Conflito(
                    "Ja existe o lote ${rascunho.codigoLote} deste produto com validade " +
                        "${formatarData(resultado.loteExistente.validade)}, e voce informou " +
                        "${formatarData(resultado.validadeInformada)}. " +
                        "Confira a data: se forem lotes diferentes, use outro codigo de lote.",
                ),
            )
            is ResultadoEntrada.Sucesso -> viewModelScope.launch {
                repositorio.aplicar(resultado.resultado)
                aoTerminar(
                    ResultadoAcao.Sucesso(
                        "Entrada de ${quantidade.formatar(produto.unidade)} no lote " +
                            "${rascunho.codigoLote} registrada.",
                        resultado.resultado.avisos + validacao.avisos,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------------------- camera

    /**
     * Acha o produto por codigo de barras lido na embalagem, caindo para o codigo interno quando a
     * loja usa etiqueta propria.
     */
    fun produtoPorCodigoDeBarras(codigo: String): Produto? {
        val limpo = codigo.trim()
        if (limpo.isBlank()) return null
        val atual = estado.value
        return atual.produtos.firstOrNull { it.codigoBarras.equals(limpo, ignoreCase = true) }
            ?: atual.produtos.firstOrNull { it.codigo.equals(limpo, ignoreCase = true) }
    }

    /** Liga um codigo de barras lido a um produto ja cadastrado, sem mexer no resto do cadastro. */
    fun associarCodigoDeBarras(produtoCodigo: String, codigoBarras: String, aoTerminar: (ResultadoAcao) -> Unit) {
        val atual = estado.value
        val produto = atual.produto(produtoCodigo) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Produto nao encontrado")))
            return
        }
        val dono = atual.produtos.firstOrNull {
            it.codigo != produto.codigo && it.codigoBarras.equals(codigoBarras.trim(), ignoreCase = true)
        }
        if (dono != null) {
            aoTerminar(ResultadoAcao.Conflito("Este codigo de barras ja e do produto ${dono.nome}."))
            return
        }
        viewModelScope.launch {
            repositorio.salvarProduto(produto.copy(codigoBarras = codigoBarras.trim()))
            aoTerminar(ResultadoAcao.Sucesso("Codigo de barras ligado a ${produto.nome}."))
        }
    }

    /** Guarda o nome do arquivo da foto no cadastro do produto. */
    fun salvarFotoDoProduto(produtoCodigo: String, foto: String, aoTerminar: (ResultadoAcao) -> Unit) {
        val produto = estado.value.produto(produtoCodigo) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Produto nao encontrado")))
            return
        }
        viewModelScope.launch {
            repositorio.salvarProduto(produto.copy(foto = foto))
            aoTerminar(ResultadoAcao.Sucesso("Foto guardada."))
        }
    }

    /**
     * Grava uma fila de entradas revisadas, uma a uma, relendo o estoque entre elas: duas caixas
     * do mesmo lote na mesma rajada precisam somar, nao conflitar.
     */
    fun registrarEntradasEmFila(
        rascunhos: List<RascunhoEntrada>,
        aoTerminar: (gravadas: Int, problemas: List<String>) -> Unit,
    ) {
        viewModelScope.launch {
            var gravadas = 0
            val problemas = mutableListOf<String>()

            rascunhos.forEachIndexed { indice, rascunho ->
                val atual = repositorio.estado.first()
                val validacao = rascunho.validar(hoje())
                val produto = atual.produto(rascunho.produtoCodigo)
                val quantidade = Quantidade.deTexto(rascunho.quantidade)

                when {
                    !validacao.valido ->
                        problemas += "Item ${indice + 1}: ${validacao.resumoDoQueFalta}"

                    produto == null ->
                        problemas += "Item ${indice + 1}: produto nao encontrado"

                    else -> {
                        val resultado = Operacoes.entrada(
                            produto = produto,
                            lotesDoProduto = atual.lotesDoProduto(produto.codigo),
                            codigoLote = rascunho.codigoLote.trim(),
                            validade = rascunho.validade!!,
                            quantidade = quantidade!!,
                            localizacao = rascunho.localizacao!!,
                            agora = agora(),
                            observacao = rascunho.observacoes.trim(),
                            fotoEtiqueta = rascunho.fotoEtiqueta,
                            gerarId = ::novoId,
                        )
                        when (resultado) {
                            is ResultadoEntrada.ConflitoDeValidade ->
                                problemas += "Item ${indice + 1}: o lote ${rascunho.codigoLote} ja existe " +
                                    "com validade ${formatarData(resultado.loteExistente.validade)}"

                            is ResultadoEntrada.Sucesso -> {
                                repositorio.aplicar(resultado.resultado)
                                gravadas++
                            }
                        }
                    }
                }
            }

            aoTerminar(gravadas, problemas)
        }
    }

    // ----------------------------------------------------------------- movimentacoes

    fun registrarReposicao(
        produtoCodigo: String,
        quantidadeTexto: String,
        destino: Localizacao?,
        observacao: String,
        aoTerminar: (ResultadoAcao) -> Unit,
    ) {
        val faltantes = buildList {
            if (produtoCodigo.isBlank()) add("Escolha o produto")
            if (destino == null) add("Escolha para onde vai a reposicao")
            val quantidade = Quantidade.deTexto(quantidadeTexto)
            if (quantidade == null || !quantidade.ehPositiva) add("Informe a quantidade a repor")
        }
        if (faltantes.isNotEmpty()) {
            aoTerminar(ResultadoAcao.Bloqueado(faltantes))
            return
        }
        val quantidade = Quantidade.deTexto(quantidadeTexto)!!
        val resultado = Operacoes.reposicao(
            lotesDoProduto = estado.value.lotesDoProduto(produtoCodigo),
            quantidade = quantidade,
            destino = destino!!,
            hoje = hoje(),
            agora = agora(),
            observacao = observacao.trim(),
            gerarId = ::novoId,
        )
        aplicarEResponder(
            resultado,
            "Reposicao registrada.",
            desfazivel = "reposicao",
            aoTerminar = aoTerminar,
        )
    }

    fun registrarBaixa(
        rascunho: RascunhoMovimentacao,
        aoTerminar: (ResultadoAcao) -> Unit,
    ) {
        val validacao = rascunho.validar()
        if (!validacao.valido) {
            aoTerminar(ResultadoAcao.Bloqueado(validacao.faltantes.map { it.mensagem }))
            return
        }
        val lote = estado.value.lote(rascunho.loteId) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Lote nao encontrado")))
            return
        }
        // A validacao acima ja garante que estes campos existem, mas o tipo vem de outro modulo,
        // entao o compilador nao carrega a garantia adiante: guardamos em variaveis locais.
        val tipo = rascunho.tipo!!
        val resultado = Operacoes.baixaDeLote(
            lote = lote,
            tipo = tipo,
            quantidade = Quantidade.deTexto(rascunho.quantidade)!!,
            origem = rascunho.localOrigem!!,
            agora = agora(),
            observacao = rascunho.observacao.trim(),
            gerarId = ::novoId,
        )
        aplicarEResponder(resultado, "${tipo.rotulo} registrada.", aoTerminar = aoTerminar)
    }

    fun registrarSaida(
        produtoCodigo: String,
        quantidadeTexto: String,
        observacao: String,
        aoTerminar: (ResultadoAcao) -> Unit,
    ) {
        val quantidade = Quantidade.deTexto(quantidadeTexto)
        if (quantidade == null || !quantidade.ehPositiva) {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Informe a quantidade de saida")))
            return
        }
        val resultado = Operacoes.saida(
            lotesDoProduto = estado.value.lotesDoProduto(produtoCodigo),
            quantidade = quantidade,
            hoje = hoje(),
            agora = agora(),
            observacao = observacao.trim(),
            gerarId = ::novoId,
        )
        aplicarEResponder(resultado, "Saida registrada.", aoTerminar = aoTerminar)
    }

    fun registrarTransferencia(
        loteId: String,
        quantidadeTexto: String,
        origem: Localizacao?,
        destino: Localizacao?,
        observacao: String,
        aoTerminar: (ResultadoAcao) -> Unit,
    ) {
        val rascunho = RascunhoMovimentacao(
            tipo = TipoMovimentacao.TRANSFERENCIA,
            loteId = loteId,
            quantidade = quantidadeTexto,
            localOrigem = origem,
            localDestino = destino,
            observacao = observacao,
        )
        val validacao = rascunho.validar()
        if (!validacao.valido) {
            aoTerminar(ResultadoAcao.Bloqueado(validacao.faltantes.map { it.mensagem }))
            return
        }
        val lote = estado.value.lote(loteId) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Lote nao encontrado")))
            return
        }
        val resultado = Operacoes.transferencia(
            lote = lote,
            quantidade = Quantidade.deTexto(quantidadeTexto)!!,
            origem = origem!!,
            destino = destino!!,
            agora = agora(),
            observacao = observacao.trim(),
            gerarId = ::novoId,
        )
        aplicarEResponder(resultado, "Transferencia registrada.", aoTerminar = aoTerminar)
    }

    fun segregarLote(loteId: String, motivo: MotivoSegregacao, observacao: String, aoTerminar: (ResultadoAcao) -> Unit) {
        val lote = estado.value.lote(loteId) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Lote nao encontrado")))
            return
        }
        val segregacao = estado.value.configuracao.locais
            .firstOrNull { it.tipo == br.com.borghi.estoquechocolate.core.modelo.TipoLocal.SEGREGACAO }
            ?: Localizacao.SEGREGACAO_PADRAO
        val resultado = Operacoes.segregar(
            lote = lote,
            motivo = motivo,
            localSegregacao = segregacao,
            agora = agora(),
            observacao = observacao.trim(),
            gerarId = ::novoId,
        )
        aplicarEResponder(
            resultado,
            "Lote ${lote.codigoLote} segregado e retirado da venda.",
            aoTerminar = aoTerminar,
        )
    }

    private fun aplicarEResponder(
        resultado: ResultadoOperacao,
        mensagemSucesso: String,
        desfazivel: String? = null,
        aoTerminar: (ResultadoAcao) -> Unit,
    ) {
        if (resultado.movimentacoes.isEmpty()) {
            aoTerminar(
                ResultadoAcao.Bloqueado(
                    resultado.avisos.ifEmpty { listOf("Nao havia quantidade disponivel para esta operacao") },
                ),
            )
            return
        }
        val anteriores = resultado.lotesAtualizados.mapNotNull { estado.value.lote(it.id) }
        viewModelScope.launch {
            repositorio.aplicar(resultado)
            if (desfazivel != null && anteriores.size == resultado.lotesAtualizados.size) {
                guardarParaDesfazer(
                    AcaoDesfazivel(
                        descricao = desfazivel,
                        lotesAnteriores = anteriores,
                        movimentacoes = resultado.movimentacoes.map { it.id },
                        divergencias = resultado.divergenciasAtualizadas.map { it.id },
                    ),
                )
            } else {
                esquecerDesfazer()
            }
            aoTerminar(ResultadoAcao.Sucesso(mensagemSucesso, resultado.avisos))
        }
    }

    // ----------------------------------------------------------------- conferencia

    fun registrarContagem(rascunho: RascunhoContagem, aoTerminar: (ResultadoAcao) -> Unit) {
        val validacao = rascunho.validar()
        if (!validacao.valido) {
            aoTerminar(ResultadoAcao.Bloqueado(validacao.faltantes.map { it.mensagem }))
            return
        }
        val lote = estado.value.lote(rascunho.loteId) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Lote nao encontrado")))
            return
        }
        val contagem = Contagens.registrar(
            lote = lote,
            localizacao = rascunho.localizacao!!,
            quantidadeContada = Quantidade.deTexto(rascunho.quantidadeContada)!!,
            hoje = hoje(),
            observacao = rascunho.observacao.trim(),
            gerarId = ::novoId,
        )
        val conferencia = conferenciaDoDia()
        val itens = conferencia.itens
            .filterNot { it.loteId == contagem.item.loteId && it.localizacao == contagem.item.localizacao } +
            contagem.item
        val atualizada = conferencia.copy(itens = itens)
        val conferenciaDeAntes = conferenciaAberta
        conferenciaAberta = atualizada

        viewModelScope.launch {
            repositorio.salvarConferencia(atualizada)
            contagem.divergencia?.let { repositorio.salvarDivergencias(listOf(it)) }
            guardarParaDesfazer(
                AcaoDesfazivel(
                    descricao = "contagem do lote ${lote.codigoLote}",
                    divergencias = listOfNotNull(contagem.divergencia?.id),
                    conferenciaAnterior = conferenciaDeAntes ?: conferencia,
                    conferenciaEmMemoria = conferenciaDeAntes,
                ),
            )
            val unidade = unidadeDe(lote.produtoCodigo)
            aoTerminar(
                if (contagem.bateu) {
                    ResultadoAcao.Sucesso("Contagem do lote ${lote.codigoLote} bateu com o esperado.")
                } else {
                    val divergencia = contagem.divergencia!!
                    ResultadoAcao.Sucesso(
                        "Divergencia aberta no lote ${lote.codigoLote}.",
                        listOf(
                            divergencia.descricao(unidade),
                            "O estoque NAO foi alterado. Recontagem ou ajuste de inventario resolvem.",
                        ),
                    )
                },
            )
        }
    }

    fun conferenciaDoDia(): Conferencia {
        val hoje = hoje()
        val emMemoria = conferenciaAberta
        if (emMemoria != null && emMemoria.data == hoje && emMemoria.status == StatusConferencia.ABERTA) {
            return emMemoria
        }
        val salva = estado.value.conferencias
            .firstOrNull { it.data == hoje && it.status == StatusConferencia.ABERTA }
        val conferencia = salva ?: Conferencia(id = novoId(), data = hoje)
        conferenciaAberta = conferencia
        return conferencia
    }

    fun fecharConferenciaDoDia(observacao: String, aoTerminar: (ResultadoAcao) -> Unit) {
        val atual = estado.value
        val conferencia = conferenciaDoDia()
        if (conferencia.itens.isEmpty()) {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Nenhuma contagem registrada hoje para fechar")))
            return
        }
        val resultado = Contagens.fechar(
            conferencia = conferencia,
            lotes = atual.lotes,
            divergencias = atual.divergencias,
            hoje = hoje(),
            observacao = observacao.trim(),
        )
        viewModelScope.launch {
            repositorio.salvarConferencia(resultado.conferencia)
            repositorio.salvarLotes(resultado.lotesAtualizados)
            conferenciaAberta = null
            val avisos = buildList {
                if (!resultado.conferiuTudo) {
                    add("${resultado.lotesNaoConferidos.size} lote(s) com estoque ficaram sem contagem hoje.")
                }
                if (resultado.divergenciasAbertas > 0) {
                    add("${resultado.divergenciasAbertas} divergencia(s) continuam abertas.")
                }
            }
            aoTerminar(
                ResultadoAcao.Sucesso(
                    "Conferencia do dia fechada com ${resultado.conferencia.totalConferido} item(ns).",
                    avisos,
                ),
            )
        }
    }

    fun resolverDivergencia(divergenciaId: String, observacao: String, aoTerminar: (ResultadoAcao) -> Unit) {
        if (observacao.isBlank()) {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Descreva o motivo do ajuste de inventario")))
            return
        }
        val atual = estado.value
        val divergencia = atual.divergencia(divergenciaId) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Divergencia nao encontrada")))
            return
        }
        val lote = atual.lote(divergencia.loteId) ?: run {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Lote da divergencia nao encontrado")))
            return
        }
        val resultado = Operacoes.ajusteDeInventario(
            lote = lote,
            divergencia = divergencia,
            hoje = hoje(),
            agora = agora(),
            observacao = observacao.trim(),
            gerarId = ::novoId,
        )
        viewModelScope.launch {
            repositorio.aplicar(resultado)
            aoTerminar(ResultadoAcao.Sucesso("Estoque ajustado e divergencia resolvida."))
        }
    }

    // ----------------------------------------------------------------- relatorios e dados

    fun relatorioSemanal(): Relatorio = estado.value.let {
        GeradorRelatorio.semanal(hoje(), it.produtos, it.lotes, it.movimentacoes, it.divergencias, it.conferencias)
    }

    fun relatorioMensal(): Relatorio = estado.value.let {
        GeradorRelatorio.mensal(hoje(), it.produtos, it.lotes, it.movimentacoes, it.divergencias, it.conferencias)
    }

    fun textoDoBackup(): String = repositorio.exportar(estado.value, agora())

    fun importarBackup(texto: String, aoTerminar: (ResultadoAcao) -> Unit) {
        viewModelScope.launch {
            when (val resultado = repositorio.importar(texto)) {
                is ResultadoImportacao.Sucesso -> aoTerminar(
                    ResultadoAcao.Sucesso("Backup restaurado: ${resultado.arquivo.resumo}"),
                )
                is ResultadoImportacao.Falha -> aoTerminar(ResultadoAcao.Bloqueado(listOf(resultado.mensagem)))
            }
        }
    }

    fun carregarDadosDeExemplo(aoTerminar: (ResultadoAcao) -> Unit) {
        viewModelScope.launch {
            repositorio.carregarDadosDeExemplo(hoje())
            conferenciaAberta = null
            aoTerminar(ResultadoAcao.Sucesso("Dados de exemplo carregados. Use 'Limpar tudo' antes de cadastrar os reais."))
        }
    }

    fun limparTudo(aoTerminar: (ResultadoAcao) -> Unit) {
        viewModelScope.launch {
            repositorio.limparTudo()
            repositorio.garantirLocaisPadrao()
            conferenciaAberta = null
            aoTerminar(ResultadoAcao.Sucesso("Todos os dados foram apagados."))
        }
    }

    fun salvarDiasSemConferencia(dias: Int, aoTerminar: (ResultadoAcao) -> Unit) {
        if (dias < 1) {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("O prazo precisa ser de pelo menos 1 dia")))
            return
        }
        viewModelScope.launch {
            repositorio.salvarConfiguracao(estado.value.configuracao.copy(diasSemConferencia = dias))
            aoTerminar(ResultadoAcao.Sucesso("Prazo de conferencia salvo: $dias dia(s)."))
        }
    }

    fun salvarLocal(nome: String, tipo: br.com.borghi.estoquechocolate.core.modelo.TipoLocal, aoTerminar: (ResultadoAcao) -> Unit) {
        if (nome.isBlank()) {
            aoTerminar(ResultadoAcao.Bloqueado(listOf("Informe o nome do local")))
            return
        }
        viewModelScope.launch {
            repositorio.salvarLocal(Localizacao(nome.trim(), tipo))
            aoTerminar(ResultadoAcao.Sucesso("Local ${nome.trim()} salvo."))
        }
    }

    class Fabrica(private val repositorio: EstoqueRepositorio) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EstoqueViewModel(repositorio) as T
    }
}

/** Configuracao padrao usada enquanto o banco ainda nao respondeu. */
val CONFIGURACAO_INICIAL = Configuracao()
