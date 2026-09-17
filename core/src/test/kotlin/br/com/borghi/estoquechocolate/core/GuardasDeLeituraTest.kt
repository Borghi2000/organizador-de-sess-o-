package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.leitura.CampoEtiqueta
import br.com.borghi.estoquechocolate.core.leitura.GuardasDeLeitura
import br.com.borghi.estoquechocolate.core.leitura.InterpretadorDeEtiqueta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A camera propoe, o app duvida, voce confirma. Estas sao as duvidas que dependem do que ja esta
 * cadastrado, e por isso nao cabem dentro do interpretador.
 */
class GuardasDeLeituraTest {

    private fun ler(texto: String) = InterpretadorDeEtiqueta.interpretar(texto, HOJE)

    @Test
    fun `lote repetido com validade diferente segura o preenchimento`() {
        val existente = lote("l1", codigoLote = "2504A", validade = HOJE.plusDays(100))
        val avisos = GuardasDeLeitura.conferir(ler("VAL 31/12/2026 LOTE 2504A"), listOf(existente), HOJE)

        val conflito = avisos.single { it.campo == CampoEtiqueta.LOTE }
        assertTrue(conflito.seguraOPreenchimento)
        assertTrue(conflito.mensagem.contains("2504A"))
    }

    @Test
    fun `lote repetido com a mesma validade nao e conflito`() {
        val existente = lote("l1", codigoLote = "2504A", validade = java.time.LocalDate.of(2026, 12, 31))
        val avisos = GuardasDeLeitura.conferir(ler("VAL 31/12/2026 LOTE 2504A"), listOf(existente), HOJE)
        assertTrue(avisos.none { it.campo == CampoEtiqueta.LOTE })
    }

    @Test
    fun `validade muito fora do padrao do produto avisa, mas nao segura`() {
        val iguais = listOf(
            lote("a", codigoLote = "A1", validade = HOJE.plusDays(90)),
            lote("b", codigoLote = "B1", validade = HOJE.plusDays(100)),
            lote("c", codigoLote = "C1", validade = HOJE.plusDays(110)),
        )
        val avisos = GuardasDeLeitura.conferir(ler("VAL 31/12/2027 LOTE 9Z"), iguais, HOJE)

        val fora = avisos.single { it.campo == CampoEtiqueta.VALIDADE }
        assertTrue(fora.mensagem.contains("outros lotes"))
        assertTrue(!fora.seguraOPreenchimento)
    }

    @Test
    fun `validade dentro do padrao nao gera aviso nenhum`() {
        val iguais = listOf(
            lote("a", codigoLote = "A1", validade = HOJE.plusDays(90)),
            lote("b", codigoLote = "B1", validade = HOJE.plusDays(100)),
        )
        val avisos = GuardasDeLeitura.conferir(ler("VAL 31/12/2026 LOTE 9Z"), iguais, HOJE)
        assertEquals(emptyList(), avisos)
    }

    @Test
    fun `campo nao lido vira aviso que segura o preenchimento`() {
        val avisos = GuardasDeLeitura.conferir(ler("CHOCOLATE AO LEITE"), emptyList(), HOJE)
        assertEquals(
            listOf(CampoEtiqueta.VALIDADE, CampoEtiqueta.LOTE),
            avisos.map { it.campo },
        )
        assertTrue(avisos.all { it.seguraOPreenchimento })
    }

    @Test
    fun `o rascunho sai preenchido quando a leitura e confiavel`() {
        val rascunho = GuardasDeLeitura.paraRascunho(
            etiqueta = ler("VAL 31/12/2026 LOTE 2504A"),
            produto = produto(),
            lotesDoProduto = emptyList(),
            hoje = HOJE,
        )
        assertEquals("CH001", rascunho.produtoCodigo)
        assertEquals("2504A", rascunho.codigoLote)
        assertEquals(java.time.LocalDate.of(2026, 12, 31), rascunho.validade)
        assertEquals(GONDOLA, rascunho.localizacao)
    }

    @Test
    fun `campo duvidoso fica em branco no rascunho em vez de chutado`() {
        val rascunho = GuardasDeLeitura.paraRascunho(
            etiqueta = ler("01/01/2026 31/12/2026 LOTE 2504A"),
            produto = produto(),
            lotesDoProduto = emptyList(),
            hoje = HOJE,
        )
        assertNull(rascunho.validade)
        assertEquals("2504A", rascunho.codigoLote)
    }

    @Test
    fun `validade vencida lida da etiqueta nao entra sozinha no rascunho`() {
        val rascunho = GuardasDeLeitura.paraRascunho(
            etiqueta = ler("VAL 01/01/2020 LOTE 2504A"),
            produto = produto(),
            lotesDoProduto = emptyList(),
            hoje = HOJE,
        )
        assertNull(rascunho.validade)
    }

    @Test
    fun `o rascunho nao apaga o que ja estava digitado`() {
        val base = br.com.borghi.estoquechocolate.core.regras.RascunhoEntrada(
            quantidade = "12",
            observacoes = "caixa amassada",
        )
        val rascunho = GuardasDeLeitura.paraRascunho(
            etiqueta = ler("VAL 31/12/2026 LOTE 2504A"),
            produto = produto(),
            lotesDoProduto = emptyList(),
            hoje = HOJE,
            base = base,
        )
        assertEquals("12", rascunho.quantidade)
        assertEquals("caixa amassada", rascunho.observacoes)
    }
}
