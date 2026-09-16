package br.com.borghi.estoquechocolate.core.backup

import br.com.borghi.estoquechocolate.core.modelo.Configuracao
import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.PosicaoEstoque
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Dados para experimentar o app antes de cadastrar o estoque real.
 *
 * Sao inventados de proposito e ficam atras de um botao com esse nome; nenhum deles aparece no
 * banco por conta propria. As validades sao calculadas a partir de hoje para que cada faixa do
 * painel apareca preenchida.
 */
object DadosDeExemplo {

    private val gondola = Localizacao("Gondola", TipoLocal.EXPOSICAO)
    private val deposito = Localizacao("Deposito", TipoLocal.DEPOSITO)
    private val segregacao = Localizacao.SEGREGACAO_PADRAO

    fun gerar(hoje: LocalDate): ArquivoBackup {
        val produtos = listOf(
            produto("CH001", "Bombom ao leite avulso", "Bombons", UnidadeMedida.UNIDADE, 40, 200, gondola),
            produto("CH002", "Barra 70% cacau 100g", "Barras", UnidadeMedida.UNIDADE, 12, 60, gondola),
            produto("CH003", "Trufa de maracuja", "Trufas", UnidadeMedida.UNIDADE, 20, 80, gondola),
            produto("CH004", "Caixa presente 250g", "Presentes", UnidadeMedida.CAIXA, 5, 25, gondola),
            produto("CH005", "Chocolate granel ao leite", "Granel", UnidadeMedida.QUILO, 3, 15, deposito),
        )

        val lotes = listOf(
            // Vencido e ainda solto na gondola: e o pior caso, e o painel tem que gritar.
            lote("L1", "CH001", "A2405", hoje.minusDays(4), 120, listOf(gondola to 35)),
            // Critico: vence em 3 dias.
            lote("L2", "CH002", "B2501", hoje.plusDays(3), 48, listOf(gondola to 9, deposito to 6)),
            // Urgente: vence em 12 dias.
            lote("L3", "CH003", "C2502", hoje.plusDays(12), 60, listOf(gondola to 24)),
            // Atencao: 25 dias, e abaixo do minimo.
            lote("L4", "CH004", "D2503", hoje.plusDays(25), 20, listOf(gondola to 3)),
            // Normal, com estoque no deposito para exercitar a reposicao.
            lote("L5", "CH001", "A2406", hoje.plusDays(90), 200, listOf(deposito to 150)),
            // Segregado por avaria: existe no fisico, mas nao pode ser vendido.
            lote(
                "L6", "CH005", "E2504", hoje.plusDays(45), 8,
                listOf(segregacao to 4), segregado = true, motivo = MotivoSegregacao.AVARIA,
            ),
        ).map { it.copy(dataUltimaConferencia = if (it.id == "L1") null else hoje.minusDays(12)) }

        val agora = LocalDateTime.of(hoje, java.time.LocalTime.of(9, 0))
        val movimentacoes = listOf(
            movimentacao("M1", agora.minusDays(20), "CH001", "L1", "A2405", TipoMovimentacao.ENTRADA, 120, destino = gondola),
            movimentacao("M2", agora.minusDays(10), "CH002", "L2", "B2501", TipoMovimentacao.ENTRADA, 48, destino = deposito),
            movimentacao("M3", agora.minusDays(9), "CH002", "L2", "B2501", TipoMovimentacao.REPOSICAO, 12, origem = deposito, destino = gondola),
            movimentacao("M4", agora.minusDays(5), "CH003", "L3", "C2502", TipoMovimentacao.SAIDA, 36, origem = gondola),
            movimentacao(
                "M5", agora.minusDays(3), "CH005", "L6", "E2504", TipoMovimentacao.PERDA_AVARIA, 4,
                origem = deposito, observacao = "Embalagem rasgada no transporte",
            ),
            movimentacao(
                "M6", agora.minusDays(1), "CH001", "L1", "A2405", TipoMovimentacao.DEGUSTACAO, 5,
                origem = gondola, observacao = "Degustacao de sabado",
            ),
        )

        val divergencias = listOf(
            Divergencia(
                id = "D1",
                data = hoje.minusDays(2),
                produtoCodigo = "CH003",
                loteId = "L3",
                codigoLote = "C2502",
                localizacao = gondola,
                quantidadeEsperada = Quantidade.deInteiro(26),
                quantidadeContada = Quantidade.deInteiro(24),
                observacao = "Contagem da tarde",
            ),
        )

        return ArquivoBackup(
            geradoEm = agora,
            produtos = produtos,
            lotes = lotes,
            movimentacoes = movimentacoes,
            divergencias = divergencias,
            conferencias = emptyList(),
            configuracao = Configuracao(),
        )
    }

    private fun produto(
        codigo: String,
        nome: String,
        categoria: String,
        unidade: UnidadeMedida,
        minimo: Int,
        maximo: Int,
        local: Localizacao,
    ) = Produto(
        codigo = codigo,
        nome = nome,
        categoria = categoria,
        unidade = unidade,
        estoqueMinimo = Quantidade.deInteiro(minimo),
        estoqueMaximo = Quantidade.deInteiro(maximo),
        localPadrao = local,
        observacoes = "",
    )

    private fun lote(
        id: String,
        produtoCodigo: String,
        codigoLote: String,
        validade: LocalDate,
        recebida: Int,
        posicoes: List<Pair<Localizacao, Int>>,
        segregado: Boolean = false,
        motivo: MotivoSegregacao? = null,
    ) = Lote(
        id = id,
        produtoCodigo = produtoCodigo,
        codigoLote = codigoLote,
        validade = validade,
        quantidadeRecebida = Quantidade.deInteiro(recebida),
        posicoes = posicoes.map { PosicaoEstoque(it.first, Quantidade.deInteiro(it.second)) },
        segregado = segregado,
        motivoSegregacao = motivo,
    )

    private fun movimentacao(
        id: String,
        dataHora: LocalDateTime,
        produtoCodigo: String,
        loteId: String,
        codigoLote: String,
        tipo: TipoMovimentacao,
        quantidade: Int,
        origem: Localizacao? = null,
        destino: Localizacao? = null,
        observacao: String = "",
    ) = Movimentacao(
        id = id,
        dataHora = dataHora,
        produtoCodigo = produtoCodigo,
        loteId = loteId,
        codigoLote = codigoLote,
        tipo = tipo,
        quantidade = Quantidade.deInteiro(quantidade),
        localOrigem = origem,
        localDestino = destino,
        observacao = observacao,
    )
}
