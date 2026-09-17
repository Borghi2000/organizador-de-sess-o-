package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.leitura.CampoEtiqueta
import br.com.borghi.estoquechocolate.core.leitura.Confianca
import br.com.borghi.estoquechocolate.core.leitura.InterpretadorDeEtiqueta
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O interpretador e a peca de que a camera inteira depende. Como e Kotlin puro, da para cobrir
 * aqui os formatos que as etiquetas usam de verdade, sem aparelho e sem rede.
 */
class InterpretadorDeEtiquetaTest {

    private fun ler(texto: String, hoje: LocalDate = HOJE) =
        InterpretadorDeEtiqueta.interpretar(texto, hoje)

    private fun data(dia: Int, mes: Int, ano: Int) = LocalDate.of(ano, mes, dia)

    // ------------------------------------------------------------- validade

    @Test
    fun `le VAL com barra e ano de dois digitos`() {
        val etiqueta = ler("VAL 31/12/26")
        assertEquals(data(31, 12, 2026), etiqueta.validade?.valor)
        assertEquals(Confianca.ALTA, etiqueta.validade?.confianca)
    }

    @Test
    fun `le VALIDADE com dois pontos e ponto separando, ano de quatro digitos`() {
        assertEquals(data(31, 12, 2026), ler("VALIDADE: 31.12.2026").validade?.valor)
    }

    @Test
    fun `le validade com hifen`() {
        assertEquals(data(1, 3, 2027), ler("VAL: 01-03-2027").validade?.valor)
    }

    @Test
    fun `le CONSUMIR ANTES DE`() {
        assertEquals(data(15, 5, 2027), ler("CONSUMIR ANTES DE 15/05/2027").validade?.valor)
    }

    @Test
    fun `le CONSUMIR PREFERENCIALMENTE ANTES DE`() {
        val etiqueta = ler("CONSUMIR PREFERENCIALMENTE ANTES DE: 20/11/2026")
        assertEquals(data(20, 11, 2026), etiqueta.validade?.valor)
        assertEquals(Confianca.ALTA, etiqueta.validade?.confianca)
    }

    @Test
    fun `le VENCIMENTO e VALIDO ATE`() {
        assertEquals(data(2, 2, 2027), ler("VENCIMENTO 02/02/27").validade?.valor)
        assertEquals(data(2, 2, 2027), ler("VALIDO ATE 02/02/2027").validade?.valor)
    }

    @Test
    fun `le mes escrito por extenso`() {
        assertEquals(data(31, 12, 2026), ler("VAL 31 DEZ 26").validade?.valor)
        assertEquals(data(5, 1, 2027), ler("VALIDADE 05 JANEIRO 2027").validade?.valor)
    }

    @Test
    fun `le data compacta quando tem rotulo antes`() {
        assertEquals(data(31, 12, 2026), ler("VAL 311226").validade?.valor)
    }

    @Test
    fun `sem rotulo, seis digitos seguidos nao viram data`() {
        val etiqueta = ler("LOTE 311226")
        assertNull(etiqueta.validade)
        assertEquals("311226", etiqueta.lote?.valor)
    }

    @Test
    fun `mes e ano viram o ultimo dia do mes, com aviso`() {
        val etiqueta = ler("VAL 12/2026")
        assertEquals(data(31, 12, 2026), etiqueta.validade?.valor)
        assertTrue(etiqueta.avisos.any { it.contains("mes e ano") })
    }

    @Test
    fun `fabricacao e validade na mesma etiqueta ficam em campos diferentes`() {
        val etiqueta = ler("FAB 01/01/26 VAL 01/01/27")
        assertEquals(data(1, 1, 2026), etiqueta.fabricacao?.valor)
        assertEquals(data(1, 1, 2027), etiqueta.validade?.valor)
    }

    @Test
    fun `validade escrita em meses e calculada a partir da fabricacao`() {
        val etiqueta = ler("FABRICACAO 01/01/2026 - VALIDADE 12 MESES")
        assertEquals(data(1, 1, 2027), etiqueta.validade?.valor)
        assertEquals(Confianca.MEDIA, etiqueta.validade?.confianca)
        assertTrue(etiqueta.avisos.any { it.contains("12 meses") })
    }

