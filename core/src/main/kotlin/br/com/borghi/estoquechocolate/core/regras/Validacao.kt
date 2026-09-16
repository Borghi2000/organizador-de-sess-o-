package br.com.borghi.estoquechocolate.core.regras

import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import java.time.LocalDate

data class CampoFaltante(val campo: String, val mensagem: String)

/**
 * Resultado da conferencia de um formulario antes de gravar.
 *
 * [faltantes] bloqueia o salvamento; [avisos] apenas alerta. O app nunca completa campo sozinho:
 * se falta informacao, ele pede.
 */
data class ResultadoValidacao(
    val faltantes: List<CampoFaltante> = emptyList(),
    val avisos: List<String> = emptyList(),
) {
    val valido: Boolean get() = faltantes.isEmpty()
    val resumoDoQueFalta: String get() = faltantes.joinToString("; ") { it.mensagem }
}

/** Uma linha da tela de confirmacao que aparece antes de gravar. */
data class LinhaConfirmacao(val rotulo: String, val valor: String)

private fun obrigatorio(
    valor: String?,
    campo: String,
    mensagem: String,
): CampoFaltante? = if (valor.isNullOrBlank()) CampoFaltante(campo, mensagem) else null

data class RascunhoProduto(
    val codigo: String = "",
    val nome: String = "",
    val categoria: String = "",
    val unidade: UnidadeMedida? = null,
    val estoqueMinimo: String = "",
    val estoqueMaximo: String = "",
    val localPadrao: Localizacao? = null,
    val observacoes: String = "",
) {
    fun validar(codigosExistentes: Set<String> = emptySet()): ResultadoValidacao {
        val faltantes = buildList {
            obrigatorio(codigo, "codigo", "Informe o codigo do produto")?.let { add(it) }
            obrigatorio(nome, "nome", "Informe o nome do produto")?.let { add(it) }
            obrigatorio(categoria, "categoria", "Informe a categoria")?.let { add(it) }
            if (unidade == null) add(CampoFaltante("unidade", "Escolha a unidade de medida"))
            if (localPadrao == null) add(CampoFaltante("localPadrao", "Escolha o local padrao"))
            val minimo = Quantidade.deTexto(estoqueMinimo)
            val maximo = Quantidade.deTexto(estoqueMaximo)
            if (minimo == null) add(CampoFaltante("estoqueMinimo", "Informe o estoque minimo"))
            if (maximo == null) add(CampoFaltante("estoqueMaximo", "Informe o estoque maximo"))
            if (minimo != null && minimo.ehNegativa) {
                add(CampoFaltante("estoqueMinimo", "O estoque minimo nao pode ser negativo"))
            }
            if (minimo != null && maximo != null && maximo < minimo) {
                add(CampoFaltante("estoqueMaximo", "O estoque maximo nao pode ser menor que o minimo"))
            }
            if (codigo.isNotBlank() && codigo.trim() in codigosExistentes) {
                add(CampoFaltante("codigo", "Ja existe um produto com o codigo ${codigo.trim()}"))
            }
        }
        return ResultadoValidacao(faltantes)
    }

    fun paraConfirmacao(): List<LinhaConfirmacao> = listOf(
        LinhaConfirmacao("Codigo", codigo),
        LinhaConfirmacao("Nome", nome),
        LinhaConfirmacao("Categoria", categoria),
        LinhaConfirmacao("Unidade", unidade?.rotulo ?: "-"),
        LinhaConfirmacao("Estoque minimo", estoqueMinimo),
        LinhaConfirmacao("Estoque maximo", estoqueMaximo),
        LinhaConfirmacao("Local padrao", localPadrao?.descricao() ?: "-"),
        LinhaConfirmacao("Observacoes", observacoes.ifBlank { "-" }),
    )
}

