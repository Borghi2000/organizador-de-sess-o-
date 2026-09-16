package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.StatusConferencia
import br.com.borghi.estoquechocolate.core.regras.Contagens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContagensTest {

    @Test
    fun `contagem que bate nao gera divergencia`() {
        val existente = lote("L1", posicoes = listOf(GONDOLA to 10))

        val resultado = Contagens.registrar(existente, GONDOLA, q(10), HOJE, gerarId = IdSequencial().gerar)

        assertNull(resultado.divergencia)
        assertTrue(resultado.bateu)
        assertTrue(resultado.item.conferiu)
    }

    @Test
    fun `contagem diferente abre divergencia e NAO altera o estoque`() {
        val existente = lote("L1", posicoes = listOf(GONDOLA to 10))

        val resultado = Contagens.registrar(
            existente, GONDOLA, q(7), HOJE, "Contagem da tarde", IdSequencial("div").gerar,
        )

        val divergencia = resultado.divergencia!!
        assertEquals(q(10), divergencia.quantidadeEsperada)
        assertEquals(q(7), divergencia.quantidadeContada)
        assertEquals(q(-3), divergencia.diferenca)
        assertTrue(divergencia.ehFalta)
        assertTrue(divergencia.estaAberta)
        assertEquals("div-1", resultado.item.divergenciaId)
        assertEquals(q(10), existente.quantidadeEm(GONDOLA), "a contagem sozinha nunca corrige o estoque")
    }

    @Test
    fun `sobra tambem vira divergencia`() {
        val existente = lote("L1", posicoes = listOf(GONDOLA to 10))

        val divergencia = Contagens.registrar(existente, GONDOLA, q(12), HOJE, gerarId = IdSequencial().gerar).divergencia!!

        assertTrue(divergencia.ehSobra)
        assertEquals(q(2), divergencia.diferenca)
    }

    @Test
    fun `fechar a conferencia carimba a data nos lotes contados e lista o que ficou de fora`() {
        val ids = IdSequencial()
        val contado = lote("contado", posicoes = listOf(GONDOLA to 5), ultimaConferencia = HOJE.minusDays(20))
        val esquecido = lote("esquecido", validade = HOJE.plusDays(3), posicoes = listOf(GONDOLA to 2), ultimaConferencia = null)
        val item = Contagens.registrar(contado, GONDOLA, q(5), HOJE, gerarId = ids.gerar).item
        val conferencia = Conferencia(id = "C1", data = HOJE, itens = listOf(item))

        val resultado = Contagens.fechar(conferencia, listOf(contado, esquecido), emptyList(), HOJE)

        assertEquals(StatusConferencia.FECHADA, resultado.conferencia.status)
        assertEquals(HOJE, resultado.lotesAtualizados.single().dataUltimaConferencia)
        assertEquals(listOf("esquecido"), resultado.lotesNaoConferidos.map { it.id })
        assertFalse(resultado.conferiuTudo)
    }

    @Test
    fun `a sugestao do dia traz primeiro quem esta ha mais tempo sem contagem`() {
        val nunca = lote("nunca", posicoes = listOf(GONDOLA to 1), ultimaConferencia = null)
        val antigo = lote("antigo", posicoes = listOf(GONDOLA to 1), ultimaConferencia = HOJE.minusDays(30))
        val recente = lote("recente", posicoes = listOf(GONDOLA to 1), ultimaConferencia = HOJE.minusDays(1))
        val vazio = lote("vazio", posicoes = emptyList(), ultimaConferencia = null)

        val sugestao = Contagens.sugestaoDoDia(listOf(recente, antigo, nunca, vazio), HOJE, diasSemConferencia = 7)

        assertEquals(listOf("nunca", "antigo"), sugestao.map { it.id })
    }
}
