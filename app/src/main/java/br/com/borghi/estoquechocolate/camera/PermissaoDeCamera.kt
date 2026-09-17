package br.com.borghi.estoquechocolate.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

fun temPermissaoDeCamera(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Pede a permissao na hora em que a camera vai abrir, e nunca deixa voce preso: recusar a camera
 * sempre leva de volta para a digitacao manual.
 */
@Composable
fun ComPermissaoDeCamera(
    aoDigitarNaMao: () -> Unit,
    conteudo: @Composable () -> Unit,
) {
    val contexto = LocalContext.current
    var concedida by remember { mutableStateOf(temPermissaoDeCamera(contexto)) }
    var recusada by remember { mutableStateOf(false) }

    val pedido = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { autorizou ->
        concedida = autorizou
        recusada = !autorizou
    }

    LaunchedEffect(Unit) {
        if (!concedida) pedido.launch(Manifest.permission.CAMERA)
    }

    when {
        concedida -> conteudo()

        else -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (recusada) "Sem permissao de camera nao da para ler a etiqueta."
                else "Autorize a camera para ler etiqueta e codigo de barras.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "A leitura acontece dentro do aparelho: nenhuma foto sai daqui.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { pedido.launch(Manifest.permission.CAMERA) }) { Text("Pedir de novo") }
            OutlinedButton(onClick = aoDigitarNaMao) { Text("Digitar na mao") }
        }
    }
}
