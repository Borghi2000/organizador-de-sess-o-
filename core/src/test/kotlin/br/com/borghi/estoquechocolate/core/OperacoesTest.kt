package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.regras.Operacoes
import br.com.borghi.estoquechocolate.core.regras.ResultadoEntrada
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OperacoesTest {

    private val bombom = produto()

    @Test
    fun `entrada de lote novo cria o lote e grava a movimentacao`() {
        val ids = IdSequencial()

        val resultado = Operacoes.entrada(
            produto = bombom,
            lotesDoProduto = emptyList(),
            codigoLote = "A2501",
            validade = HOJE.plusDays(40),
            quantidade = q(24),
            localizacao = GONDOLA,
            agora = AGORA,
            observacao = "Nota 123",
            gerarId = ids.gerar,
        )

        val sucesso = assertIs<ResultadoEntrada.Sucesso>(resultado)
        assertTrue(sucesso.loteNovo)
        val lote = sucesso.resultado.lotesAtualizados.single()
        assertEquals(q(24), lote.quantidadeAtual)
        assertEquals(q(24), lote.quantidadeRecebida)
        assertEquals(q(24), lote.quantidadeEm(GONDOLA))

        val movimentacao = sucesso.resultado.movimentacoes.single()
        assertEquals(TipoMovimentacao.ENTRADA, movimentacao.tipo)
        assertEquals(GONDOLA, movimentacao.localDestino)
        assertEquals("Nota 123", movimentacao.observacao)
        assertEquals(AGORA, movimentacao.dataHora)
    }

    @Test
    fun `entrada no mesmo lote e validade soma no lote existente e avisa`() {
        val existente = lote("L1", codigoLote = "A2501", validade = HOJE.plusDays(40), posicoes = listOf(GONDOLA to 10))

        val resultado = Operacoes.entrada(
            produto = bombom,
            lotesDoProduto = listOf(existente),
            codigoLote = "A2501",
            validade = HOJE.plusDays(40),
            quantidade = q(5),
            localizacao = GONDOLA,
            agora = AGORA,
            gerarId = IdSequencial().gerar,
        )

        val sucesso = assertIs<ResultadoEntrada.Sucesso>(resultado)
        assertFalse(sucesso.loteNovo)
        assertEquals(q(15), sucesso.resultado.lotesAtualizados.single().quantidadeAtual)
        assertTrue(sucesso.resultado.avisos.single().contains("ja existia"))
    }

    @Test
    fun `mesmo codigo de lote com validade diferente vira conflito, nao um palpite`() {
        val existente = lote("L1", codigoLote = "A2501", validade = HOJE.plusDays(40))

        val resultado = Operacoes.entrada(
            produto = bombom,
            lotesDoProduto = listOf(existente),
            codigoLote = "A2501",
            validade = HOJE.plusDays(90),
            quantidade = q(5),
            localizacao = GONDOLA,
            agora = AGORA,
            gerarId = IdSequencial().gerar,
        )

        val conflito = assertIs<ResultadoEntrada.ConflitoDeValidade>(resultado)
        assertEquals(HOJE.plusDays(40), conflito.loteExistente.validade)
        assertEquals(HOJE.plusDays(90), conflito.validadeInformada)
    }

    @Test
    fun `reposicao tira do deposito pelo lote que vence primeiro`() {
        val velho = lote("velho", validade = HOJE.plusDays(5), posicoes = listOf(DEPOSITO to 6))
        val novo = lote("novo", validade = HOJE.plusDays(50), posicoes = listOf(DEPOSITO to 30))

        val resultado = Operacoes.reposicao(
            lotesDoProduto = listOf(novo, velho),
            quantidade = q(10),
            destino = GONDOLA,
            hoje = HOJE,
            agora = AGORA,
            gerarId = IdSequencial().gerar,
        )

        assertEquals(listOf("velho", "novo"), resultado.movimentacoes.map { it.loteId })
        assertEquals(listOf(q(6), q(4)), resultado.movimentacoes.map { it.quantidade })
        assertTrue(resultado.movimentacoes.all { it.tipo == TipoMovimentacao.REPOSICAO })
        assertTrue(resultado.atendeuTudo)

        val loteVelho = resultado.lotesAtualizados.first { it.id == "velho" }
        assertEquals(q(0), loteVelho.quantidadeEm(DEPOSITO))
        assertEquals(q(6), loteVelho.quantidadeEm(GONDOLA))
        assertEquals(q(6), loteVelho.quantidadeAtual, "transferencia nao muda o total do lote")
    }

    @Test
    fun `reposicao maior que o deposito avisa quanto faltou em vez de inventar`() {
        val unico = lote("unico", validade = HOJE.plusDays(5), posicoes = listOf(DEPOSITO to 4))

        val resultado = Operacoes.reposicao(
            lotesDoProduto = listOf(unico),
            quantidade = q(10),
            destino = GONDOLA,
            hoje = HOJE,
            agora = AGORA,
            gerarId = IdSequencial().gerar,
        )

        assertEquals(q(6), resultado.naoAtendido)
        assertFalse(resultado.atendeuTudo)
        assertTrue(resultado.avisos.any { it.contains("Faltaram") })
    }

    @Test
    fun `saida consome da exposicao e nunca de lote vencido`() {
        val vencido = lote("vencido", validade = HOJE.minusDays(2), posicoes = listOf(GONDOLA to 50))
        val bom = lote("bom", validade = HOJE.plusDays(10), posicoes = listOf(GONDOLA to 8))

        val resultado = Operacoes.saida(
            lotesDoProduto = listOf(vencido, bom),
            quantidade = q(5),
            hoje = HOJE,
            agora = AGORA,
            gerarId = IdSequencial().gerar,
        )

        assertEquals("bom", resultado.movimentacoes.single().loteId)
        assertEquals(q(3), resultado.lotesAtualizados.single().quantidadeEm(GONDOLA))
    }

    @Test
    fun `perda da baixa no lote escolhido e guarda a justificativa`() {
        val vencido = lote("vencido", validade = HOJE.minusDays(1), posicoes = listOf(GONDOLA to 7))

        val resultado = Operacoes.baixaDeLote(
            lote = vencido,
            tipo = TipoMovimentacao.PERDA_VENCIMENTO,
            quantidade = q(7),
            origem = GONDOLA,
            agora = AGORA,
            observacao = "Descartado na conferencia da manha",
            gerarId = IdSequencial().gerar,
        )

        assertEquals(q(0), resultado.lotesAtualizados.single().quantidadeAtual)
        val movimentacao = resultado.movimentacoes.single()
        assertEquals(TipoMovimentacao.PERDA_VENCIMENTO, movimentacao.tipo)
        assertTrue(movimentacao.tipo.ehPerda)
        assertEquals("Descartado na conferencia da manha", movimentacao.observacao)
    }

    @Test
    fun `baixa maior que o existente baixa so o que existe e avisa`() {
        val existente = lote("L1", posicoes = listOf(GONDOLA to 3))

        val resultado = Operacoes.baixaDeLote(
            lote = existente,
            tipo = TipoMovimentacao.PERDA_AVARIA,
            quantidade = q(10),
            origem = GONDOLA,
            agora = AGORA,
            observacao = "Caixa amassada",
            gerarId = IdSequencial().gerar,
        )

        assertEquals(q(3), resultado.movimentacoes.single().quantidade)
        assertEquals(q(7), resultado.naoAtendido)
        assertTrue(resultado.avisos.isNotEmpty())
    }

    @Test
    fun `degustacao sai do estoque mas nao conta como perda`() {
        val existente = lote("L1", posicoes = listOf(GONDOLA to 10))

        val resultado = Operacoes.baixaDeLote(
            lote = existente,
            tipo = TipoMovimentacao.DEGUSTACAO,
            quantidade = q(2),
            origem = GONDOLA,
            agora = AGORA,
            observacao = "Prova de sabado",
            gerarId = IdSequencial().gerar,
        )

        assertEquals(q(8), resultado.lotesAtualizados.single().quantidadeAtual)
        assertFalse(resultado.movimentacoes.single().tipo.ehPerda)
    }

    @Test
    fun `transferencia move entre locais sem mudar o total`() {
        val existente = lote("L1", posicoes = listOf(DEPOSITO to 10))

        val resultado = Operacoes.transferencia(
            lote = existente,
            quantidade = q(4),
            origem = DEPOSITO,
            destino = VITRINE,
            agora = AGORA,
            gerarId = IdSequencial().gerar,
        )

        val atualizado = resultado.lotesAtualizados.single()
        assertEquals(q(10), atualizado.quantidadeAtual)
        assertEquals(q(6), atualizado.quantidadeEm(DEPOSITO))
        assertEquals(q(4), atualizado.quantidadeEm(VITRINE))
    }

    @Test
    fun `segregar marca o lote e leva todas as posicoes para a area de segregacao`() {
        val existente = lote("L1", validade = HOJE.minusDays(1), posicoes = listOf(GONDOLA to 6, DEPOSITO to 4))

        val resultado = Operacoes.segregar(
            lote = existente,
            motivo = MotivoSegregacao.VENCIDO,
            agora = AGORA,
            observacao = "Retirado da gondola",
            gerarId = IdSequencial().gerar,
        )

        val atualizado = resultado.lotesAtualizados.single()
        assertTrue(atualizado.segregado)
        assertEquals(MotivoSegregacao.VENCIDO, atualizado.motivoSegregacao)
        assertEquals(q(10), atualizado.quantidadeEm(SEGREGACAO))
        assertEquals(q(10), atualizado.quantidadeAtual)
        assertEquals(q(0), atualizado.quantidadeDisponivelParaVenda(HOJE))
        assertEquals(2, resultado.movimentacoes.size, "uma movimentacao por local de origem")
    }

    @Test
    fun `ajuste de inventario corrige o lote e fecha a divergencia`() {
        val ids = IdSequencial()
        val existente = lote("L1", posicoes = listOf(GONDOLA to 10))
        val contagem = br.com.borghi.estoquechocolate.core.regras.Contagens.registrar(
            lote = existente,
            localizacao = GONDOLA,
            quantidadeContada = q(8),
            hoje = HOJE,
            gerarId = ids.gerar,
        )

        val resultado = Operacoes.ajusteDeInventario(
            lote = existente,
            divergencia = contagem.divergencia!!,
            hoje = HOJE,
            agora = AGORA,
            observacao = "Aceita a contagem fisica",
            gerarId = ids.gerar,
        )

        assertEquals(q(8), resultado.lotesAtualizados.single().quantidadeEm(GONDOLA))
        val movimentacao = resultado.movimentacoes.single()
        assertEquals(TipoMovimentacao.AJUSTE_INVENTARIO, movimentacao.tipo)
        assertEquals(q(2), movimentacao.quantidade)
        assertEquals(contagem.divergencia.id, movimentacao.divergenciaId)
        val resolvida = resultado.divergenciasAtualizadas.single()
        assertFalse(resolvida.estaAberta)
        assertEquals(HOJE, resolvida.dataResolucao)
    }
}
