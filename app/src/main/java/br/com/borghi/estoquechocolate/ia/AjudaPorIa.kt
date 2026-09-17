package br.com.borghi.estoquechocolate.ia

import br.com.borghi.estoquechocolate.aviso.Preferencias
import br.com.borghi.estoquechocolate.core.leitura.EtiquetaLida
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * O que a IA devolveu. Nada disso entra no estoque sozinho: sao sugestoes que aparecem na tela
 * com um botao "usar" ao lado, e so viram dado depois que voce confirma.
 */
data class SugestaoDaIa(
    val validade: LocalDate? = null,
    val codigoLote: String? = null,
    val nome: String? = null,
    val marca: String? = null,
    val peso: String? = null,
    val categoria: String? = null,
    val explicacao: String = "",
) {
    val vazia: Boolean
        get() = validade == null && codigoLote == null && nome == null &&
            marca == null && peso == null && categoria == null
}

@Serializable
private data class RespostaDaIa(
    val validade: String? = null,
    val lote: String? = null,
    val nome: String? = null,
    val marca: String? = null,
    val peso: String? = null,
    val categoria: String? = null,
    val explicacao: String? = null,
)

/**
 * Camada 2 do reconhecimento: entra so quando a camada offline nao resolveu.
 *
 * O OCR e o interpretador acertam a maioria das etiquetas sem rede e sem custo. A IA e chamada
 * quando eles falham — etiqueta amassada, fonte estranha, produto novo sem cadastro — e devolve
 * campos separados, nao texto solto.
 *
 * A chave e sua, fica guardada so neste aparelho e nunca entra no APK nem no repositorio. Sem
 * chave e sem o interruptor ligado, esta classe nao abre conexao nenhuma.
 */
class AjudaPorIa(private val preferencias: Preferencias) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val disponivel: Boolean get() = preferencias.iaDisponivel

    suspend fun completar(
        etiqueta: EtiquetaLida,
        hoje: LocalDate,
    ): Result<SugestaoDaIa> = withContext(Dispatchers.IO) {
        if (!preferencias.iaDisponivel) {
            return@withContext Result.failure(IllegalStateException("A ajuda por IA esta desligada."))
        }
        if (etiqueta.textoOriginal.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Nao ha texto lido para interpretar."))
        }

        runCatching {
            val cliente = AnthropicOkHttpClient.builder()
                .apiKey(preferencias.chaveDaIa)
                .build()

            val parametros = MessageCreateParams.builder()
                .model("claude-opus-5")
                // Resposta curta de proposito: sao seis campos. Nao ha por que pagar mais que isso.
                .maxTokens(1024L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system(INSTRUCAO)
                .addUserMessage(perguntaSobre(etiqueta, hoje))
                .build()

            val resposta = cliente.messages().create(parametros)
            val texto = resposta.content()
                .mapNotNull { bloco -> bloco.text().orElse(null)?.text() }
                .joinToString("\n")

            interpretar(texto)
        }
    }

    private fun perguntaSobre(etiqueta: EtiquetaLida, hoje: LocalDate): String = buildString {
        appendLine("Hoje e $hoje.")
        appendLine("Texto lido por OCR de uma etiqueta de produto de uma loja de chocolates no Brasil:")
        appendLine("---")
        appendLine(etiqueta.textoOriginal.take(2000))
        appendLine("---")
        appendLine("O leitor automatico ja entendeu:")
        appendLine("- validade: " + (etiqueta.validade?.valor?.toString() ?: "nao identificada"))
        appendLine("- lote: " + (etiqueta.lote?.valor ?: "nao identificado"))
        appendLine("Complete apenas o que falta ou o que esta marcado como duvidoso.")
    }

    private fun interpretar(texto: String): SugestaoDaIa {
        val inicio = texto.indexOf('{')
        val fim = texto.lastIndexOf('}')
        if (inicio < 0 || fim <= inicio) return SugestaoDaIa()

        val resposta = json.decodeFromString<RespostaDaIa>(texto.substring(inicio, fim + 1))
        return SugestaoDaIa(
            validade = resposta.validade?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            codigoLote = resposta.lote?.ifBlank { null },
            nome = resposta.nome?.ifBlank { null },
            marca = resposta.marca?.ifBlank { null },
            peso = resposta.peso?.ifBlank { null },
            categoria = resposta.categoria?.ifBlank { null },
            explicacao = resposta.explicacao.orEmpty(),
        )
    }

    private companion object {
        /**
         * A instrucao repete, para a IA, a mesma regra que vale para o app inteiro: campo que nao
         * da para afirmar volta nulo. Um chute aqui vira validade errada no estoque, e validade
         * errada corrompe a ordem do PVPS sem ninguem perceber.
         */
        const val INSTRUCAO = """
Voce le etiquetas de produtos de uma loja de chocolates no Brasil, a partir de texto de OCR
possivelmente sujo. Responda APENAS com um objeto JSON, sem texto antes ou depois, com as chaves:

{"validade": "AAAA-MM-DD ou null", "lote": "codigo ou null", "nome": "nome do produto ou null",
 "marca": "marca ou null", "peso": "ex.: 500 g ou null", "categoria": "ex.: bombons ou null",
 "explicacao": "uma frase curta sobre o que voce deduziu"}

Regras:
- Se nao der para afirmar um campo a partir do texto, devolva null. Nunca invente.
- Data com dois digitos de ano: resolva para o ano futuro mais proximo e explique na explicacao.
- Nao converta "fabricacao" em "validade" a menos que o texto traga o prazo em meses.
- Prefira null a um palpite: o usuario prefere digitar a corrigir um dado errado depois.
"""
    }
}
