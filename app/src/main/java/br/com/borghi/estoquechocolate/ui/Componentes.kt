package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.borghi.estoquechocolate.core.regras.LinhaConfirmacao
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FORMATO_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatarData(data: LocalDate): String = data.format(FORMATO_BR)

/**
 * Le a data digitada aceitando 16/09/2026, 16-09-2026 ou so 16092026, porque no celular o
 * teclado numerico nao tem barra e digitar rapido importa mais que o formato exato.
 */
fun lerData(texto: String): LocalDate? {
    val digitos = texto.filter { it.isDigit() }
    if (digitos.length != 8) return null
    return try {
        LocalDate.of(
            digitos.substring(4, 8).toInt(),
            digitos.substring(2, 4).toInt(),
            digitos.substring(0, 2).toInt(),
        )
    } catch (erro: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaBase(
    titulo: String,
    aoVoltar: (() -> Unit)? = null,
    acao: @Composable () -> Unit = {},
    rodape: @Composable () -> Unit = {},
    conteudo: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (aoVoltar != null) TextButton(onClick = aoVoltar) { Text("Voltar") }
                },
                actions = { acao() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = rodape,
        content = conteudo,
    )
}

@Composable
fun ColunaRolavel(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        conteudo()
    }
}

@Composable
fun CampoTexto(
    rotulo: String,
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    numerico: Boolean = false,
    erro: String? = null,
    apoio: String? = null,
    linhasUnicas: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = aoMudar,
            label = { Text(rotulo) },
            singleLine = linhasUnicas,
            isError = erro != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text,
            ),
            supportingText = when {
                erro != null -> {
                    { Text(erro, color = MaterialTheme.colorScheme.error) }
                }
                apoio != null -> {
                    { Text(apoio) }
                }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Escolha de uma opcao numa lista. Abre um dialogo em vez de um menu suspenso porque o alvo de
 * toque fica maior, o que conta quando se usa o app de pe, com uma mao so.
 */
@Composable
fun <T> SeletorDeOpcao(
    rotulo: String,
    opcoes: List<T>,
    selecionado: T?,
    textoDe: (T) -> String,
    aoSelecionar: (T) -> Unit,
    erro: String? = null,
    vazio: String = "Nenhuma opcao disponivel",
) {
    var aberto by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { aberto = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selecionado?.let(textoDe) ?: "Escolher...",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (erro != null) {
            Text(erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (aberto) {
        AlertDialog(
            onDismissRequest = { aberto = false },
            title = { Text(rotulo) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (opcoes.isEmpty()) {
                        Text(vazio)
                    } else {
                        opcoes.forEach { opcao ->
                            TextButton(
                                onClick = {
                                    aoSelecionar(opcao)
                                    aberto = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(textoDe(opcao), modifier = Modifier.fillMaxWidth())
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { aberto = false }) { Text("Fechar") } },
        )
    }
}

/**
 * Nada e gravado sem passar por aqui: a tela mostra o que vai ser salvo, avisa o que esta
 * estranho e aponta campo por campo o que ainda falta.
 */
@Composable
fun DialogoDeConfirmacao(
    titulo: String,
    linhas: List<LinhaConfirmacao>,
    avisos: List<String> = emptyList(),
    faltantes: List<String> = emptyList(),
    textoConfirmar: String = "Confirmar e salvar",
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                linhas.forEach { linha ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${linha.rotulo}: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(linha.valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (faltantes.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Faltam dados obrigatorios:",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    faltantes.forEach { Text("- $it", color = MaterialTheme.colorScheme.error) }
                }
                if (avisos.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Confira antes de salvar:", fontWeight = FontWeight.SemiBold)
                    avisos.forEach { Text("- $it") }
                }
            }
        },
        confirmButton = {
            if (faltantes.isEmpty()) {
                Button(onClick = aoConfirmar) { Text(textoConfirmar) }
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar) {
                Text(if (faltantes.isEmpty()) "Voltar e revisar" else "Voltar e completar")
            }
        },
    )
}

@Composable
fun Etiqueta(texto: String, fundo: Color, cor: Color) {
    Box(
        modifier = Modifier
            .background(fundo, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(texto, color = cor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Contador(numero: Int, fundo: Color, cor: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(fundo, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$numero", color = cor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CartaoSimples(
    modifier: Modifier = Modifier,
    aoClicar: (() -> Unit)? = null,
    conteudo: @Composable () -> Unit,
) {
    val cores = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    if (aoClicar != null) {
        Card(onClick = aoClicar, colors = cores, modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) { conteudo() }
        }
    } else {
        Card(colors = cores, modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) { conteudo() }
        }
    }
}

@Composable
fun TextoVazio(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