data class RascunhoEntrada(
    val produtoCodigo: String = "",
    val codigoLote: String = "",
    val validade: LocalDate? = null,
    val quantidade: String = "",
    val localizacao: Localizacao? = null,
    val observacoes: String = "",
) {
    fun validar(hoje: LocalDate): ResultadoValidacao {
        val quantidadeLida = Quantidade.deTexto(quantidade)
        val faltantes = buildList {
            obrigatorio(produtoCodigo, "produto", "Escolha o produto")?.let { add(it) }
            obrigatorio(codigoLote, "codigoLote", "Informe o codigo do lote")?.let { add(it) }
            if (validade == null) add(CampoFaltante("validade", "Informe a data de validade"))
            if (localizacao == null) add(CampoFaltante("localizacao", "Escolha onde o produto vai ficar"))
            if (quantidadeLida == null) {
                add(CampoFaltante("quantidade", "Informe a quantidade recebida"))
            } else if (!quantidadeLida.ehPositiva) {
                add(CampoFaltante("quantidade", "A quantidade recebida precisa ser maior que zero"))
            }
        }
        val avisos = buildList {
            if (validade != null && validade.isBefore(hoje)) {
                add("Esta validade ja passou. Confira a data antes de gravar; o lote entrara como vencido.")
            } else if (validade != null) {
                val classificacao = ClassificacaoValidade.de(validade, hoje)
                if (classificacao == ClassificacaoValidade.CRITICO) {
                    add("Lote chegando com validade critica: ${ClassificacaoValidade.descreverPrazo(validade, hoje)}.")
                }
            }
        }
        return ResultadoValidacao(faltantes, avisos)
    }

    fun paraConfirmacao(nomeProduto: String, unidade: UnidadeMedida?): List<LinhaConfirmacao> = listOf(
        LinhaConfirmacao("Produto", nomeProduto.ifBlank { produtoCodigo }),
        LinhaConfirmacao("Lote", codigoLote),
        LinhaConfirmacao("Validade", validade?.let { formatarData(it) } ?: "-"),
        LinhaConfirmacao(
            "Quantidade",
            Quantidade.deTexto(quantidade)?.let { q -> unidade?.let { q.formatar(it) } ?: q.formatar() } ?: "-",
        ),
        LinhaConfirmacao("Local", localizacao?.descricao() ?: "-"),
        LinhaConfirmacao("Observacoes", observacoes.ifBlank { "-" }),
    )
}

data class RascunhoMovimentacao(
    val tipo: TipoMovimentacao? = null,
    val loteId: String = "",
    val quantidade: String = "",
    val localOrigem: Localizacao? = null,
    val localDestino: Localizacao? = null,
    val observacao: String = "",
) {
    fun validar(): ResultadoValidacao {
        val quantidadeLida = Quantidade.deTexto(quantidade)
        val faltantes = buildList {
            if (tipo == null) add(CampoFaltante("tipo", "Escolha o tipo de movimentacao"))
            obrigatorio(loteId, "lote", "Escolha o lote")?.let { add(it) }
            if (quantidadeLida == null) {
                add(CampoFaltante("quantidade", "Informe a quantidade"))
            } else if (!quantidadeLida.ehPositiva) {
                add(CampoFaltante("quantidade", "A quantidade precisa ser maior que zero"))
            }
            if (tipo != null && tipo.exigeOrigem && localOrigem == null) {
                add(CampoFaltante("localOrigem", "Informe de qual local o produto saiu"))
            }
            if (tipo != null && tipo.exigeDestino && localDestino == null) {
                add(CampoFaltante("localDestino", "Informe para qual local o produto foi"))
            }
            if (tipo != null && tipo.exigeObservacao && observacao.isBlank()) {
                add(CampoFaltante("observacao", "Descreva o motivo: ${tipo.rotulo} exige justificativa"))
            }
            if (tipo == TipoMovimentacao.TRANSFERENCIA && localOrigem != null && localOrigem == localDestino) {
                add(CampoFaltante("localDestino", "Origem e destino nao podem ser o mesmo local"))
            }
        }
        return ResultadoValidacao(faltantes)
    }

    fun paraConfirmacao(
        descricaoLote: String,
        unidade: UnidadeMedida?,
    ): List<LinhaConfirmacao> = buildList {
        add(LinhaConfirmacao("Movimentacao", tipo?.rotulo ?: "-"))
        add(LinhaConfirmacao("Lote", descricaoLote))
        add(
            LinhaConfirmacao(
                "Quantidade",
                Quantidade.deTexto(quantidade)?.let { q -> unidade?.let { q.formatar(it) } ?: q.formatar() } ?: "-",
            ),
        )
        if (tipo?.exigeOrigem == true) add(LinhaConfirmacao("De", localOrigem?.descricao() ?: "-"))
        if (tipo?.exigeDestino == true) add(LinhaConfirmacao("Para", localDestino?.descricao() ?: "-"))
        add(LinhaConfirmacao("Observacao", observacao.ifBlank { "-" }))
    }
}

data class RascunhoContagem(
    val loteId: String = "",
    val localizacao: Localizacao? = null,
    val quantidadeContada: String = "",
    val observacao: String = "",
) {
    fun validar(): ResultadoValidacao {
        val quantidadeLida = Quantidade.deTexto(quantidadeContada)
        val faltantes = buildList {
            obrigatorio(loteId, "lote", "Escolha o lote conferido")?.let { add(it) }
            if (localizacao == null) add(CampoFaltante("localizacao", "Informe o local conferido"))
            if (quantidadeLida == null) {
                add(CampoFaltante("quantidadeContada", "Informe a quantidade contada"))
            } else if (quantidadeLida.ehNegativa) {
                add(CampoFaltante("quantidadeContada", "A quantidade contada nao pode ser negativa"))
            }
        }
        return ResultadoValidacao(faltantes)
    }
}

internal fun formatarData(data: LocalDate): String =
    "%02d/%02d/%04d".format(data.dayOfMonth, data.monthValue, data.year)
