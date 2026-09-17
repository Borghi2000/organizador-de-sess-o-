package br.com.borghi.estoquechocolate.core.leitura

import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import java.time.LocalDate

/**
 * O quanto o interpretador confia no que leu.
 *
 * BAIXA nao e "provavelmente certo": e "nao preencha sozinho". Uma validade errada e pior que uma
 * validade vazia, porque contamina a ordem do PVPS e o painel inteiro sem ninguem perceber.
 */
enum class Confianca(val rotulo: String) {
    ALTA("Lido com rotulo na etiqueta"),
    MEDIA("Lido, mas sem rotulo explicito"),
    BAIXA("Duvidoso: confira antes de salvar"),
}

enum class CampoEtiqueta(val rotulo: String) {
    VALIDADE("Validade"),
    LOTE("Codigo do lote"),
    FABRICACAO("Fabricacao"),
    PESO("Peso"),
    CODIGO_BARRAS("Codigo de barras"),
}

/** Um campo lido da etiqueta, junto com o pedaco de texto de onde saiu. */
data class Leitura<out T>(val valor: T, val trecho: String, val confianca: Confianca) {

    /** Confiavel o bastante para preencher o rascunho sozinho. */
    val confiavel: Boolean get() = confianca != Confianca.BAIXA

    fun <R> mapear(bloco: (T) -> R): Leitura<R> = Leitura(bloco(valor), trecho, confianca)

    fun rebaixar(): Leitura<T> = when (confianca) {
        Confianca.ALTA -> copy(confianca = Confianca.MEDIA)
        else -> copy(confianca = Confianca.BAIXA)
    }
}

data class PesoLido(val quantidade: Quantidade, val unidade: UnidadeMedida) {
    fun descricao(): String = quantidade.formatar(unidade)
}

/**
 * O resultado de ler uma etiqueta. Nunca grava nada: monta um rascunho e para.
 *
 * Campo que o interpretador nao leu fica `null` — em branco na tela, nunca chutado.
 */
data class EtiquetaLida(
    val textoOriginal: String,
    val validade: Leitura<LocalDate>? = null,
    val fabricacao: Leitura<LocalDate>? = null,
    val lote: Leitura<String>? = null,
    val peso: Leitura<PesoLido>? = null,
    val codigoBarras: Leitura<String>? = null,
    val avisos: List<String> = emptyList(),
) {

    fun leitura(campo: CampoEtiqueta): Leitura<*>? = when (campo) {
        CampoEtiqueta.VALIDADE -> validade
        CampoEtiqueta.LOTE -> lote
        CampoEtiqueta.FABRICACAO -> fabricacao
        CampoEtiqueta.PESO -> peso
        CampoEtiqueta.CODIGO_BARRAS -> codigoBarras
    }

    val camposLidos: List<CampoEtiqueta> get() = CampoEtiqueta.entries.filter { leitura(it) != null }

    /** Os dois campos que uma entrada exige. O resto e conveniencia. */
    val camposEssenciaisNaoLidos: List<CampoEtiqueta>
        get() = listOf(CampoEtiqueta.VALIDADE, CampoEtiqueta.LOTE).filter { leitura(it) == null }

    /** Lidos, mas com confianca baixa: aparecem destacados para conferencia. */
    val camposParaConferir: List<CampoEtiqueta>
        get() = camposLidos.filter { leitura(it)?.confiavel == false }

    /** So o que pode preencher o rascunho sozinho. */
    val validadeConfiavel: LocalDate? get() = validade?.takeIf { it.confiavel }?.valor
    val loteConfiavel: String? get() = lote?.takeIf { it.confiavel }?.valor

    val leuTudoQuePrecisa: Boolean
        get() = camposEssenciaisNaoLidos.isEmpty() && camposParaConferir.isEmpty()

    /**
     * Quando vale gastar uma chamada da camada de IA: o OCR leu o texto mas o interpretador nao
     * achou o essencial, ou achou com duvida. Se leu tudo, a IA nao tem o que acrescentar.
     */
    val precisaDeAjuda: Boolean get() = !leuTudoQuePrecisa

    fun descricaoCurta(): String = buildList {
        validade?.let { add("validade ${it.valor}") }
        lote?.let { add("lote ${it.valor}") }
        peso?.let { add("peso ${it.valor.descricao()}") }
        codigoBarras?.let { add("codigo ${it.valor}") }
    }.joinToString(", ").ifBlank { "nada reconhecido" }

    companion object {
        fun vazia(texto: String, aviso: String) = EtiquetaLida(textoOriginal = texto, avisos = listOf(aviso))
    }
}
