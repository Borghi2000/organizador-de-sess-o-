package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.painel.MontadorPainel
import br.com.borghi.estoquechocolate.core.painel.TipoCartao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PainelTest {

    private val bombom = produto(codigo = "CH001", nome = "Bombom ao leite", minimo = 10, maximo = 100)

    private fun montar(
        lotes: List<br.com.borghi.estoquechocolate.core.modelo.Lote> = emptyList(),
        movimentacoes: List<Movimentacao> = emptyList(),
        divergencias: List<Divergencia> = emptyList(),
    ) = MontadorPainel.montar(listOf(bombom), lotes, movimentacoes, divergencias, HOJE)

    @Test
    fun `estoque saudavel nao mostra nenhuma pendencia`() {
        val painel = montar(
            listOf(
                lote("L1", validade = HOJE.plusDays(120), posicoes = listOf(GONDOLA to 50), ultimaConferencia = HOJE),
            ),
        )

        assertTrue(painel.tudoEmOrdem)
        assertEquals(0, painel.totalDePendencias)
    }

    @Test
    fun `vencido aparece sempre no topo das pendencias`() {
        val painel = montar(
            listOf(
                lote("normal", validade = HOJE.plusDays(120), posicoes = listOf(GONDOLA to 50)),
                lote("urgente", validade = HOJE.plusDays(10), posicoes = listOf(GONDOLA to 5)),
                lote("vencido", validade = HOJE.minusDays(2), posicoes = listOf(GONDOLA to 3)),
            ),
        )

        assertEquals(TipoCartao.VENCIDOS, painel.pendencias.first().tipo)
        assertFalse(painel.tudoEmOrdem)
    }

    @Test
    fun `cada lote cai em um unico cartao de validade, entao os numeros nao se repetem`() {
        val painel = montar(
            listOf(
                lote("critico", validade = HOJE.plusDays(3), posicoes = listOf(GONDOLA to 5)),
                lote("urgente", validade = HOJE.plusDays(12), posicoes = listOf(GONDOLA to 5)),
                lote("atencao", validade = HOJE.plusDays(25), posicoes = listOf(GONDOLA to 5)),
            ),
        )

        assertEquals(1, painel.cartao(TipoCartao.VENCE_EM_7_DIAS).quantidade)
        assertEquals(1, painel.cartao(TipoCartao.VENCE_EM_8_A_15_DIAS).quantidade)
        assertEquals(0, painel.cartao(TipoCartao.VENCIDOS).quantidade)
    }

    @Test
    fun `abaixo do minimo mostra quanto repor`() {
        val painel = montar(listOf(lote("L1", validade = HOJE.plusDays(100), posicoes = listOf(GONDOLA to 4))))

        val item = painel.cartao(TipoCartao.ABAIXO_DO_MINIMO).itens.single()
        assertEquals("Bombom ao leite", item.titulo)
        assertTrue(item.detalhe.contains("repor 96 un"))
    }

    @Test
    fun `estoque que existe mas nao pode ser vendido tem cartao proprio`() {
        val painel = montar(
            listOf(
                lote(
                    "segregado",
                    validade = HOJE.plusDays(100),
                    posicoes = listOf(SEGREGACAO to 20),
                    segregado = true,
                    motivo = MotivoSegregacao.AVARIA,
                ),
            ),
        )

        assertEquals(1, painel.cartao(TipoCartao.SEM_ESTOQUE_PARA_VENDA).quantidade)
    }

    @Test
    fun `produto sem nenhum lote entra em falta, mas nao no cartao de estoque que engana`() {
        val painel = montar(emptyList())

        // Precisa repor: cadastrado com minimo 10 e nada disponivel.
        assertEquals(1, painel.cartao(TipoCartao.ABAIXO_DO_MINIMO).quantidade)
        // E nao entra aqui, porque este cartao e so para o que existe no fisico e mesmo assim nao vende.
        assertEquals(0, painel.cartao(TipoCartao.SEM_ESTOQUE_PARA_VENDA).quantidade)
    }

    @Test
    fun `lote sem conferencia recente aparece, e o conferido hoje nao`() {
        val painel = montar(
            listOf(
                lote("nunca", validade = HOJE.plusDays(100), posicoes = listOf(GONDOLA to 50), ultimaConferencia = null),
                lote("hoje", validade = HOJE.plusDays(100), posicoes = listOf(GONDOLA to 50), ultimaConferencia = HOJE),
            ),
        )

        val cartao = painel.cartao(TipoCartao.SEM_CONFERENCIA_RECENTE)
        assertEquals(1, cartao.quantidade)
        assertTrue(cartao.itens.single().detalhe.contains("nunca conferido"))
    }

    @Test
    fun `divergencia aberta aparece e a resolvida some`() {
        val aberta = Divergencia(
            id = "D1", data = HOJE.minusDays(1), produtoCodigo = "CH001", loteId = "L1", codigoLote = "A1",
            localizacao = GONDOLA, quantidadeEsperada = q(10), quantidadeContada = q(8),
        )
        val resolvida = aberta.copy(
            id = "D2",
            status = br.com.borghi.estoquechocolate.core.modelo.StatusDivergencia.RESOLVIDA,
            dataResolucao = HOJE,
        )

        val painel = montar(divergencias = listOf(aberta, resolvida))

        val cartao = painel.cartao(TipoCartao.DIVERGENCIAS_ABERTAS)
        assertEquals(1, cartao.quantidade)
        assertTrue(cartao.itens.single().detalhe.contains("faltando 2 un"))
    }

    @Test
    fun `perdas do mes somam so as perdas, nunca a degustacao`() {
        val perda = Movimentacao(
            id = "M1", dataHora = AGORA.minusDays(2), produtoCodigo = "CH001", loteId = "L1", codigoLote = "A1",
            tipo = TipoMovimentacao.PERDA_VENCIMENTO, quantidade = q(4), observacao = "vencido",
        )
        val degustacao = perda.copy(id = "M2", tipo = TipoMovimentacao.DEGUSTACAO, quantidade = q(9))
        val mesPassado = perda.copy(id = "M3", dataHora = AGORA.minusMonths(1), quantidade = q(100))

        val painel = montar(movimentacoes = listOf(perda, degustacao, mesPassado))

        val cartao = painel.cartao(TipoCartao.PERDAS_DO_MES)
        assertEquals(1, cartao.quantidade)
        assertTrue(cartao.resumo.contains("Total perdido no mes: 4"))
    }

    @Test
    fun `os oito cartoes existem sempre, mesmo vazios, e saem na ordem de prioridade`() {
        val painel = montar()

        assertEquals(TipoCartao.entries.size, painel.cartoes.size)
        assertEquals(TipoCartao.entries.map { it.titulo }, painel.cartoes.map { it.tipo.titulo })
    }
}
