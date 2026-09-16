package br.com.borghi.estoquechocolate.core

import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.PosicaoEstoque
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import java.time.LocalDate
import java.time.LocalDateTime

val HOJE: LocalDate = LocalDate.of(2026, 9, 16)
val AGORA: LocalDateTime = LocalDateTime.of(2026, 9, 16, 10, 30)

val GONDOLA = Localizacao("Gondola", TipoLocal.EXPOSICAO)
val VITRINE = Localizacao("Vitrine", TipoLocal.EXPOSICAO)
val DEPOSITO = Localizacao("Deposito", TipoLocal.DEPOSITO)
val SEGREGACAO = Localizacao.SEGREGACAO_PADRAO

fun q(valor: Int) = Quantidade.deInteiro(valor)

fun produto(
    codigo: String = "CH001",
    nome: String = "Bombom ao leite",
    minimo: Int = 10,
    maximo: Int = 100,
    unidade: UnidadeMedida = UnidadeMedida.UNIDADE,
) = Produto(
    codigo = codigo,
    nome = nome,
    categoria = "Bombons",
    unidade = unidade,
    estoqueMinimo = q(minimo),
    estoqueMaximo = q(maximo),
    localPadrao = GONDOLA,
)

fun lote(
    id: String,
    codigoLote: String = id,
    produtoCodigo: String = "CH001",
    validade: LocalDate = HOJE.plusDays(90),
    posicoes: List<Pair<Localizacao, Int>> = listOf(GONDOLA to 10),
    recebida: Int = posicoes.sumOf { it.second },
    segregado: Boolean = false,
    motivo: MotivoSegregacao? = null,
    ultimaConferencia: LocalDate? = HOJE,
) = Lote(
    id = id,
    produtoCodigo = produtoCodigo,
    codigoLote = codigoLote,
    validade = validade,
    quantidadeRecebida = q(recebida),
    posicoes = posicoes.map { PosicaoEstoque(it.first, q(it.second)) },
    dataUltimaConferencia = ultimaConferencia,
    segregado = segregado,
    motivoSegregacao = motivo,
)

/** Gerador de id previsivel, para os testes poderem afirmar exatamente o que foi criado. */
class IdSequencial(private val prefixo: String = "id") {
    private var contador = 0
    val gerar: () -> String = { "$prefixo-${++contador}" }
}
