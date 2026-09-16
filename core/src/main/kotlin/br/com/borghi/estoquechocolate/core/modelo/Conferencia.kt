@file:UseSerializers(SerializadorData::class)

package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

@Serializable
enum class StatusConferencia(val rotulo: String) {
    ABERTA("Aberta"),
    FECHADA("Fechada"),
}

@Serializable
data class ItemConferencia(
    val loteId: String,
    val codigoLote: String,
    val produtoCodigo: String,
    val localizacao: Localizacao,
    val quantidadeEsperada: Quantidade,
    val quantidadeContada: Quantidade,
    val divergenciaId: String? = null,
) {
    val conferiu: Boolean get() = divergenciaId == null
}

@Serializable
data class Conferencia(
    val id: String,
    val data: LocalDate,
    val itens: List<ItemConferencia> = emptyList(),
    val status: StatusConferencia = StatusConferencia.ABERTA,
    val observacao: String = "",
) {
    val totalConferido: Int get() = itens.size
    val totalComDivergencia: Int get() = itens.count { !it.conferiu }
    val lotesConferidos: Set<String> get() = itens.map { it.loteId }.toSet()
}
