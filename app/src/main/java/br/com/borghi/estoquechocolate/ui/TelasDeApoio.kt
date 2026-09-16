package br.com.borghi.estoquechocolate.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import br.com.borghi.estoquechocolate.core.relatorio.Relatorio
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque
import java.time.format.DateTimeFormatter

@Composable
fun RelatoriosTela(vm: EstoqueViewModel, voltar: () -> Unit) {
    val contexto = LocalContext.current
    var semanal by remember { mutableStateOf(true) }
    val relatorio: Relatorio = if (semanal) vm.relatorioSemanal() else vm.relatorioMensal()
    val texto = relatorio.paraTexto()

    val salvarCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            contexto.contentResolver.openOutputStream(uri)?.use { it.write(relatorio.paraCsv().toByteArray()) }
        }.onSuccess { vm.avisar("Planilha salva.") }
            .onFailure { vm.avisar("Nao consegui salvar: ${it.message}") }
    }

    TelaBase("Relatorios", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { semanal = true }, modifier = Modifier.weight(1f)) { Text("Semanal") }
                OutlinedButton(onClick = { semanal = false }, modifier = Modifier.weight(1f)) { Text("Mensal") }
            }

            CartaoSimples {
                Text(texto, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    val intencao = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, relatorio.titulo)
                        putExtra(Intent.EXTRA_TEXT, texto)
                    }
                    contexto.startActivity(Intent.createChooser(intencao, "Compartilhar relatorio"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Compartilhar como texto")
            }

            OutlinedButton(
                onClick = {
                    val nome = "relatorio-${relatorio.inicio.format(DateTimeFormatter.BASIC_ISO_DATE)}.csv"
                    salvarCsv.launch(nome)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar planilha CSV")
            }
        }
    }
}

@Composable
fun ConfiguracoesTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val contexto = LocalContext.current
    var dias by remember(estado.configuracao.diasSemConferencia) {
        mutableStateOf(estado.configuracao.diasSemConferencia.toString())
    }
    var nomeLocal by remember { mutableStateOf("") }
    var tipoLocal by remember { mutableStateOf(TipoLocal.EXPOSICAO) }
    var confirmandoLimpeza by remember { mutableStateOf(false) }
    var confirmandoExemplo by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }

    fun tratar(resultado: ResultadoAcao) {
        when (resultado) {
            is ResultadoAcao.Sucesso -> {
                vm.avisar(resultado.textoCompleto())
                erros = emptyList()
            }
            is ResultadoAcao.Bloqueado -> erros = resultado.faltantes
            is ResultadoAcao.Conflito -> erros = listOf(resultado.mensagem)
        }
    }

    val exportar = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            contexto.contentResolver.openOutputStream(uri)?.use { it.write(vm.textoDoBackup().toByteArray()) }
        }.onSuccess { vm.avisar("Backup salvo. Guarde esse arquivo em outro lugar.") }
            .onFailure { vm.avisar("Nao consegui salvar o backup: ${it.message}") }
    }

    val importar = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            contexto.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.onSuccess { texto ->
            if (texto == null) {
                vm.avisar("Nao consegui ler o arquivo.")
            } else {
                vm.importarBackup(texto) { tratar(it) }
            }
        }.onFailure { vm.avisar("Nao consegui ler o arquivo: ${it.message}") }
    }

    TelaBase("Ajustes", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text("Conferencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            CampoTexto(
                "Dias ate cobrar nova conferencia",
                dias,
                { dias = it },
                numerico = true,
                apoio = "Lotes sem contagem por mais tempo que isso aparecem no painel",
            )
            Button(
                onClick = { vm.salvarDiasSemConferencia(dias.toIntOrNull() ?: 0) { tratar(it) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar prazo")
            }

            Text(
                "Locais",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            estado.configuracao.locais.forEach { Text("- ${it.descricao()}") }
            CampoTexto("Novo local", nomeLocal, { nomeLocal = it })
            SeletorDeOpcao(
                rotulo = "Tipo do local",
                opcoes = TipoLocal.entries,
                selecionado = tipoLocal,
                textoDe = { it.rotulo },
                aoSelecionar = { tipoLocal = it },
            )
            OutlinedButton(
                onClick = {
                    vm.salvarLocal(nomeLocal, tipoLocal) { resultado ->
                        tratar(resultado)
                        if (resultado is ResultadoAcao.Sucesso) nomeLocal = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Adicionar local")
            }

            Text(
                "Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Os dados ficam somente neste aparelho. O backup e a unica forma de nao perder tudo " +
                    "se o celular quebrar ou for trocado. Faca um de tempos em tempos.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { exportar.launch("estoque-chocolate-${vm.hoje()}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Exportar backup")
            }
            OutlinedButton(
                onClick = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Importar backup (substitui tudo)")
            }

            Text(
                "Dados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedButton(onClick = { confirmandoExemplo = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Carregar dados de exemplo")
            }
            OutlinedButton(onClick = { confirmandoLimpeza = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar tudo")
            }

            if (erros.isNotEmpty()) BlocoDeErrosPublico(erros)

            Text(
                "Estoque e validade - app pessoal, sem internet e sem conta. " +
                    "Nenhum dado sai do aparelho a nao ser pelo backup que voce exportar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    if (confirmandoExemplo) {
        AlertDialog(
            onDismissRequest = { confirmandoExemplo = false },
            title = { Text("Carregar dados de exemplo") },
            text = {
                Text(
                    "Isso APAGA o que estiver gravado e coloca produtos inventados, so para voce " +
                        "experimentar o app. Depois use 'Limpar tudo' antes de cadastrar o estoque real.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmandoExemplo = false
                    vm.carregarDadosDeExemplo { tratar(it) }
                }) { Text("Carregar exemplo") }
            },
            dismissButton = { TextButton(onClick = { confirmandoExemplo = false }) { Text("Cancelar") } },
        )
    }

    if (confirmandoLimpeza) {
        AlertDialog(
            onDismissRequest = { confirmandoLimpeza = false },
            title = { Text("Apagar tudo") },
            text = {
                Column {
                    Text("Isso apaga produtos, lotes, movimentacoes, divergencias e conferencias. Nao da para desfazer.")
                    Text(
                        "Exporte um backup antes se houver qualquer chance de precisar desses dados.",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmandoLimpeza = false
                    vm.limparTudo { tratar(it) }
                }) { Text("Apagar tudo") }
            },
            dismissButton = { TextButton(onClick = { confirmandoLimpeza = false }) { Text("Cancelar") } },
        )
    }
}
