package br.com.borghi.estoquechocolate.core.regras

import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.ItemConferencia
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.StatusConferencia
import java.time.LocalDate

object Contagens {

    data class ResultadoContagem(
        val item: ItemConferencia,
        /** Null quando a contagem bateu com o esperado. */
        val divergencia: Divergencia?,
    ) {
        val bateu: Boolean get() = divergencia == null
    }

    /**
     * Registra o que foi contado.
     *
     * Nao mexe na quantidade do lote de proposito: contagem diferente do esperado vira divergencia
     * aberta, e so um ajuste de inventario confirmado altera o estoque.
     */
    fun registrar(
        lote: Lote,
        localizacao: Localizacao,
        quantidadeContada: Quantidade,
        hoje: LocalDate,
        observacao: String = "",
        gerarId: () -> String,
    ): ResultadoContagem {
        val esperada = lote.quantidadeEm(localizacao)
        val item = ItemConferencia(
            loteId = lote.id,
            codigoLote = lote.codigoLote,
            produtoCodigo = lote.produtoCodigo,
            localizacao = localizacao,
            quantidadeEsperada = esperada,
            quantidadeContada = quantidadeContada,
        )
        if (esperada == quantidadeContada) return ResultadoContagem(item, null)

        val divergencia = Divergencia(
            id = gerarId(),
            data = hoje,
            produtoCodigo = lote.produtoCodigo,
            loteId = lote.id,
            codigoLote = lote.codigoLote,
            localizacao = localizacao,
            quantidadeEsperada = esperada,
            quantidadeContada = quantidadeContada,
            observacao = observacao,
        )
        return ResultadoContagem(item.copy(divergenciaId = divergencia.id), divergencia)
    }

    data class ResultadoFechamento(
        val conferencia: Conferencia,
        /** Lotes que receberam a data de hoje como ultima conferencia. */
        val lotesAtualizados: List<Lote>,
        /** Lotes que continuam sem conferencia recente porque nao foram contados hoje. */
        val lotesNaoConferidos: List<Lote>,
        val divergenciasAbertas: Int,
    ) {
        val conferiuTudo: Boolean get() = lotesNaoConferidos.isEmpty()
    }

    /**
     * Fecha a conferencia do dia: carimba a data nos lotes contados e mostra, sem maquiar, o que
     * ficou de fora.
     */
    fun fechar(
        conferencia: Conferencia,
        lotes: List<Lote>,
        divergencias: List<Divergencia>,
        hoje: LocalDate,
        observacao: String = "",
    ): ResultadoFechamento {
        val contados = conferencia.lotesConferidos
        val atualizados = lotes.filter { it.id in contados }.map { it.copy(dataUltimaConferencia = hoje) }
        val naoConferidos = Pvps.ordenar(
            lotes.filter { it.id !in contados && it.quantidadeAtual.ehPositiva },
        )
        val abertas = divergencias.count { it.estaAberta }
        return ResultadoFechamento(
            conferencia = conferencia.copy(status = StatusConferencia.FECHADA, observacao = observacao),
            lotesAtualizados = atualizados,
            lotesNaoConferidos = naoConferidos,
            divergenciasAbertas = abertas,
        )
    }

    /** Lotes que o app deve sugerir para a conferencia de hoje, do mais atrasado para o menos. */
    fun sugestaoDoDia(lotes: List<Lote>, hoje: LocalDate, diasSemConferencia: Int): List<Lote> =
        lotes
            .filter { it.quantidadeAtual.ehPositiva && it.semConferenciaDesde(hoje, diasSemConferencia) }
            .sortedWith(compareBy({ it.dataUltimaConferencia ?: LocalDate.MIN }, { it.validade }))
}
