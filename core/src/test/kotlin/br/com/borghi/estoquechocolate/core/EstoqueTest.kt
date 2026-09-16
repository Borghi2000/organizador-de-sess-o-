package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.regras.Estoque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EstoqueTest {

    private val bombom = produto(minimo = 10, maximo = 100)

    @Test
    fun `separa fisico, exposto, deposito e disponivel para venda`() {
        val lotes = listOf(
            lote("a", validade = HOJE.plusDays(30), posicoes = listOf(GONDOLA to 6, DEPOSITO to 20)),
            lote("b", validade = HOJE.plusDays(45), posicoes = listOf(VITRINE to 4)),
        )

        val resumo = Estoque.resumo(bombom, lotes, HOJE)

        assertEquals(q(30), resumo.fisico)
        assertEquals(q(10), resumo.exposto)
        assertEquals(q(20), resumo.deposito)
        assertEquals(q(30), resumo.disponivelParaVenda)
    }

    @Test
    fun `vencido sai do disponivel no dia em que vence, mesmo sem ninguem segregar`() {
        val lotes = listOf(
            lote("vencido", validade = HOJE.minusDays(1), posicoes = listOf(GONDOLA to 12)),
            lote("bom", validade = HOJE.plusDays(30), posicoes = listOf(GONDOLA to 5)),
        )

        val resumo = Estoque.resumo(bombom, lotes, HOJE)

        assertEquals(q(17), resumo.fisico)
        assertEquals(q(5), resumo.disponivelParaVenda)
        assertEquals(q(12), resumo.vencidoSolto)
        assertTrue(resumo.abaixoDoMinimo)
    }

    @Test
    fun `segregado conta no fisico mas nunca no disponivel`() {
        val lotes = listOf(
            lote(
                "avariado",
                validade = HOJE.plusDays(40),
                posicoes = listOf(SEGREGACAO to 9),
                segregado = true,
                motivo = MotivoSegregacao.AVARIA,
            ),
        )

        val resumo = Estoque.resumo(bombom, lotes, HOJE)

        assertEquals(q(9), resumo.fisico)
        assertEquals(q(9), resumo.segregado)
        assertEquals(q(0), resumo.disponivelParaVenda)
        assertTrue(resumo.temEstoqueMasNaoPodeVender)
    }

    @Test
    fun `produto sem nenhum lote nao conta como estoque que engana`() {
        val resumo = Estoque.resumo(bombom, emptyList(), HOJE)

        assertTrue(resumo.semEstoqueParaVenda)
        assertFalse(resumo.existeNoSistema)
        assertFalse(resumo.temEstoqueMasNaoPodeVender)
    }

    @Test
    fun `sugestao de compra leva ao maximo e falta para o minimo mostra o buraco`() {
        val lotes = listOf(lote("a", posicoes = listOf(GONDOLA to 4)))

        val resumo = Estoque.resumo(bombom, lotes, HOJE)

        assertEquals(q(96), resumo.sugestaoDeCompra)
        assertEquals(q(6), resumo.faltaParaOMinimo)
    }

    @Test
    fun `lotes de outro produto nao entram na conta`() {
        val lotes = listOf(
            lote("meu", posicoes = listOf(GONDOLA to 5)),
            lote("outro", produtoCodigo = "CH999", posicoes = listOf(GONDOLA to 500)),
        )

        assertEquals(q(5), Estoque.resumo(bombom, lotes, HOJE).fisico)
    }

    @Test
    fun `lista de abaixo do minimo vem do mais critico para o menos`() {
        val a = produto(codigo = "A", nome = "A", minimo = 10)
        val b = produto(codigo = "B", nome = "B", minimo = 10)
        val lotes = listOf(
            lote("la", produtoCodigo = "A", posicoes = listOf(GONDOLA to 8)),
            lote("lb", produtoCodigo = "B", posicoes = listOf(GONDOLA to 1)),
        )

        val faltas = Estoque.abaixoDoMinimo(listOf(a, b), lotes, HOJE)

        assertEquals(listOf("B", "A"), faltas.map { it.produto.codigo })
    }
}
