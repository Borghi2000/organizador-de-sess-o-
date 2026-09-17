package br.com.borghi.estoquechocolate.dados

import br.com.borghi.estoquechocolate.core.backup.ArquivoBackup
import br.com.borghi.estoquechocolate.core.backup.Backup
import br.com.borghi.estoquechocolate.core.backup.DadosDeExemplo
import br.com.borghi.estoquechocolate.core.backup.ResultadoImportacao
import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.Configuracao
import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.regras.ResultadoOperacao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/** Tudo que o app precisa para montar qualquer tela, numa foto so. */
data class EstadoDoEstoque(
    val produtos: List<Produto> = emptyList(),
    val lotes: List<Lote> = emptyList(),
    val movimentacoes: List<Movimentacao> = emptyList(),
    val divergencias: List<Divergencia> = emptyList(),
    val conferencias: List<Conferencia> = emptyList(),
    val configuracao: Configuracao = Configuracao(),
    val carregado: Boolean = false,
) {
    fun produto(codigo: String): Produto? = produtos.firstOrNull { it.codigo == codigo }

    fun lote(id: String): Lote? = lotes.firstOrNull { it.id == id }

    fun lotesDoProduto(codigo: String): List<Lote> = lotes.filter { it.produtoCodigo == codigo }

    fun nomeDoProduto(codigo: String): String = produto(codigo)?.nome ?: codigo

    fun divergencia(id: String): Divergencia? = divergencias.firstOrNull { it.id == id }
}

private data class Complementos(
    val divergencias: List<Divergencia>,
    val conferencias: List<Conferencia>,
    val locais: List<LocalEntity>,
    val configuracao: ConfiguracaoEntity?,
)

class EstoqueRepositorio(private val dao: EstoqueDao) {

    private val principais: Flow<Triple<List<Produto>, List<Lote>, List<Movimentacao>>> = combine(
        dao.observarProdutos(),
        dao.observarLotes(),
        dao.observarMovimentacoes(),
    ) { produtos, lotes, movimentacoes ->
        Triple(
            produtos.map { it.paraModelo() },
            lotes.map { it.paraModelo() },
            movimentacoes.map { it.paraModelo() },
        )
    }

    private val complementos: Flow<Complementos> = combine(
        dao.observarDivergencias(),
        dao.observarConferencias(),
        dao.observarLocais(),
        dao.observarConfiguracao(),
    ) { divergencias, conferencias, locais, configuracao ->
        Complementos(
            divergencias.map { it.paraModelo() },
            conferencias.map { it.paraModelo() },
            locais,
            configuracao,
        )
    }

    val estado: Flow<EstadoDoEstoque> = combine(principais, complementos) { principal, complemento ->
        EstadoDoEstoque(
            produtos = principal.first,
            lotes = principal.second,
            movimentacoes = principal.third,
            divergencias = complemento.divergencias,
            conferencias = complemento.conferencias,
            configuracao = configuracaoDe(complemento.configuracao, complemento.locais),
            carregado = true,
        )
    }

    /** Na primeira abertura o app precisa de locais para poder registrar qualquer coisa. */
    suspend fun garantirLocaisPadrao() {
        dao.salvarLocais(Configuracao.LOCAIS_PADRAO.map { it.paraEntidade() })
    }

    suspend fun salvarProduto(produto: Produto) = dao.salvarProduto(produto.paraEntidade())

    suspend fun salvarLocal(localizacao: Localizacao) = dao.salvarLocais(listOf(localizacao.paraEntidade()))

    suspend fun salvarConfiguracao(configuracao: Configuracao) {
        dao.salvarConfiguracao(ConfiguracaoEntity(diasSemConferencia = configuracao.diasSemConferencia))
    }

    /** Grava uma operacao inteira numa transacao so. */
    suspend fun aplicar(resultado: ResultadoOperacao) {
        dao.aplicar(
            lotes = resultado.lotesAtualizados.map { it.paraEntidade() },
            posicoesPorLote = resultado.lotesAtualizados.associate { it.id to it.posicoesParaEntidade() },
            movimentacoes = resultado.movimentacoes.map { it.paraEntidade() },
            divergencias = resultado.divergenciasAtualizadas.map { it.paraEntidade() },
        )
    }

