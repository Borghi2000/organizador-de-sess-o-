package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.borghi.estoquechocolate.core.painel.CartaoPainel
import br.com.borghi.estoquechocolate.core.painel.MontadorPainel
import br.com.borghi.estoquechocolate.core.painel.TipoCartao
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque

private fun classificacaoDoCartao(tipo: TipoCartao): ClassificacaoValidade = when (tipo) {
    TipoCartao.VENCIDOS -> ClassificacaoValidade.VENCIDO
    TipoCartao.VENCE_EM_7_DIAS -> ClassificacaoValidade.CRITICO
    TipoCartao.VENCE_EM_8_A_15_DIAS -> ClassificacaoValidade.URGENTE
    TipoCartao.ABAIXO_DO_MINIMO, TipoCartao.SEM_ESTOQUE_PARA_VENDA -> ClassificacaoValidade.CRITICO
    TipoCartao.DIVERGENCIAS_ABERTAS -> ClassificacaoValidade.URGENTE
    TipoCartao.SEM_CONFERENCIA_RECENTE, TipoCartao.PERDAS_DO_MES -> ClassificacaoValidade.ATENCAO
}

@Composable
fun PainelTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, ir: (Rota) -> Unit) {
    val hoje = vm.hoje()
    val painel = MontadorPainel.montar(
        produtos = estado.produtos,
        lotes = estado.lotes,
        movimentacoes = estado.movimentacoes,
        divergencias = estado.divergencias,
        hoje = hoje,
        configuracao = estado.configuracao,
    )

    TelaBase(
        titulo = "Estoque e validade",
        acao = {
            TextButton(onClick = { ir(Rota.Busca) }) { Text("Buscar") }
            TextButton(onClick = { ir(Rota.Configuracoes) }) { Text("Ajustes") }
        },
        rodape = { BarraDoDiaADia(ir) },
    ) { padding ->
        ColunaRolavel(padding) {
            Text(
                "Painel de ${formatarData(hoje)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            when {
                estado.produtos.isEmpty() -> CartaoSimples {
                    Text("Nenhum produto cadastrado ainda.", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Comece cadastrando os produtos da sua secao, ou carregue os dados de exemplo " +
                            "em Ajustes para experimentar o app antes.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { ir(Rota.NovoProduto()) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Cadastrar primeiro produto")
                    }
                }

                painel.tudoEmOrdem -> CartaoSimples {
                    Text("Nada pendente hoje.", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Nenhum vencimento proximo, nenhuma falta, nenhuma divergencia aberta e " +
                            "as conferencias estao em dia.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    Text(
                        "${painel.totalDePendencias} item(ns) exigindo acao",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    painel.pendencias.forEach { cartao ->
                        CartaoDePainel(cartao) { ir(Rota.ListaDeCartao(cartao.tipo)) }
                    }
                }
            }

            Text(
                "Comandos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )

            GradeDeComandos(ir)

            TextButton(onClick = { ir(Rota.Produtos) }, modifier = Modifier.fillMaxWidth()) {
                Text("Produtos e lotes cadastrados")
            }
        }
    }
}

@Composable
private fun CartaoDePainel(cartao: CartaoPainel, aoClicar: () -> Unit) {
    val classificacao = classificacaoDoCartao(cartao.tipo)
    val cores = coresDa(classificacao)
    CartaoSimples(aoClicar = aoClicar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Contador(cartao.quantidade, cores.fundo, cores.texto)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    "${simboloDa(classificacao)} ${cartao.tipo.titulo}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    cartao.tipo.acao,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cartao.resumo.isNotBlank()) {
                    Text(cartao.resumo, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GradeDeComandos(ir: (Rota) -> Unit) {
    val comandos = listOf(
        "O que e isso? (camera)" to Rota.IdentificarProduto,
        "Conferir com camera" to Rota.ConferenciaComCamera,
        "Entrada em rajada" to Rota.EntradaEmRajada,
        "Registrar entrada" to Rota.RegistrarEntrada,
        "Registrar contagem" to Rota.RegistrarContagem,
        "Registrar perda" to Rota.RegistrarPerda,
        "Registrar reposicao" to Rota.RegistrarReposicao,
        "Mostrar vencimentos" to Rota.Vencimentos,
        "Mostrar faltas" to Rota.Faltas,
        "Mostrar divergencias" to Rota.Divergencias,
        "Fechar conferencia do dia" to Rota.FecharConferencia,
        "Gerar relatorio semanal" to Rota.Relatorios,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        comandos.chunked(2).forEach { par ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                par.forEach { (rotulo, rota) ->
                    Button(
                        onClick = { ir(rota) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp),
                    ) {
                        Text(rotulo, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (par.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Os tres comandos do dia a dia ficam fixos embaixo, na zona do polegar.
 *
 * Os nove comandos tem o mesmo peso na grade, mas o uso nao e igual: contagem, reposicao e perda
 * acontecem todo dia; entrada e dia de entrega; relatorio e semanal. Quem e diario nao deveria
 * exigir rolagem.
 */
@Composable
private fun BarraDoDiaADia(ir: (Rota) -> Unit) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { ir(Rota.ConferenciaComCamera) }, modifier = Modifier.weight(1f)) {
                Text("Contar", style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = { ir(Rota.RegistrarReposicao) }, modifier = Modifier.weight(1f)) {
                Text("Repor", style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = { ir(Rota.RegistrarPerda) }, modifier = Modifier.weight(1f)) {
                Text("Perda", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