    @Test
    fun `data solta sem rotulo nenhum e aceita, mas sem confianca alta`() {
        val etiqueta = ler("BOMBOM AO LEITE 31/12/2026")
        assertEquals(data(31, 12, 2026), etiqueta.validade?.valor)
        assertEquals(Confianca.MEDIA, etiqueta.validade?.confianca)
    }

    @Test
    fun `duas datas sem rotulo nao preenchem sozinhas`() {
        val etiqueta = ler("01/01/2026 31/12/2026")
        assertEquals(data(31, 12, 2026), etiqueta.validade?.valor)
        assertEquals(Confianca.BAIXA, etiqueta.validade?.confianca)
        assertNull(etiqueta.validadeConfiavel)
        assertTrue(CampoEtiqueta.VALIDADE in etiqueta.camposParaConferir)
    }

    @Test
    fun `o ano de dois digitos sempre aparece expandido num aviso`() {
        val etiqueta = ler("VAL 31/12/26")
        assertTrue(etiqueta.avisos.any { it.contains("31/12/2026") })
    }

    @Test
    fun `ano de dois digitos resolve para o futuro na validade e para o passado na fabricacao`() {
        val etiqueta = ler("FAB 10/10/25 VAL 10/10/27", hoje = LocalDate.of(2026, 9, 16))
        assertEquals(data(10, 10, 2025), etiqueta.fabricacao?.valor)
        assertEquals(data(10, 10, 2027), etiqueta.validade?.valor)
    }

    // ------------------------------------------------------------- guardas

    @Test
    fun `validade no passado e segurada, nao aceita calada`() {
        val etiqueta = ler("VAL 01/01/2020")
        assertEquals(data(1, 1, 2020), etiqueta.validade?.valor)
        assertEquals(Confianca.BAIXA, etiqueta.validade?.confianca)
        assertNull(etiqueta.validadeConfiavel)
        assertTrue(etiqueta.avisos.any { it.contains("ja passou") })
    }

    @Test
    fun `validade a mais de tres anos e segurada`() {
        val etiqueta = ler("VAL 31/12/2040")
        assertEquals(Confianca.BAIXA, etiqueta.validade?.confianca)
        assertTrue(etiqueta.avisos.any { it.contains("3 anos") })
    }

    @Test
    fun `dia 31 num mes de 30 dias nao vira data`() {
        assertNull(ler("VAL 31/11/2026").validade)
    }

    @Test
    fun `mes 13 nao vira data`() {
        assertNull(ler("VAL 01/13/2026").validade)
    }

    @Test
    fun `letra no lugar de numero e corrigida, mas rebaixa a confianca`() {
        val etiqueta = ler("VAL 3I/I2/26")
        assertEquals(data(31, 12, 2026), etiqueta.validade?.valor)
        assertEquals(Confianca.MEDIA, etiqueta.validade?.confianca)
        assertTrue(etiqueta.avisos.any { it.contains("letra no lugar de numero") })
    }

    @Test
    fun `texto em branco devolve leitura vazia com aviso, sem estourar`() {
        val etiqueta = ler("   ")
        assertNull(etiqueta.validade)
        assertNull(etiqueta.lote)
        assertEquals(listOf("Nao consegui ler nada na foto"), etiqueta.avisos)
    }

    @Test
    fun `texto sem nada reconhecivel pede os dois campos`() {
        val etiqueta = ler("CHOCOLATE MEIO AMARGO INGREDIENTES ACUCAR CACAU")
        assertEquals(
            listOf(CampoEtiqueta.VALIDADE, CampoEtiqueta.LOTE),
            etiqueta.camposEssenciaisNaoLidos,
        )
        assertTrue(etiqueta.precisaDeAjuda)
    }

    // ----------------------------------------------------------------- lote

    @Test
    fun `le LOTE com dois pontos`() {
        assertEquals("2504A", ler("LOTE: 2504A").lote?.valor)
    }

    @Test
    fun `le L abreviado`() {
        assertEquals("2504A", ler("L 2504A").lote?.valor)
    }

    @Test
    fun `le LOTE N com simbolo de numero`() {
        assertEquals("A1234", ler("LOTE Nº A1234").lote?.valor)
    }

    @Test
    fun `L dentro de outra palavra nao vira lote`() {
        assertNull(ler("CHOCOLATE AO LEITE").lote)
    }

