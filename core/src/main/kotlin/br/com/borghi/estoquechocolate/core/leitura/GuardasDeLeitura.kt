package br.com.borghi.estoquechocolate.core.leitura

import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.regras.RascunhoEntrada
import java.time.LocalDate
import kotlin.math.abs

/**
 * Um aviso sobre a leitura da camera. [seguraOPreenchimento] nao impede o registro: impede que o
 * app preencha o campo sozinho, empurrando a decisao para voce.
 */
data class AvisoDeLeitura(
    val campo: CampoEtiqueta,
    val mensagem: String,
    val seguraOPreenchimento: Boolean,
)

/**
 * As conferencias que o interpretador sozinho nao consegue fazer, porque dependem do que ja esta
 * cadastrado: lote repetido com validade diferente, validade fora do padrao do produto.
 *
 * E a mesma ideia da regra "nao inventar dados", aplicada a leitura automatica: a camera propoe,
 * o app duvida, voce confirma.
 */
object GuardasDeLeitura {

    /** Quanto a validade lida pode se afastar do padrao do produto antes de virar suspeita. */
    private const val DIAS_DE_FOLGA_NO_PADRAO = 180L

    fun conferir(
        etiqueta: EtiquetaLida,
        lotesDoProduto: List<Lote>,
        hoje: LocalDate,
    ): List<AvisoDeLeitura> = buildList {
        val validade = etiqueta.validade
        val lote = etiqueta.lote

        if (validade == null) {
            add(AvisoDeLeitura(CampoEtiqueta.VALIDADE, "Validade nao lida: digite antes de salvar", true))
        } else if (!validade.confiavel) {
            add(AvisoDeLeitura(CampoEtiqueta.VALIDADE, "Validade duvidosa (${validade.trecho}): confira", true))
        }

        if (lote == null) {
            add(AvisoDeLeitura(CampoEtiqueta.LOTE, "Codigo do lote nao lido: digite antes de salvar", true))
        }

        if (validade != null && lote != null) {
            val mesmoCodigo = lotesDoProduto.filter { it.codigoLote.equals(lote.valor, ignoreCase = true) }
            val conflitante = mesmoCodigo.firstOrNull { it.validade != validade.valor }
            if (conflitante != null) {
                add(
                    AvisoDeLeitura(
                        CampoEtiqueta.LOTE,
                        "Ja existe o lote ${lote.valor} deste produto com validade " +
                            "${formatar(conflitante.validade)}, e a etiqueta diz " +
                            "${formatar(validade.valor)}. Confira qual esta certa.",
                        true,
                    )
                )
            }
        }

        if (validade != null && lotesDoProduto.isNotEmpty()) {
            val prazos = lotesDoProduto.map { java.time.temporal.ChronoUnit.DAYS.between(hoje, it.validade) }
            val tipico = mediana(prazos)
            val prazoLido = java.time.temporal.ChronoUnit.DAYS.between(hoje, validade.valor)
            if (abs(prazoLido - tipico) > DIAS_DE_FOLGA_NO_PADRAO) {
                add(
                    AvisoDeLeitura(
                        CampoEtiqueta.VALIDADE,
                        "Os outros lotes deste produto vencem por volta de $tipico dias e este ficou " +
                            "em $prazoLido. Pode estar certo, mas vale conferir.",
                        false,
                    )
                )
            }
        }
    }

    /**
     * Monta o rascunho da entrada com o que foi lido — e so com isso. Campo duvidoso ou segurado
     * por uma guarda fica em branco de proposito, para aparecer destacado na tela.
     */
    fun paraRascunho(
        etiqueta: EtiquetaLida,
        produto: Produto?,
        lotesDoProduto: List<Lote>,
        hoje: LocalDate,
        base: RascunhoEntrada = RascunhoEntrada(),
    ): RascunhoEntrada {
        val avisos = conferir(etiqueta, lotesDoProduto, hoje)
        val segurados = avisos.filter { it.seguraOPreenchimento }.map { it.campo }.toSet()

        val validade = etiqueta.validade
            ?.takeIf { it.confiavel && CampoEtiqueta.VALIDADE !in segurados }
            ?.valor
        val lote = etiqueta.lote
            ?.takeIf { it.confiavel && CampoEtiqueta.LOTE !in segurados }
            ?.valor

        return base.copy(
            produtoCodigo = produto?.codigo ?: base.produtoCodigo,
            codigoLote = lote ?: base.codigoLote,
            validade = validade ?: base.validade,
            localizacao = base.localizacao ?: produto?.localPadrao,
        )
    }

    private fun mediana(valores: List<Long>): Long {
        val ordenados = valores.sorted()
        val meio = ordenados.size / 2
        return if (ordenados.size % 2 == 1) ordenados[meio]
        else (ordenados[meio - 1] + ordenados[meio]) / 2
    }

    private fun formatar(data: LocalDate): String =
        "%02d/%02d/%04d".format(data.dayOfMonth, data.monthValue, data.year)
}