    suspend fun salvarDivergencias(divergencias: List<Divergencia>) {
        if (divergencias.isNotEmpty()) dao.salvarDivergencias(divergencias.map { it.paraEntidade() })
    }

    suspend fun salvarLotes(lotes: List<Lote>) {
        dao.aplicar(
            lotes = lotes.map { it.paraEntidade() },
            posicoesPorLote = lotes.associate { it.id to it.posicoesParaEntidade() },
        )
    }

    suspend fun salvarConferencia(conferencia: Conferencia) {
        dao.salvarConferencia(conferencia.paraEntidade())
        dao.apagarItensDaConferencia(conferencia.id)
        dao.salvarItensConferencia(conferencia.itensParaEntidade())
    }

    /**
     * Volta o estoque ao estado anterior a uma operacao: repoe os lotes como estavam e apaga as
     * movimentacoes e divergencias que aquela operacao criou.
     *
     * So e usada pelo "desfazer" das acoes repetitivas, dentro da janela de segundos em que ainda
     * da para dizer que foi engano.
     */
    suspend fun desfazer(
        lotesAnteriores: List<Lote>,
        movimentacoes: List<String>,
        divergencias: List<String>,
        conferenciaAnterior: Conferencia?,
    ) {
        if (lotesAnteriores.isNotEmpty()) salvarLotes(lotesAnteriores)
        if (movimentacoes.isNotEmpty()) dao.apagarMovimentacoesPorId(movimentacoes)
        if (divergencias.isNotEmpty()) dao.apagarDivergenciasPorId(divergencias)
        conferenciaAnterior?.let { salvarConferencia(it) }
    }

    suspend fun estaVazio(): Boolean = dao.contarProdutos() == 0

    fun montarBackup(estado: EstadoDoEstoque, agora: LocalDateTime) = ArquivoBackup(
        geradoEm = agora,
        produtos = estado.produtos,
        lotes = estado.lotes,
        movimentacoes = estado.movimentacoes,
        divergencias = estado.divergencias,
        conferencias = estado.conferencias,
        configuracao = estado.configuracao,
    )

    fun exportar(estado: EstadoDoEstoque, agora: LocalDateTime): String =
        Backup.exportar(montarBackup(estado, agora))

    /** Importar substitui tudo. Ou o arquivo inteiro entra, ou nada muda. */
    suspend fun importar(texto: String): ResultadoImportacao {
        val resultado = Backup.importar(texto)
        if (resultado is ResultadoImportacao.Sucesso) gravarArquivo(resultado.arquivo)
        return resultado
    }

    suspend fun carregarDadosDeExemplo(hoje: LocalDate) = gravarArquivo(DadosDeExemplo.gerar(hoje))

    suspend fun limparTudo() = gravarArquivo(
        ArquivoBackup(geradoEm = LocalDateTime.now(), configuracao = Configuracao()),
    )

    private suspend fun gravarArquivo(arquivo: ArquivoBackup) {
        dao.substituirTudo(
            produtos = arquivo.produtos.map { it.paraEntidade() },
            lotes = arquivo.lotes.map { it.paraEntidade() },
            posicoes = arquivo.lotes.flatMap { it.posicoesParaEntidade() },
            movimentacoes = arquivo.movimentacoes.map { it.paraEntidade() },
            divergencias = arquivo.divergencias.map { it.paraEntidade() },
            conferencias = arquivo.conferencias.map { it.paraEntidade() },
            itensConferencia = arquivo.conferencias.flatMap { it.itensParaEntidade() },
            locais = arquivo.configuracao.locais.map { it.paraEntidade() },
            configuracao = ConfiguracaoEntity(diasSemConferencia = arquivo.configuracao.diasSemConferencia),
        )
    }

    /** Conveniencia para telas que so precisam saber se ha algum produto cadastrado. */
    val temProdutos: Flow<Boolean> = dao.observarProdutos().map { it.isNotEmpty() }
}
