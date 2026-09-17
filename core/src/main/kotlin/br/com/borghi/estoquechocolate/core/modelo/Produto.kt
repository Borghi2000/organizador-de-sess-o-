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
    /** EAN lido da embalagem. E o que a camera usa para achar o produto num toque. */
    val codigoBarras: String = "",
    /** Nome do arquivo da foto, guardado no aparelho. Vazio quando nao ha foto. */
    val foto: String = "",
)
