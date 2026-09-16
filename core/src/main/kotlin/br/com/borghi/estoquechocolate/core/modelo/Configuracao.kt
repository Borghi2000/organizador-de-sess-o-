package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable

@Serializable
data class Configuracao(
    /** Depois de quantos dias sem contagem um lote entra no cartao "sem conferencia recente". */
    val diasSemConferencia: Int = 7,
    val locais: List<Localizacao> = LOCAIS_PADRAO,
) {
    companion object {
        val LOCAIS_PADRAO = listOf(
            Localizacao("Gondola", TipoLocal.EXPOSICAO),
            Localizacao("Vitrine", TipoLocal.EXPOSICAO),
            Localizacao("Deposito", TipoLocal.DEPOSITO),
            Localizacao.SEGREGACAO_PADRAO,
        )
    }
}
