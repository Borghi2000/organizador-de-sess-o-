package br.com.borghi.estoquechocolate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.borghi.estoquechocolate.core.painel.TipoCartao
import kotlinx.coroutines.delay

sealed interface Rota {
    data object Painel : Rota
    data class ListaDeCartao(val tipo: TipoCartao) : Rota
    data object RegistrarEntrada : Rota
    data object RegistrarContagem : Rota
    data object RegistrarPerda : Rota
    data object RegistrarReposicao : Rota
    data object Vencimentos : Rota
    data object Faltas : Rota
    data object Divergencias : Rota
    data object FecharConferencia : Rota
    data object Relatorios : Rota
    data object Produtos : Rota
    data class NovoProduto(val codigoBarras: String = "") : Rota
    data object IdentificarProduto : Rota
    data object ConferenciaComCamera : Rota
    data object EntradaEmRajada : Rota
    data object Busca : Rota
    data class DetalheDoLote(val loteId: String) : Rota
    data object Configuracoes : Rota
}

@Composable
fun AplicativoEstoque(vm: EstoqueViewModel) {
    val pilha = remember { mutableStateListOf<Rota>(Rota.Painel) }
    val estado by vm.estado.collectAsState()

    val ir: (Rota) -> Unit = { pilha.add(it) }
    val voltar: () -> Unit = { if (pilha.size > 1) pilha.removeAt(pilha.lastIndex) }

    BackHandler(enabled = pilha.size > 1) { voltar() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val rota = pilha.last()) {
            Rota.Painel -> PainelTela(vm, estado, ir)
            is Rota.ListaDeCartao -> ListaDeCartaoTela(vm, estado, rota.tipo, voltar, ir)
            Rota.RegistrarEntrada -> RegistrarEntradaTela(vm, estado, voltar)
            Rota.RegistrarContagem -> RegistrarContagemTela(vm, estado, voltar)
            Rota.RegistrarPerda -> RegistrarPerdaTela(vm, estado, voltar)
            Rota.RegistrarReposicao -> RegistrarReposicaoTela(vm, estado, voltar)
            Rota.Vencimentos -> VencimentosTela(vm, estado, voltar, ir)
            Rota.Faltas -> FaltasTela(vm, estado, voltar)
            Rota.Divergencias -> DivergenciasTela(vm, estado, voltar)
            Rota.FecharConferencia -> FecharConferenciaTela(vm, estado, voltar)
            Rota.Relatorios -> RelatoriosTela(vm, voltar)
            Rota.Produtos -> ProdutosTela(vm, estado, voltar, ir)
            is Rota.NovoProduto -> NovoProdutoTela(vm, estado, rota.codigoBarras, voltar)
            Rota.IdentificarProduto -> IdentificarProdutoTela(vm, estado, voltar, ir)
            Rota.ConferenciaComCamera -> ConferenciaComCameraTela(vm, estado, voltar)
            Rota.EntradaEmRajada -> EntradaEmRajadaTela(vm, estado, voltar)
            Rota.Busca -> BuscaTela(vm, estado, voltar, ir)
            is Rota.DetalheDoLote -> DetalheDoLoteTela(vm, estado, rota.loteId, voltar)
            Rota.Configuracoes -> ConfiguracoesTela(vm, estado, voltar)
        }

        BannerDeMensagem(vm, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Avisos aparecem embaixo e somem sozinhos. Nada de dialogo bloqueando o caminho a cada registro:
 * numa conferencia se registram dezenas de itens seguidos.
 */
@Composable
private fun BannerDeMensagem(vm: EstoqueViewModel, modifier: Modifier = Modifier) {
    val mensagem = vm.mensagem ?: return

    LaunchedEffect(mensagem) {
        delay(6_000)
        vm.limparMensagem()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    ) {
        Text(mensagem, modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp))
        TextButton(onClick = { vm.limparMensagem() }, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text("Fechar", color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

/** Junta a mensagem principal com os avisos num texto so para o banner. */
fun ResultadoAcao.Sucesso.textoCompleto(): String =
    (listOf(mensagem) + avisos).joinToString("\n")
