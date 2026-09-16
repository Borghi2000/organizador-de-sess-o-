@file:UseSerializers(SerializadorData::class)

package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

@Serializable
enum class StatusDivergencia(val rotulo: String) {
    ABERTA("Aberta"),
    RESOLVIDA("Resolvida"),
}

/**
 * Contagem fisica diferente do esperado. Nasce aberta e so some quando alguem decide o que fazer:
 * a contagem sozinha nunca corrige o estoque.
 */
@Serializable
data class Divergencia(
    val id: String,
    val data: LocalDate,
    val produtoCodigo: String,
    val loteId: String,
    val codigoLote: String,
    val localizacao: Localizacao,
    val quantidadeEsperada: Quantidade,
    val quantidadeContada: Quantidade,
    val observacao: String = "",
    val status: StatusDivergencia = StatusDivergencia.ABERTA,
    val dataResolucao: LocalDate? = null,
    val observacaoResolucao: String = "",
) {
    val diferenca: Quantidade get() = quantidadeContada - quantidadeEsperada
    val ehFalta: Boolean get() = diferenca.ehNegativa
    val ehSobra: Boolean get() = diferenca.ehPositiva
    val estaAberta: Boolean get() = status == StatusDivergencia.ABERTA

    fun descricao(unidade: UnidadeMedida): String {
        val rotulo = if (ehFalta) "faltando" else "sobrando"
        return "Lote $codigoLote em ${localizacao.nome}: $rotulo ${diferenca.valorAbsoluto().formatar(unidade)} " +
            "(esperado ${quantidadeEsperada.formatar(unidade)}, contado ${quantidadeContada.formatar(unidade)})"
    }
}
