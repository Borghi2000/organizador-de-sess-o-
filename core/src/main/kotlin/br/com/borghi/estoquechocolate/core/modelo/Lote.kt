@file:UseSerializers(SerializadorData::class)

package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

@Serializable
enum class MotivoSegregacao(val rotulo: String) {
    VENCIDO("Vencido"),
    AVARIA("Avaria"),
    OUTRO("Outro"),
}

/**
 * Quanto deste lote existe em um local especifico.
 *
 * E esta tabela que permite responder "quanto tem na gondola" e "quanto tem no deposito" sem
 * nunca somar lotes diferentes: toda posicao pertence a um unico lote.
 */
@Serializable
data class PosicaoEstoque(val localizacao: Localizacao, val quantidade: Quantidade)

@Serializable
data class Lote(
    val id: String,
    val produtoCodigo: String,
    val codigoLote: String,
    val validade: LocalDate,
    val quantidadeRecebida: Quantidade,
    val posicoes: List<PosicaoEstoque> = emptyList(),
    val dataUltimaConferencia: LocalDate? = null,
    val observacoes: String = "",
    val segregado: Boolean = false,
    val motivoSegregacao: MotivoSegregacao? = null,
    /**
     * Foto da etiqueta lida na entrada. Seis meses depois, numa divergencia, da para abrir a foto
     * e conferir a etiqueta original em vez de discutir de memoria.
     */
    val fotoEtiqueta: String = "",
) {

    /** Soma das posicoes. Nao e guardada separadamente, entao nao tem como divergir do detalhe. */
    val quantidadeAtual: Quantidade get() = Quantidade.soma(posicoes.map { it.quantidade })

    fun quantidadeEm(localizacao: Localizacao): Quantidade =
        Quantidade.soma(posicoes.filter { it.localizacao == localizacao }.map { it.quantidade })

    fun quantidadeEmTipo(tipo: TipoLocal): Quantidade =
        Quantidade.soma(posicoes.filter { it.localizacao.tipo == tipo }.map { it.quantidade })

    fun estaVencido(hoje: LocalDate): Boolean = validade.isBefore(hoje)

    /**
     * Vencido para de ser vendavel no instante em que vence, independente de alguem ja ter ido
     * ate a gondola tirar o produto. A marcacao [segregado] registra o ato fisico; esta conta
     * nao espera por ele.
     */
    fun disponivelParaVenda(hoje: LocalDate): Boolean = !segregado && !estaVencido(hoje)

    fun quantidadeDisponivelParaVenda(hoje: LocalDate): Quantidade =
        if (!disponivelParaVenda(hoje)) Quantidade.ZERO
        else Quantidade.soma(posicoes.filter { it.localizacao.tipo != TipoLocal.SEGREGACAO }.map { it.quantidade })

    fun semConferenciaDesde(hoje: LocalDate, dias: Int): Boolean {
        val ultima = dataUltimaConferencia ?: return true
        return ultima.isBefore(hoje.minusDays(dias.toLong()))
    }

    /** Aplica uma variacao a um local, mantendo a lista de posicoes limpa de zeros. */
    fun comVariacao(localizacao: Localizacao, variacao: Quantidade): Lote {
        val atual = quantidadeEm(localizacao)
        val nova = atual + variacao
        val semLocal = posicoes.filterNot { it.localizacao == localizacao }
        val posicoes = if (nova.ehZero) semLocal else semLocal + PosicaoEstoque(localizacao, nova)
        return copy(posicoes = posicoes.sortedBy { it.localizacao.nome })
    }
}
