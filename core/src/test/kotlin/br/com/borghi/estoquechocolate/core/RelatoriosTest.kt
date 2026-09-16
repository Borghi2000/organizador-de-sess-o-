package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.StatusConferencia
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.relatorio.GeradorRelatorio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelatoriosTest {

    private val bombom = produto(codigo = "CH001", nome = "Bombom ao leite", minimo = 10)

    private fun movimentacao(
        id: String,
        diasAtras: Long,
        tipo: TipoMovimentacao,
        quantidade: Int,
    ) = Movimentacao(
        id = id,
        dataHora = AGORA.minusDays(diasAtras),
        produtoCodigo = "CH001",
        loteId = "L1",
        codigoLote = "A2501",
        tipo = tipo,
        quantidade = q(quantidade),
        observacao = "",
    )

    @Test
    fun `o relatorio semanal cobre sete dias e ignora o que esta fora`() {
        val dentro = movimentacao("M1", 6, TipoMovimentacao.ENTRADA, 50)
        val fora = movimentacao("M2", 7, TipoMovimentacao.ENTRADA, 999)

        val relatorio = GeradorRelatorio.semanal(HOJE, listOf(bombom), emptyList(), listOf(dentro, fora), emptyList(), emptyList())

        assertEquals(HOJE.minusDays(6), relatorio.inicio)
        assertEquals(HOJE, relatorio.fim)
        assertEquals(7, relatorio.diasDoPeriodo)
        assertEquals(q(50), relatorio.totalEntradas)
    }

    @Test
    fun `perda e degustacao aparecem separadas`() {
        val movimentacoes = listOf(
            movimentacao("M1", 1, TipoMovimentacao.PERDA_VENCIMENTO, 4),
            movimentacao("M2", 2, TipoMovimentacao.PERDA_AVARIA, 3),
            movimentacao("M3", 3, TipoMovimentacao.DEGUSTACAO, 10),
        )

        val relatorio = GeradorRelatorio.semanal(HOJE, listOf(bombom), emptyList(), movimentacoes, emptyList(), emptyList())

        assertEquals(q(7), relatorio.totalPerdas)
        assertEquals(q(10), relatorio.degustacoes.quantidade)
        assertEquals(2, relatorio.perdasPorMotivo.size)
        assertEquals(q(7), relatorio.perdasPorProduto.single().quantidade)
    }

    @Test
    fun `o relatorio mensal vai do dia 1 ao ultimo dia do mes`() {
        val relatorio = GeradorRelatorio.mensal(HOJE, listOf(bombom), emptyList(), emptyList(), emptyList(), emptyList())

        assertEquals(HOJE.withDayOfMonth(1), relatorio.inicio)
        assertEquals(30, relatorio.diasDoPeriodo)
    }

    @Test
    fun `conta os dias com conferencia fechada e os sem`() {
        val conferencias = listOf(
            Conferencia(id = "C1", data = HOJE, status = StatusConferencia.FECHADA),
            Conferencia(id = "C2", data = HOJE.minusDays(1), status = StatusConferencia.FECHADA),
            Conferencia(id = "C3", data = HOJE.minusDays(2), status = StatusConferencia.ABERTA),
        )

        val relatorio = GeradorRelatorio.semanal(HOJE, listOf(bombom), emptyList(), emptyList(), emptyList(), conferencias)

        assertEquals(2, relatorio.diasComConferencia)
        assertEquals(5, relatorio.diasSemConferencia)
    }

    @Test
    fun `lista os lotes que venceram dentro do periodo e quem esta abaixo do minimo`() {
        val venceu = lote("L1", validade = HOJE.minusDays(2), posicoes = listOf(GONDOLA to 5))
        val vaiVencer = lote("L2", validade = HOJE.plusDays(60), posicoes = listOf(GONDOLA to 2))

        val relatorio = GeradorRelatorio.semanal(
            HOJE, listOf(bombom), listOf(venceu, vaiVencer), emptyList(), emptyList(), emptyList(),
        )

        assertEquals(1, relatorio.lotesVencidosNoPeriodo.size)
        assertTrue(relatorio.lotesVencidosNoPeriodo.single().contains("A2501".take(0) + "L1"))
        assertEquals(1, relatorio.produtosAbaixoDoMinimo.size)
    }

    @Test
    fun `o texto do relatorio sai pronto para compartilhar`() {
        val relatorio = GeradorRelatorio.semanal(
            HOJE, listOf(bombom), emptyList(),
            listOf(movimentacao("M1", 1, TipoMovimentacao.PERDA_VENCIMENTO, 4)),
            emptyList(), emptyList(),
        )

        val texto = relatorio.paraTexto()
        assertTrue(texto.contains("RELATORIO SEMANAL"))
        assertTrue(texto.contains("Periodo: 10/09/2026 a 16/09/2026"))
        assertTrue(texto.contains("Total perdido: 4"))
        assertTrue(texto.contains("Degustacao (nao contada como perda): 0"))

        val csv = relatorio.paraCsv()
        assertTrue(csv.lineSequence().first().startsWith("secao;item"))
        assertTrue(csv.contains("perda_motivo;Perda por vencimento;1;4"))
    }
}
