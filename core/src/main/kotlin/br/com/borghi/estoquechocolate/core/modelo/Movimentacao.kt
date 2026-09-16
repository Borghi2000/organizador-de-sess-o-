@file:UseSerializers(SerializadorDataHora::class)

package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDateTime

@Serializable
enum class EfeitoEstoque {
    /** Entra estoque novo na secao. */
    AUMENTA,

    /** Sai estoque da secao de vez. */
    DIMINUI,

    /** So muda de lugar dentro da secao; o total nao muda. */
    INTERNO,

    /** Corrige o registro para bater com a contagem fisica. */
    AJUSTA,
}

@Serializable
enum class TipoMovimentacao(
    val rotulo: String,
    val efeito: EfeitoEstoque,
    val exigeObservacao: Boolean,
) {
    ENTRADA("Entrada", EfeitoEstoque.AUMENTA, false),
    REPOSICAO("Reposicao", EfeitoEstoque.INTERNO, false),
    SAIDA("Saida", EfeitoEstoque.DIMINUI, false),
    PERDA_VENCIMENTO("Perda por vencimento", EfeitoEstoque.DIMINUI, true),
    PERDA_AVARIA("Perda por avaria", EfeitoEstoque.DIMINUI, true),
    DEGUSTACAO("Degustacao", EfeitoEstoque.DIMINUI, false),
    TRANSFERENCIA("Transferencia", EfeitoEstoque.INTERNO, false),
    AJUSTE_INVENTARIO("Ajuste de inventario", EfeitoEstoque.AJUSTA, true);

    /**
     * Degustacao tambem tira produto do estoque, mas e uma decisao comercial, nao uma perda.
     * Somar as duas coisas esconderia o numero que interessa: quanto estragou.
     */
    val ehPerda: Boolean get() = this == PERDA_VENCIMENTO || this == PERDA_AVARIA

    val exigeOrigem: Boolean
        get() = efeito == EfeitoEstoque.DIMINUI || efeito == EfeitoEstoque.INTERNO

    val exigeDestino: Boolean
        get() = efeito == EfeitoEstoque.AUMENTA || efeito == EfeitoEstoque.INTERNO
}

/**
 * Registro imutavel de tudo que aconteceu. Sempre de um unico lote: nunca existe movimentacao
 * com quantidade somada de lotes diferentes.
 */
@Serializable
data class Movimentacao(
    val id: String,
    val dataHora: LocalDateTime,
    val produtoCodigo: String,
    val loteId: String,
    val codigoLote: String,
    val tipo: TipoMovimentacao,
    val quantidade: Quantidade,
    val localOrigem: Localizacao? = null,
    val localDestino: Localizacao? = null,
    val observacao: String = "",
    val divergenciaId: String? = null,
)