    @Test
    fun `o lote nao rouba a data que esta logo depois`() {
        val etiqueta = ler("VAL 31/12/2026 LOTE 2504A")
        assertEquals(data(31, 12, 2026), etiqueta.validade?.valor)
        assertEquals("2504A", etiqueta.lote?.valor)
    }

    @Test
    fun `etiqueta completa de verdade sai inteira`() {
        val etiqueta = ler(
            """
            CHOCOLATE AO LEITE 500G
            FAB: 01/03/2026
            VAL: 01/03/2027
            LOTE: L2504A
            """.trimIndent()
        )
        assertEquals(data(1, 3, 2027), etiqueta.validade?.valor)
        assertEquals(data(1, 3, 2026), etiqueta.fabricacao?.valor)
        assertEquals("L2504A", etiqueta.lote?.valor)
        assertEquals(UnidadeMedida.GRAMA, etiqueta.peso?.valor?.unidade)
        assertTrue(etiqueta.leuTudoQuePrecisa)
        assertFalse(etiqueta.precisaDeAjuda)
    }

    // ----------------------------------------------------------------- peso

    @Test
    fun `le peso em gramas e em quilos`() {
        assertEquals(UnidadeMedida.GRAMA, ler("PESO LIQUIDO 500 G").peso?.valor?.unidade)
        val quilo = ler("1,5 KG")
        assertEquals(UnidadeMedida.QUILO, quilo.peso?.valor?.unidade)
        assertEquals("1,5", quilo.peso?.valor?.quantidade?.formatar())
    }

    @Test
    fun `mililitro vira litro`() {
        val etiqueta = ler("300 ML")
        assertEquals(UnidadeMedida.LITRO, etiqueta.peso?.valor?.unidade)
        assertEquals("0,3", etiqueta.peso?.valor?.quantidade?.formatar())
    }

    // ------------------------------------------------------- codigo de barras

    @Test
    fun `sequencia de treze digitos com digito verificador valido vira codigo de barras`() {
        val etiqueta = ler("7891000315507")
        assertEquals("7891000315507", etiqueta.codigoBarras?.valor)
    }

    @Test
    fun `sequencia de treze digitos com digito verificador errado nao vira codigo de barras`() {
        assertNull(ler("7891000315508").codigoBarras)
    }

    @Test
    fun `digito verificador confere para EAN-8 e recusa tamanho invalido`() {
        assertTrue(InterpretadorDeEtiqueta.digitoVerificadorConfere("40170725"))
        assertFalse(InterpretadorDeEtiqueta.digitoVerificadorConfere("1234567"))
        assertFalse(InterpretadorDeEtiqueta.digitoVerificadorConfere("789100031550A"))
    }

    // ------------------------------------------------------------ acentuacao

    @Test
    fun `acento e caixa baixa nao atrapalham`() {
        val etiqueta = ler("Fabricação: 01/01/2026  Validade: 01/01/2027  Lote: 77B")
        assertEquals(data(1, 1, 2027), etiqueta.validade?.valor)
        assertEquals(data(1, 1, 2026), etiqueta.fabricacao?.valor)
        assertEquals("77B", etiqueta.lote?.valor)
    }

    @Test
    fun `expandir ano escolhe o seculo mais proximo`() {
        val hoje = LocalDate.of(2026, 9, 16)
        assertEquals(2026, InterpretadorDeEtiqueta.expandirAno(26, hoje, futuro = true))
        assertEquals(2027, InterpretadorDeEtiqueta.expandirAno(27, hoje, futuro = true))
        assertEquals(2125, InterpretadorDeEtiqueta.expandirAno(25, hoje, futuro = true))
        assertEquals(2025, InterpretadorDeEtiqueta.expandirAno(25, hoje, futuro = false))
        assertEquals(1999, InterpretadorDeEtiqueta.expandirAno(99, hoje, futuro = false))
        assertEquals(2026, InterpretadorDeEtiqueta.expandirAno(2026, hoje, futuro = true))
    }

    @Test
    fun `a descricao curta resume o que foi lido`() {
        val etiqueta = ler("VAL 31/12/2026 LOTE 2504A")
        assertNotNull(etiqueta.validade)
        assertTrue(etiqueta.descricaoCurta().contains("lote 2504A"))
    }
}
