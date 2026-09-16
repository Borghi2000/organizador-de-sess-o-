package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable

@Serializable
data class Produto(
    val codigo: String,
    val nome: String,
    val categoria: String,
    val unidade: UnidadeMedida,
    val estoqueMinimo: Quantidade,
    val estoqueMaximo: Quantidade,
    val localPadrao: Localizacao,
    val observacoes: String = "",
)
