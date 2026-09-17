package br.com.borghi.estoquechocolate.camera

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
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

/**
 * Mostra uma foto guardada, se ela ainda existir. Foto apagada do aparelho nao quebra a tela:
 * simplesmente nao aparece.
 */
@androidx.compose.runtime.Composable
fun FotoGuardada(
    nome: String,
    descricao: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val imagem = androidx.compose.runtime.remember(nome) { Fotos.carregar(contexto, nome) } ?: return

    androidx.compose.foundation.Image(
        bitmap = imagem.asImageBitmap(),
        contentDescription = descricao,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
}
