@file:UseSerializers(SerializadorDataHora::class)

package br.com.borghi.estoquechocolate.core.backup

import br.com.borghi.estoquechocolate.core.modelo.Conferencia
import br.com.borghi.estoquechocolate.core.modelo.Configuracao
import br.com.borghi.estoquechocolate.core.modelo.Divergencia
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Movimentacao
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.SerializadorDataHora
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

/**
 * O arquivo de backup e o banco inteiro em texto JSON, legivel a olho nu.
 *
 * Ele e a unica saida de dados do app: se o aparelho quebrar ou for trocado, e por aqui que o
 * estoque volta.
 */
@Serializable
data class ArquivoBackup(
    val versao: Int = VERSAO_ATUAL,
    val geradoEm: LocalDateTime,
    val produtos: List<Produto> = emptyList(),
    val lotes: List<Lote> = emptyList(),
    val movimentacoes: List<Movimentacao> = emptyList(),
    val divergencias: List<Divergencia> = emptyList(),
    val conferencias: List<Conferencia> = emptyList(),
    val configuracao: Configuracao = Configuracao(),
) {
    val resumo: String
        get() = "${produtos.size} produto(s), ${lotes.size} lote(s), " +
            "${movimentacoes.size} movimentacao(oes), ${divergencias.size} divergencia(s)"

    companion object {
        const val VERSAO_ATUAL = 1
    }
}

sealed interface ResultadoImportacao {
    data class Sucesso(val arquivo: ArquivoBackup) : ResultadoImportacao

    /** Nunca importa pela metade: ou o arquivo inteiro e valido, ou nada entra. */
    data class Falha(val mensagem: String) : ResultadoImportacao
}

object Backup {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportar(arquivo: ArquivoBackup): String = json.encodeToString(ArquivoBackup.serializer(), arquivo)

    fun importar(texto: String): ResultadoImportacao = try {
        val arquivo = json.decodeFromString(ArquivoBackup.serializer(), texto)
        when {
            arquivo.versao > ArquivoBackup.VERSAO_ATUAL -> ResultadoImportacao.Falha(
                "Este backup foi gerado por uma versao mais nova do app (versao ${arquivo.versao}). " +
                    "Atualize o app antes de importar.",
            )
            else -> ResultadoImportacao.Sucesso(arquivo)
        }
    } catch (erro: Exception) {
        ResultadoImportacao.Falha("Arquivo de backup invalido: ${erro.message ?: "conteudo nao reconhecido"}")
    }
}
