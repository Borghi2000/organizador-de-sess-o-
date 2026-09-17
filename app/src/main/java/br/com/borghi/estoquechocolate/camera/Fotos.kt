package br.com.borghi.estoquechocolate.camera

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File

/**
 * As fotos ficam na area privada do app, no proprio aparelho. Nao vao para a galeria e nao saem
 * daqui: o backup JSON guarda so o nome do arquivo.
 */
object Fotos {

    private const val PASTA = "fotos"

    fun pasta(contexto: Context): File =
        File(contexto.filesDir, PASTA).apply { if (!exists()) mkdirs() }

    fun arquivo(contexto: Context, nome: String): File = File(pasta(contexto), nome)

    fun existe(contexto: Context, nome: String): Boolean =
        nome.isNotBlank() && arquivo(contexto, nome).exists()

    fun novoNome(prefixo: String): String = "$prefixo-${System.currentTimeMillis()}.jpg"

    fun carregar(contexto: Context, nome: String) =
        if (existe(contexto, nome)) BitmapFactory.decodeFile(arquivo(contexto, nome).absolutePath) else null

    fun apagar(contexto: Context, nome: String) {
        if (nome.isBlank()) return
        runCatching { arquivo(contexto, nome).delete() }
    }

    /** Fotos que nao pertencem mais a nenhum produto ou lote, apagadas depois de importar backup. */
    fun limparOrfas(contexto: Context, emUso: Set<String>) {
        runCatching {
            pasta(contexto).listFiles()?.forEach { if (it.name !in emUso) it.delete() }
        }
    }
}
