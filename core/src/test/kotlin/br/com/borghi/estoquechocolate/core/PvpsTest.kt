package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import br.com.borghi.estoquechocolate.core.regras.Pvps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PvpsTest {

    private val maisNovo = lote("novo", validade = HOJE.plusDays(60))
    private val maisVelho = lote("velho", validade = HOJE.plusDays(5))
    private val meio = lote("meio", validade = HOJE.plusDays(20))

    @Test
    fun `o lote que vence primeiro aparece primeiro`() {
        val ordenados = Pvps.ordenar(listOf(maisNovo, meio, maisVelho))
        assertEquals(listOf("velho", "meio", "novo"), ordenados.map { it.id })
    }

    @Test
    fun `empate de validade e desempatado pelo codigo do lote, sempre na mesma ordem`() {
        val a = lote("x", codigoLote = "B001", validade = HOJE.plusDays(10))
        val b = lote("y", codigoLote = "A001", validade = HOJE.plusDays(10))
        assertEquals(listOf("y", "x"), Pvps.ordenar(listOf(a, b)).map { it.id })
        assertEquals(listOf("y", "x"), Pvps.ordenar(listOf(b, a)).map { it.id })
    }

    @Test
    fun `lote vencido nunca entra na fila de saida`() {
        val vencido = lote("vencido", validade = HOJE.minusDays(1))
        assertEquals(listOf("velho"), Pvps.disponiveis(listOf(vencido, maisVelho), HOJE).map { it.id })
        assertEquals("velho", Pvps.proximoLote(listOf(vencido, maisVelho), HOJE)?.id)
    }

    @Test
    fun `lote segregado nunca entra na fila de saida`() {
        val segregado = lote(
            "segregado",
            validade = HOJE.plusDays(1),
            posicoes = listOf(SEGREGACAO to 10),
            segregado = true,
            motivo = MotivoSegregacao.AVARIA,
        )
        assertEquals(listOf("velho"), Pvps.disponiveis(listOf(segregado, maisVelho), HOJE).map { it.id })
    }

    @Test
    fun `sem lote vendavel nao ha proximo lote`() {
        val vencido = lote("vencido", validade = HOJE.minusDays(1))
        assertNull(Pvps.proximoLote(listOf(vencido), HOJE))
    }

    @Test
    fun `consumo maior que um lote desce em cascata, um item por lote`() {
        val plano = Pvps.planejarConsumo(listOf(maisNovo, maisVelho, meio), q(25), HOJE)

        assertEquals(3, plano.itens.size)
        assertEquals(listOf("velho", "meio", "novo"), plano.itens.map { it.lote.id })
        assertEquals(listOf(q(10), q(10), q(5)), plano.itens.map { it.quantidade })
        assertTrue(plano.atendeTotalmente)
        assertTrue(plano.usaMaisDeUmLote)
        assertEquals(q(25), plano.quantidadePlanejada)
    }

    @Test
    fun `o que nao da para atender fica registrado como faltante, nao e arredondado`() {
        val plano = Pvps.planejarConsumo(listOf(maisVelho), q(30), HOJE)

        assertEquals(q(10), plano.quantidadePlanejada)
        assertEquals(q(20), plano.faltante)
        assertFalse(plano.atendeTotalmente)
    }

    @Test
    fun `o filtro de local restringe a origem do consumo`() {
        val comDeposito = lote("dep", validade = HOJE.plusDays(3), posicoes = listOf(DEPOSITO to 8, GONDOLA to 2))

        val doDeposito = Pvps.planejarConsumo(listOf(comDeposito), q(10), HOJE, TipoLocal.DEPOSITO)
        assertEquals(q(8), doDeposito.quantidadePlanejada)
        assertEquals(q(2), doDeposito.faltante)

        val daExposicao = Pvps.planejarConsumo(listOf(comDeposito), q(10), HOJE, TipoLocal.EXPOSICAO)
        assertEquals(q(2), daExposicao.quantidadePlanejada)
    }

    @Test
    fun `pedir quantidade zero nao planeja nada`() {
        val plano = Pvps.planejarConsumo(listOf(maisVelho), Quantidade.ZERO, HOJE)
        assertTrue(plano.itens.isEmpty())
        assertTrue(plano.atendeTotalmente)
    }
}
