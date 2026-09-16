package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.backup.ArquivoBackup
import br.com.borghi.estoquechocolate.core.backup.Backup
import br.com.borghi.estoquechocolate.core.backup.DadosDeExemplo
import br.com.borghi.estoquechocolate.core.backup.ResultadoImportacao
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.painel.MontadorPainel
import br.com.borghi.estoquechocolate.core.painel.TipoCartao
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupTest {

    @Test
    fun `exportar e importar devolve exatamente os mesmos dados`() {
        val original = DadosDeExemplo.gerar(HOJE)

        val texto = Backup.exportar(original)
        val volta = assertIs<ResultadoImportacao.Sucesso>(Backup.importar(texto)).arquivo

        assertEquals(original, volta)
    }

    @Test
    fun `fracoes e datas sobrevivem ao backup sem perder precisao`() {
        val original = DadosDeExemplo.gerar(HOJE).let { arquivo ->
            arquivo.copy(
                lotes = arquivo.lotes.map { lote ->
                    lote.copy(posicoes = lote.posicoes.map { it.copy(quantidade = Quantidade.deTexto("1,255")!!) })
                },
            )
        }

        val volta = assertIs<ResultadoImportacao.Sucesso>(Backup.importar(Backup.exportar(original))).arquivo

        assertEquals("1,255", volta.lotes.first().posicoes.first().quantidade.formatar())
        assertEquals(original.lotes.first().validade, volta.lotes.first().validade)
        assertEquals(original.geradoEm, volta.geradoEm)
    }

    @Test
    fun `arquivo corrompido nao importa nada pela metade`() {
        val falha = assertIs<ResultadoImportacao.Falha>(Backup.importar("{isso nao e json"))
        assertTrue(falha.mensagem.contains("invalido"))
    }

    @Test
    fun `backup de versao futura e recusado com explicacao`() {
        val futuro = Backup.exportar(DadosDeExemplo.gerar(HOJE).copy(versao = ArquivoBackup.VERSAO_ATUAL + 1))

        val falha = assertIs<ResultadoImportacao.Falha>(Backup.importar(futuro))
        assertTrue(falha.mensagem.contains("versao mais nova"))
    }

    @Test
    fun `o backup e texto legivel, nao um binario opaco`() {
        val texto = Backup.exportar(DadosDeExemplo.gerar(HOJE))

        assertTrue(texto.contains("\"codigo\": \"CH001\""))
        assertTrue(texto.contains(HOJE.plusDays(90).toString()))
    }

    @Test
    fun `os dados de exemplo preenchem todas as faixas do painel para dar o que testar`() {
        val exemplo = DadosDeExemplo.gerar(HOJE)

        val painel = MontadorPainel.montar(
            exemplo.produtos, exemplo.lotes, exemplo.movimentacoes, exemplo.divergencias, HOJE, exemplo.configuracao,
        )

        assertEquals(1, painel.cartao(TipoCartao.VENCIDOS).quantidade)
        assertEquals(1, painel.cartao(TipoCartao.VENCE_EM_7_DIAS).quantidade)
        assertEquals(1, painel.cartao(TipoCartao.VENCE_EM_8_A_15_DIAS).quantidade)
        assertEquals(1, painel.cartao(TipoCartao.DIVERGENCIAS_ABERTAS).quantidade)
        assertEquals(1, painel.cartao(TipoCartao.SEM_ESTOQUE_PARA_VENDA).quantidade)
        assertTrue(painel.cartao(TipoCartao.ABAIXO_DO_MINIMO).quantidade > 0)
        assertTrue(painel.cartao(TipoCartao.PERDAS_DO_MES).quantidade > 0)
        assertTrue(painel.cartao(TipoCartao.SEM_CONFERENCIA_RECENTE).quantidade > 0)
    }

    @Test
    fun `os dados de exemplo acompanham a data de hoje, em vez de envelhecer no codigo`() {
        val outroDia = HOJE.plusYears(1)
        val exemplo = DadosDeExemplo.gerar(outroDia)

        val vencido = exemplo.lotes.first { it.id == "L1" }
        assertEquals(ClassificacaoValidade.VENCIDO, ClassificacaoValidade.de(vencido.validade, outroDia))
    }
}
