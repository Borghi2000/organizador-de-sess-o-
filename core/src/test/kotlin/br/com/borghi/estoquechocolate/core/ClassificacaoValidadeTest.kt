package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade.ATENCAO
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade.CRITICO
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade.MONITORAR
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade.NORMAL
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade.URGENTE
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade.VENCIDO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassificacaoValidadeTest {

    private fun em(dias: Long) = ClassificacaoValidade.de(HOJE.plusDays(dias), HOJE)

    @Test
    fun `validade anterior a hoje e vencido`() {
        assertEquals(VENCIDO, em(-1))
        assertEquals(VENCIDO, em(-30))
    }

    @Test
    fun `vencer hoje ainda nao e vencido, e critico`() {
        assertEquals(CRITICO, em(0))
    }

    @Test
    fun `as bordas de cada faixa caem no lado certo`() {
        assertEquals(CRITICO, em(7))
        assertEquals(URGENTE, em(8))
        assertEquals(URGENTE, em(15))
        assertEquals(ATENCAO, em(16))
        assertEquals(ATENCAO, em(30))
        assertEquals(MONITORAR, em(31))
        assertEquals(MONITORAR, em(60))
        assertEquals(NORMAL, em(61))
    }

    @Test
    fun `vencido tem a maior prioridade de todas`() {
        val ordenadas = ClassificacaoValidade.entries.sortedBy { it.prioridade }
        assertEquals(VENCIDO, ordenadas.first())
        assertTrue(ordenadas.zipWithNext().all { (a, b) -> a.prioridade < b.prioridade })
    }

    @Test
    fun `so vencido, critico e urgente exigem acao imediata`() {
        assertEquals(
            listOf(VENCIDO, CRITICO, URGENTE),
            ClassificacaoValidade.entries.filter { it.exigeAcao },
        )
    }

    @Test
    fun `o prazo e descrito em portugues`() {
        assertEquals("vence hoje", ClassificacaoValidade.descreverPrazo(HOJE, HOJE))
        assertEquals("vence amanha", ClassificacaoValidade.descreverPrazo(HOJE.plusDays(1), HOJE))
        assertEquals("vence em 12 dias", ClassificacaoValidade.descreverPrazo(HOJE.plusDays(12), HOJE))
        assertEquals("vencido ha 1 dia", ClassificacaoValidade.descreverPrazo(HOJE.minusDays(1), HOJE))
        assertEquals("vencido ha 5 dias", ClassificacaoValidade.descreverPrazo(HOJE.minusDays(5), HOJE))
    }
}
