package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable

/**
 * O tipo do local e o que separa estoque exposto de estoque no deposito, e o que tira o produto
 * segregado da conta de disponivel para venda.
 */
@Serializable
enum class TipoLocal(val rotulo: String) {
    EXPOSICAO("Exposicao"),
    DEPOSITO("Deposito"),
    SEGREGACAO("Segregacao"),
}

@Serializable
data class Localizacao(val nome: String, val tipo: TipoLocal) {

    fun descricao(): String = "$nome (${tipo.rotulo})"

    companion object {
        val SEGREGACAO_PADRAO = Localizacao("Area de segregacao", TipoLocal.SEGREGACAO)
    }
}
