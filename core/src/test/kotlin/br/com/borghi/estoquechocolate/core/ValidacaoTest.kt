package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import br.com.borghi.estoquechocolate.core.regras.RascunhoContagem
import br.com.borghi.estoquechocolate.core.regras.RascunhoEntrada
import br.com.borghi.estoquechocolate.core.regras.RascunhoMovimentacao
import br.com.borghi.estoquechocolate.core.regras.RascunhoProduto
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidacaoTest {

    @Test
    fun `entrada em branco aponta todos os campos que faltam, um por um`() {
        val resultado = RascunhoEntrada().validar(HOJE)

        assertFalse(resultado.valido)
        assertEquals(
            setOf("produto", "codigoLote", "validade", "localizacao", "quantidade"),
            resultado.faltantes.map { it.campo }.toSet(),
        )
    }

    @Test
    fun `entrada completa passa`() {
        val resultado = RascunhoEntrada(
            produtoCodigo = "CH001",
            codigoLote = "A2501",
            validade = HOJE.plusDays(30),
            quantidade = "24",
            localizacao = GONDOLA,
        ).validar(HOJE)

        assertTrue(resultado.valido)
        assertTrue(resultado.avisos.isEmpty())
    }

    @Test
    fun `quantidade zero ou invalida bloqueia a entrada`() {
        assertContains(
            RascunhoEntrada(
                produtoCodigo = "CH001", codigoLote = "A", validade = HOJE, quantidade = "0", localizacao = GONDOLA,
            ).validar(HOJE).faltantes.map { it.campo },
            "quantidade",
        )
        assertContains(
            RascunhoEntrada(
                produtoCodigo = "CH001", codigoLote = "A", validade = HOJE, quantidade = "x", localizacao = GONDOLA,
            ).validar(HOJE).faltantes.map { it.campo },
            "quantidade",
        )
    }

    @Test
    fun `receber mercadoria ja vencida gera aviso, mas nao e impedido`() {
        val resultado = RascunhoEntrada(
            produtoCodigo = "CH001",
            codigoLote = "A2501",
            validade = HOJE.minusDays(1),
            quantidade = "5",
            localizacao = GONDOLA,
        ).validar(HOJE)

        assertTrue(resultado.valido)
        assertTrue(resultado.avisos.single().contains("ja passou"))
    }

    @Test
    fun `entrada com validade critica avisa antes de gravar`() {
        val resultado = RascunhoEntrada(
            produtoCodigo = "CH001",
            codigoLote = "A2501",
            validade = HOJE.plusDays(4),
            quantidade = "5",
            localizacao = GONDOLA,
        ).validar(HOJE)

        assertTrue(resultado.avisos.single().contains("critica"))
    }

    @Test
    fun `perda sem justificativa nao passa`() {
        val resultado = RascunhoMovimentacao(
            tipo = TipoMovimentacao.PERDA_VENCIMENTO,
            loteId = "L1",
            quantidade = "3",
            localOrigem = GONDOLA,
        ).validar()

        assertEquals(listOf("observacao"), resultado.faltantes.map { it.campo })
        assertTrue(resultado.resumoDoQueFalta.contains("Perda por vencimento"))
    }

    @Test
    fun `transferencia exige origem e destino diferentes`() {
        val resultado = RascunhoMovimentacao(
            tipo = TipoMovimentacao.TRANSFERENCIA,
            loteId = "L1",
            quantidade = "3",
            localOrigem = GONDOLA,
            localDestino = GONDOLA,
        ).validar()

        assertEquals(listOf("localDestino"), resultado.faltantes.map { it.campo })
    }

    @Test
    fun `produto com maximo menor que o minimo e recusado`() {
        val resultado = RascunhoProduto(
            codigo = "CH001",
            nome = "Bombom",
            categoria = "Bombons",
            unidade = UnidadeMedida.UNIDADE,
            estoqueMinimo = "50",
            estoqueMaximo = "10",
            localPadrao = GONDOLA,
        ).validar()

        assertEquals(listOf("estoqueMaximo"), resultado.faltantes.map { it.campo })
    }

    @Test
    fun `codigo de produto repetido e recusado`() {
        val resultado = RascunhoProduto(
            codigo = "CH001",
            nome = "Bombom",
            categoria = "Bombons",
            unidade = UnidadeMedida.UNIDADE,
            estoqueMinimo = "10",
            estoqueMaximo = "50",
            localPadrao = GONDOLA,
        ).validar(codigosExistentes = setOf("CH001"))

        assertEquals(listOf("codigo"), resultado.faltantes.map { it.campo })
    }

    @Test
    fun `a tela de confirmacao mostra os dados essenciais antes de gravar`() {
        val linhas = RascunhoEntrada(
            produtoCodigo = "CH001",
            codigoLote = "A2501",
            validade = HOJE.plusDays(30),
            quantidade = "1,5",
            localizacao = GONDOLA,
        ).paraConfirmacao("Chocolate granel", UnidadeMedida.QUILO)

        assertEquals("Chocolate granel", linhas.first { it.rotulo == "Produto" }.valor)
        assertEquals("16/10/2026", linhas.first { it.rotulo == "Validade" }.valor)
        assertEquals("1,5 kg", linhas.first { it.rotulo == "Quantidade" }.valor)
        assertEquals("Gondola (Exposicao)", linhas.first { it.rotulo == "Local" }.valor)
    }

    @Test
    fun `contagem sem quantidade pede o campo em vez de assumir zero`() {
        val resultado = RascunhoContagem(loteId = "L1", localizacao = GONDOLA).validar()

        assertEquals(listOf("quantidadeContada"), resultado.faltantes.map { it.campo })
    }
}
