package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Teclado proprio, dentro da tela.
 *
 * O teclado do sistema e lento de abrir, ocupa meia tela e faz o conteudo pular a cada campo. Numa
 * conferencia se digitam dezenas de quantidades seguidas, quase sempre com uma mao so segurando a
 * caixa: teclas grandes e fixas ganham do teclado do sistema por uma margem larga.
 */
@Composable
fun TecladoNumerico(
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    aceitaDecimal: Boolean = true,
) {
    fun digitar(tecla: String) {
        val novo = when {
            tecla == "," && (!aceitaDecimal || valor.contains(",")) -> valor
            tecla == "," && valor.isEmpty() -> "0,"
            else -> valor + tecla
        }
        aoMudar(novo.take(9))
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                linha.forEach { tecla -> Tecla(tecla, Modifier.weight(1f)) { digitar(tecla) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Tecla(if (aceitaDecimal) "," else "00", Modifier.weight(1f)) {
                if (aceitaDecimal) digitar(",") else digitar("00")
            }
            Tecla("0", Modifier.weight(1f)) { digitar("0") }
            OutlinedButton(
                onClick = { aoMudar(valor.dropLast(1)) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Apagar", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Tecla(texto: String, modifier: Modifier = Modifier, aoClicar: () -> Unit) {
    Button(
        onClick = aoClicar,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Mostrador grande do valor digitado, para conferir de longe sem apertar os olhos. */
@Composable
fun MostradorDeQuantidade(valor: String, unidade: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            valor.ifBlank { "0" },
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.displaySmall,
        )
        Text(unidade, style = MaterialTheme.typography.bodyMedium)
    }
}
