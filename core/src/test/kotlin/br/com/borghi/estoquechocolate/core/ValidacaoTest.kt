package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import br.com.borghi.estoquechocolate.core.regras.RascunhoContagem
import br.com.borghi.estoquechocolate.core.regras.RascunhoEntrada
import br.com.borghi.estoquechocolate.core.regras.RascunhoMovimentacao
import br.com.borghi.estoquechocolate.core.regras.Operacoes
import br.com.borghi.estoquechocolate.core.regras.RascunhoProduto
import br.com.borghi.estoquechocolate.core.regras.ResultadoEntrada
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

    @Test
    fun `codigo de barras repetido diz de qual produto ele e`() {
        val rascunho = RascunhoProduto(
            codigo = "CH900",
            nome = "Barra 70%",
            categoria = "Barras",
            unidade = UnidadeMedida.UNIDADE,
            estoqueMinimo = "5",
            estoqueMaximo = "50",
            localPadrao = GONDOLA,
            codigoBarras = "7891000315507",
        )
        val resultado = rascunho.validar(
            codigosDeBarrasExistentes = mapOf("7891000315507" to "Bombom ao leite"),
        )
        assertFalse(resultado.valido)
        assertTrue(resultado.faltantes.single().mensagem.contains("Bombom ao leite"))
    }

    @Test
    fun `codigo de barras que nao passa no digito verificador avisa, mas nao bloqueia`() {
        val rascunho = RascunhoProduto(
            codigo = "CH901",
            nome = "Trufa",
            categoria = "Trufas",
            unidade = UnidadeMedida.UNIDADE,
            estoqueMinimo = "5",
            estoqueMaximo = "50",
            localPadrao = GONDOLA,
            codigoBarras = "7891000315508",
        )
        val resultado = rascunho.validar()
        assertTrue(resultado.valido)
        assertTrue(resultado.avisos.single().contains("digito verificador"))
    }

    @Test
    fun `a foto da etiqueta fica guardada no lote da entrada`() {
        val resultado = Operacoes.entrada(
            produto = produto(),
            lotesDoProduto = emptyList(),
            codigoLote = "2504A",
            validade = HOJE.plusDays(90),
            quantidade = q(10),
            localizacao = GONDOLA,
            agora = AGORA,
            fotoEtiqueta = "etiqueta-1.jpg",
            gerarId = IdSequencial().gerar,
        )
        val sucesso = resultado as ResultadoEntrada.Sucesso
        assertEquals("etiqueta-1.jpg", sucesso.resultado.lotesAtualizados.single().fotoEtiqueta)
    }

    @Test
    fun `uma segunda entrada no mesmo lote nao troca a foto original da etiqueta`() {
        val existente = lote("l1", codigoLote = "2504A", validade = HOJE.plusDays(90))
            .copy(fotoEtiqueta = "primeira.jpg")
        val resultado = Operacoes.entrada(
            produto = produto(),
            lotesDoProduto = listOf(existente),
            codigoLote = "2504A",
            validade = HOJE.plusDays(90),
            quantidade = q(5),
            localizacao = GONDOLA,
            agora = AGORA,
            fotoEtiqueta = "segunda.jpg",
            gerarId = IdSequencial().gerar,
        )
        val sucesso = resultado as ResultadoEntrada.Sucesso
        assertEquals("primeira.jpg", sucesso.resultado.lotesAtualizados.single().fotoEtiqueta)
    }
}
