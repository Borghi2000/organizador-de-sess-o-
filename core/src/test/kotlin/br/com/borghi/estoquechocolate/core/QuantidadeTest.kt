package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuantidadeTest {

    @Test
    fun `le numero inteiro e fracionario com virgula ou ponto`() {
        assertEquals(q(3), Quantidade.deTexto("3"))
        assertEquals(Quantidade(1500), Quantidade.deTexto("1,5"))
        assertEquals(Quantidade(1500), Quantidade.deTexto("1.5"))
        assertEquals(Quantidade(250), Quantidade.deTexto("0,25"))
        assertEquals(Quantidade(250), Quantidade.deTexto(",25"))
        assertEquals(Quantidade(-2000), Quantidade.deTexto("-2"))
    }

    @Test
    fun `texto invalido devolve null para a tela pedir o campo de novo`() {
        assertNull(Quantidade.deTexto(""))
        assertNull(Quantidade.deTexto("   "))
        assertNull(Quantidade.deTexto("abc"))
        assertNull(Quantidade.deTexto("1,2,3"))
        assertNull(Quantidade.deTexto("1,2345"))
        assertNull(Quantidade.deTexto("-"))
    }

    @Test
    fun `formata no padrao brasileiro sem casas inuteis`() {
        assertEquals("3", q(3).formatar())
        assertEquals("1,5", Quantidade(1500).formatar())
        assertEquals("0,25", Quantidade(250).formatar())
        assertEquals("-2", Quantidade(-2000).formatar())
        assertEquals("1,5 kg", Quantidade(1500).formatar(UnidadeMedida.QUILO))
        assertEquals("3 un", q(3).formatar(UnidadeMedida.UNIDADE))
    }

    @Test
    fun `soma de fracoes fecha exata, sem erro de ponto flutuante`() {
        val total = Quantidade.deTexto("0,1")!! + Quantidade.deTexto("0,2")!!
        assertEquals(Quantidade.deTexto("0,3"), total)
        assertEquals("0,3", total.formatar())
    }

    @Test
    fun `menorEntre e naoNegativa protegem as contas de estoque`() {
        assertEquals(q(2), q(5).menorEntre(q(2)))
        assertEquals(q(5), q(5).menorEntre(q(9)))
        assertEquals(Quantidade.ZERO, (q(2) - q(5)).naoNegativa())
        assertTrue((q(2) - q(5)).ehNegativa)
    }
}
