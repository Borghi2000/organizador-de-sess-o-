package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.borghi.estoquechocolate.core.painel.MontadorPainel
import br.com.borghi.estoquechocolate.core.painel.TipoCartao
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.core.regras.Estoque
import br.com.borghi.estoquechocolate.core.regras.Pvps
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque

@Composable
fun ListaDeCartaoTela(
    vm: EstoqueViewModel,
    estado: EstadoDoEstoque,
    tipo: TipoCartao,
    voltar: () -> Unit,
    ir: (Rota) -> Unit,
) {
    val painel = MontadorPainel.montar(
        estado.produtos, estado.lotes, estado.movimentacoes, estado.divergencias, vm.hoje(), estado.configuracao,
    )
    val cartao = painel.cartao(tipo)

    TelaBase(tipo.titulo, aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text(tipo.acao, style = MaterialTheme.typography.bodyMedium)
            if (cartao.resumo.isNotBlank()) Text(cartao.resumo, fontWeight = FontWeight.SemiBold)

            if (cartao.itens.isEmpty()) {
                TextoVazio("Nada aqui no momento.")
            } else {
                cartao.itens.forEach { item ->
                    CartaoSimples(aoClicar = item.loteId?.let { id -> { ir(Rota.DetalheDoLote(id)) } }) {
                        Text(item.titulo, fontWeight = FontWeight.SemiBold)
                        Text(item.detalhe, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun VencimentosTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit, ir: (Rota) -> Unit) {
    val hoje = vm.hoje()
    val porClassificacao = Pvps.ordenar(estado.lotes.filter { it.quantidadeAtual.ehPositiva })
        .groupBy { ClassificacaoValidade.de(it.validade, hoje) }

    TelaBase("Mostrar vencimentos", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text(
                "Do que vence primeiro para o que vence depois.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (estado.lotes.none { it.quantidadeAtual.ehPositiva }) {
                TextoVazio("Nenhum lote com estoque.")
            }

            ClassificacaoValidade.entries.sortedBy { it.prioridade }.forEach { classificacao ->
                val lotes = porClassificacao[classificacao].orEmpty()
                if (lotes.isEmpty()) return@forEach
                val cores = coresDa(classificacao)

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Etiqueta("${classificacao.rotulo} (${lotes.size})", cores.fundo, cores.texto)
                }
                Text(classificacao.descricao, style = MaterialTheme.typography.bodySmall)

                lotes.forEach { lote ->
                    CartaoSimples(aoClicar = { ir(Rota.DetalheDoLote(lote.id)) }) {
                        Text(estado.nomeDoProduto(lote.produtoCodigo), fontWeight = FontWeight.SemiBold)
                        Text(descreverLote(lote, estado, hoje), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Validade ${formatarData(lote.validade)}" +
                                if (lote.segregado) " - SEGREGADO, fora da venda" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FaltasTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    val resumos = Estoque.resumos(estado.produtos, estado.lotes, hoje)
    val faltando = resumos.filter { it.abaixoDoMinimo }.sortedBy { it.disponivelParaVenda.milesimos }
    val enganosos = resumos.filter { it.temEstoqueMasNaoPodeVender }

    TelaBase("Mostrar faltas", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text("Abaixo do estoque minimo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (faltando.isEmpty()) {
                TextoVazio("Nenhum produto abaixo do minimo.")
            } else {
                faltando.forEach { resumo ->
                    CartaoSimples {
                        Text(resumo.produto.nome, fontWeight = FontWeight.SemiBold)
                        Text("Disponivel para venda: ${resumo.disponivelParaVenda.formatar(resumo.produto.unidade)}")
                        Text("Minimo: ${resumo.produto.estoqueMinimo.formatar(resumo.produto.unidade)}")
                        Text(
                            "Pedir ${resumo.sugestaoDeCompra.formatar(resumo.produto.unidade)} para chegar ao maximo",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (resumo.deposito.ehPositiva) {
                            Text(
                                "Ha ${resumo.deposito.formatar(resumo.produto.unidade)} no deposito: " +
                                    "de para repor antes de pedir.",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Text(
                "Sem estoque para venda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Produtos que existem no sistema mas nao podem ser vendidos, por estarem vencidos ou segregados.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (enganosos.isEmpty()) {
                TextoVazio("Nenhum caso assim.")
            } else {
                enganosos.forEach { resumo ->
                    CartaoSimples {
                        Text(resumo.produto.nome, fontWeight = FontWeight.SemiBold)
                        Text("Estoque fisico: ${resumo.fisico.formatar(resumo.produto.unidade)}")
                        Text("Segregado: ${resumo.segregado.formatar(resumo.produto.unidade)}")
                        Text("Vencido ainda solto: ${resumo.vencidoSolto.formatar(resumo.produto.unidade)}")
                    }
                }
            }
        }
    }
}

@Composable
fun DivergenciasTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val abertas = estado.divergencias.filter { it.estaAberta }.sortedBy { it.data }
    val resolvidas = estado.divergencias.filter { !it.estaAberta }.sortedByDescending { it.dataResolucao }
    var resolvendo by remember { mutableStateOf<String?>(null) }
    var observacao by remember { mutableStateOf("") }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }

    TelaBase("Mostrar divergencias", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text("Abertas (${abertas.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (abertas.isEmpty()) {
                TextoVazio("Nenhuma divergencia aberta.")
            } else {
                abertas.forEach { divergencia ->
                    val unidade = estado.produto(divergencia.produtoCodigo)?.unidade
                        ?: br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida.UNIDADE
                    CartaoSimples {
                        Text(estado.nomeDoProduto(divergencia.produtoCodigo), fontWeight = FontWeight.SemiBold)
                        Text(divergencia.descricao(unidade), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Aberta em ${formatarData(divergencia.data)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (divergencia.observacao.isNotBlank()) Text("Obs.: ${divergencia.observacao}")
                        Button(
                            onClick = {
                                resolvendo = divergencia.id
                                observacao = ""
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Ajustar estoque pela contagem")
                        }
                    }
                }
            }

            BlocoDeErrosPublico(erros)

            if (resolvidas.isNotEmpty()) {
                Text(
                    "Resolvidas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                resolvidas.take(20).forEach { divergencia ->
                    val unidade = estado.produto(divergencia.produtoCodigo)?.unidade
                        ?: br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida.UNIDADE
                    CartaoSimples {
                        Text(estado.nomeDoProduto(divergencia.produtoCodigo))
                        Text(divergencia.descricao(unidade), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Resolvida em ${divergencia.dataResolucao?.let { formatarData(it) } ?: "-"}: " +
                                divergencia.observacaoResolucao,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    resolvendo?.let { id ->
        val divergencia = estado.divergencia(id)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { resolvendo = null },
            title = { Text("Ajuste de inventario") },
            text = {
                Column {
                    Text(
                        "O estoque do lote ${divergencia?.codigoLote} vai passar a ser a quantidade contada " +
                            "(${divergencia?.quantidadeContada?.formatar()}). A movimentacao fica registrada.",
                    )
                    CampoTexto(
                        "Motivo do ajuste",
                        observacao,
                        { observacao = it },
                        linhasUnicas = false,
                        apoio = "Obrigatorio",
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.resolverDivergencia(id, observacao) { resultado ->
                        when (resultado) {
                            is ResultadoAcao.Sucesso -> {
                                vm.avisar(resultado.textoCompleto())
                                resolvendo = null
                                erros = emptyList()
                            }
                            is ResultadoAcao.Bloqueado -> erros = resultado.faltantes
                            is ResultadoAcao.Conflito -> erros = listOf(resultado.mensagem)
                        }
                    }
                }) { Text("Confirmar ajuste") }
            },
            dismissButton = { TextButton(onClick = { resolvendo = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun BlocoDeErrosPublico(erros: List<String>) {
    if (erros.isEmpty()) return
    CartaoSimples {
        erros.forEach { Text("- $it", color = MaterialTheme.colorScheme.error) }
    }
}
