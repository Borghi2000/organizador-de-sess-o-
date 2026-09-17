package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.borghi.estoquechocolate.core.regras.Pvps
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque

/**
 * Uma caixa so. Acha produto ou lote, por nome, codigo, codigo de barras ou codigo do lote —
 * porque no corredor voce nao lembra em qual lista aquilo estava.
 */
@Composable
fun BuscaTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit, ir: (Rota) -> Unit) {
    val hoje = vm.hoje()
    var termo by remember { mutableStateOf("") }
    val alvo = termo.trim().lowercase()

    val produtos = if (alvo.isBlank()) emptyList() else estado.produtos.filter {
        it.nome.lowercase().contains(alvo) ||
            it.codigo.lowercase().contains(alvo) ||
            it.categoria.lowercase().contains(alvo) ||
            it.codigoBarras.lowercase().contains(alvo)
    }
    val lotes = if (alvo.isBlank()) emptyList() else Pvps.ordenar(
        estado.lotes.filter {
            it.codigoLote.lowercase().contains(alvo) ||
                estado.nomeDoProduto(it.produtoCodigo).lowercase().contains(alvo)
        }
    )

    TelaBase("Buscar", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            CampoTexto(
                "Produto ou lote",
                termo,
                { termo = it },
                apoio = "Nome, codigo, codigo de barras ou codigo do lote",
            )

            if (alvo.isBlank()) {
                TextoVazio("Digite para procurar.")
                return@ColunaRolavel
            }

            if (produtos.isEmpty() && lotes.isEmpty()) {
                TextoVazio("Nada encontrado para \"$termo\".")
            }

            if (produtos.isNotEmpty()) {
                Text("Produtos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                produtos.forEach { produto ->
                    CartaoSimples(aoClicar = { ir(Rota.Produtos) }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(produto.nome, fontWeight = FontWeight.Bold)
                            Text(
                                "${produto.codigo} - ${produto.categoria}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (lotes.isNotEmpty()) {
                Text(
                    "Lotes",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                lotes.forEach { lote ->
                    CartaoSimples(aoClicar = { ir(Rota.DetalheDoLote(lote.id)) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                "${estado.nomeDoProduto(lote.produtoCodigo)} - lote ${lote.codigoLote}",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(descreverLote(lote, estado, hoje), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
