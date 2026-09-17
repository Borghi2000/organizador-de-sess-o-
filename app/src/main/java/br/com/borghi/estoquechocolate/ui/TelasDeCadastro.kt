package br.com.borghi.estoquechocolate.ui

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
import br.com.borghi.estoquechocolate.camera.CapturaDeFoto
import br.com.borghi.estoquechocolate.camera.FotoGuardada
import br.com.borghi.estoquechocolate.camera.Fotos
import br.com.borghi.estoquechocolate.camera.LeitorDeCodigoDeBarras
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.core.regras.Estoque
import br.com.borghi.estoquechocolate.core.regras.Pvps
import br.com.borghi.estoquechocolate.core.regras.RascunhoProduto
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque

@Composable
fun ProdutosTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit, ir: (Rota) -> Unit) {
    val hoje = vm.hoje()
    val resumos = Estoque.resumos(estado.produtos, estado.lotes, hoje)

    TelaBase(
        titulo = "Produtos e lotes",
        aoVoltar = voltar,
        acao = { TextButton(onClick = { ir(Rota.NovoProduto()) }) { Text("Novo") } },
    ) { padding ->
        ColunaRolavel(padding) {
            if (resumos.isEmpty()) {
                TextoVazio("Nenhum produto cadastrado.")
                Button(onClick = { ir(Rota.NovoProduto()) }) { Text("Cadastrar produto") }
            }

            resumos.sortedBy { it.produto.nome }.forEach { resumo ->
                val produto = resumo.produto
                val lotes = Pvps.ordenar(estado.lotesDoProduto(produto.codigo))
                CartaoSimples {
                    Text("${produto.codigo} - ${produto.nome}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${produto.categoria} - minimo ${produto.estoqueMinimo.formatar(produto.unidade)}, " +
                            "maximo ${produto.estoqueMaximo.formatar(produto.unidade)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Disponivel para venda: ${resumo.disponivelParaVenda.formatar(produto.unidade)}")
                    Text(
                        "Exposto ${resumo.exposto.formatar()} - deposito ${resumo.deposito.formatar()} - " +
                            "segregado ${resumo.segregado.formatar()} - fisico ${resumo.fisico.formatar()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (produto.observacoes.isNotBlank()) {
                        Text(produto.observacoes, style = MaterialTheme.typography.bodySmall)
                    }

                    if (lotes.isEmpty()) {
                        Text("Sem lotes registrados.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        lotes.forEach { lote ->
                            TextButton(
                                onClick = { ir(Rota.DetalheDoLote(lote.id)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(descreverLote(lote, estado, hoje), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NovoProdutoTela(
    vm: EstoqueViewModel,
    estado: EstadoDoEstoque,
    codigoBarrasInicial: String = "",
    voltar: () -> Unit,
) {
    var codigo by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var unidade by remember { mutableStateOf<UnidadeMedida?>(UnidadeMedida.UNIDADE) }
    var minimo by remember { mutableStateOf("") }
    var maximo by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<Localizacao?>(null) }
    var observacoes by remember { mutableStateOf("") }
    var confirmando by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }
    val contexto = LocalContext.current
    var codigoBarras by remember { mutableStateOf(codigoBarrasInicial) }
    var foto by remember { mutableStateOf("") }
    var lendoCodigo by remember { mutableStateOf(false) }
    var fotografando by remember { mutableStateOf(false) }

    val rascunho = RascunhoProduto(
        codigo, nome, categoria, unidade, minimo, maximo, local, observacoes,
        codigoBarras = codigoBarras,
        foto = foto,
    )
    val validacao = rascunho.validar(
        codigosExistentes = estado.produtos.map { it.codigo }.toSet(),
        codigosDeBarrasExistentes = estado.produtos
            .filter { it.codigoBarras.isNotBlank() }
            .associate { it.codigoBarras to it.nome },
    )

    if (lendoCodigo) {
        LeitorDeCodigoDeBarras(
            titulo = "Codigo de barras do produto",
            instrucao = "Aponte para o codigo da embalagem.",
            aoLer = { lido ->
                codigoBarras = lido
                if (codigo.isBlank()) codigo = lido
                lendoCodigo = false
            },
            aoDigitarNaMao = { lendoCodigo = false },
            aoVoltar = { lendoCodigo = false },
        )
        return
    }

    if (fotografando) {
        CapturaDeFoto(
            titulo = "Foto do produto",
            aoTirar = { arquivo ->
                if (foto.isNotBlank()) Fotos.apagar(contexto, foto)
                foto = arquivo
                fotografando = false
            },
            aoVoltar = { fotografando = false },
        )
        return
    }

    TelaBase("Novo produto", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            CampoTexto("Codigo", codigo, { codigo = it }, apoio = "Como voce identifica o produto")
            CampoTexto("Nome", nome, { nome = it })
            CampoTexto("Categoria", categoria, { categoria = it }, apoio = "Ex.: bombons, barras, trufas")
            SeletorDeOpcao(
                rotulo = "Unidade de medida",
                opcoes = UnidadeMedida.entries,
                selecionado = unidade,
                textoDe = { "${it.rotulo} (${it.abreviacao})" },
                aoSelecionar = { unidade = it },
            )
            CampoTexto("Estoque minimo", minimo, { minimo = it }, numerico = true)
            CampoTexto("Estoque maximo", maximo, { maximo = it }, numerico = true)
            SeletorDeOpcao(
                rotulo = "Local padrao",
                opcoes = estado.configuracao.locais,
                selecionado = local,
                textoDe = { it.descricao() },
                aoSelecionar = { local = it },
            )
            CampoTexto(
                "Codigo de barras",
                codigoBarras,
                { codigoBarras = it },
                numerico = true,
                apoio = "Opcional. E o que a camera usa para achar o produto num toque.",
            )
            if (foto.isNotBlank()) FotoGuardada(foto, "Foto do produto")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { lendoCodigo = true }, modifier = Modifier.weight(1f)) {
                    Text("Ler codigo")
                }
                OutlinedButton(onClick = { fotografando = true }, modifier = Modifier.weight(1f)) {
                    Text(if (foto.isBlank()) "Tirar foto" else "Trocar foto")
                }
            }
            CampoTexto("Observacoes", observacoes, { observacoes = it }, linhasUnicas = false)

            if (erros.isNotEmpty()) BlocoDeErrosPublico(erros)

            Button(onClick = { confirmando = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Revisar e salvar")
            }
        }
    }

    if (confirmando) {
        DialogoDeConfirmacao(
            titulo = "Confirmar produto",
            linhas = rascunho.paraConfirmacao(),
            faltantes = validacao.faltantes.map { it.mensagem },
            aoConfirmar = {
                confirmando = false
                vm.salvarProduto(rascunho) { resultado ->
                    when (resultado) {
                        is ResultadoAcao.Sucesso -> {
                            vm.avisar(resultado.textoCompleto())
                            voltar()
                        }
                        is ResultadoAcao.Bloqueado -> erros = resultado.faltantes
                        is ResultadoAcao.Conflito -> erros = listOf(resultado.mensagem)
                    }
                }
            },
            aoCancelar = { confirmando = false },
        )
    }
}

@Composable
fun DetalheDoLoteTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, loteId: String, voltar: () -> Unit) {
    val hoje = vm.hoje()
    val lote = estado.lote(loteId)
    val produto = lote?.let { estado.produto(it.produtoCodigo) }
    var acao by remember { mutableStateOf<String?>(null) }
    var quantidade by remember { mutableStateOf("") }
    var origem by remember { mutableStateOf<Localizacao?>(null) }
    var destino by remember { mutableStateOf<Localizacao?>(null) }
    var observacao by remember { mutableStateOf("") }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }

    if (lote == null || produto == null) {
        TelaBase("Lote", aoVoltar = voltar) { padding ->
            ColunaRolavel(padding) { TextoVazio("Lote nao encontrado.") }
        }
        return
    }

    val classificacao = ClassificacaoValidade.de(lote.validade, hoje)
    val cores = coresDa(classificacao)
    val movimentacoes = estado.movimentacoes.filter { it.loteId == loteId }.sortedByDescending { it.dataHora }

    TelaBase("Lote ${lote.codigoLote}", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            if (lote.fotoEtiqueta.isNotBlank()) {
                // Seis meses depois, numa divergencia, da para abrir a foto e conferir a etiqueta
                // original em vez de discutir de memoria.
                FotoGuardada(lote.fotoEtiqueta, "Etiqueta do lote ${lote.codigoLote}")
            } else if (produto.foto.isNotBlank()) {
                FotoGuardada(produto.foto, "Foto de ${produto.nome}")
            }

            CartaoSimples {
                Text(produto.nome, fontWeight = FontWeight.SemiBold)
                Etiqueta("${simboloDa(classificacao)} ${classificacao.rotulo}", cores.fundo, cores.texto)
                Text("Validade ${formatarData(lote.validade)} - ${ClassificacaoValidade.descreverPrazo(lote.validade, hoje)}")
                Text("Recebido: ${lote.quantidadeRecebida.formatar(produto.unidade)}")
                Text("Atual: ${lote.quantidadeAtual.formatar(produto.unidade)}", fontWeight = FontWeight.SemiBold)
                Text(
                    "Ultima conferencia: " +
                        (lote.dataUltimaConferencia?.let { formatarData(it) } ?: "nunca conferido"),
                )
                if (lote.segregado) {
                    Text(
                        "SEGREGADO por ${lote.motivoSegregacao?.rotulo?.lowercase() ?: "motivo nao informado"} - " +
                            "fora da venda",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (lote.observacoes.isNotBlank()) Text(lote.observacoes)
            }

            Text("Onde esta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (lote.posicoes.isEmpty()) {
                TextoVazio("Sem quantidade em nenhum local.")
            } else {
                lote.posicoes.forEach { posicao ->
                    CartaoSimples {
                        Text("${posicao.localizacao.descricao()}: ${posicao.quantidade.formatar(produto.unidade)}")
                    }
                }
            }

            Text("Acoes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { acao = "transferencia" }, modifier = Modifier.fillMaxWidth()) {
                Text("Transferir entre locais")
            }
            if (!lote.segregado) {
                Button(onClick = { acao = "segregar" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Segregar lote (tirar da venda)")
                }
            }

            if (erros.isNotEmpty()) BlocoDeErrosPublico(erros)

            Text(
                "Historico (${movimentacoes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (movimentacoes.isEmpty()) {
                TextoVazio("Nenhuma movimentacao registrada.")
            } else {
                movimentacoes.forEach { movimentacao ->
                    CartaoSimples {
                        Text(
                            "${movimentacao.tipo.rotulo}: ${movimentacao.quantidade.formatar(produto.unidade)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            formatarData(movimentacao.dataHora.toLocalDate()) + " " +
                                "%02d:%02d".format(movimentacao.dataHora.hour, movimentacao.dataHora.minute),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val caminho = listOfNotNull(
                            movimentacao.localOrigem?.let { "de ${it.nome}" },
                            movimentacao.localDestino?.let { "para ${it.nome}" },
                        ).joinToString(" ")
                        if (caminho.isNotBlank()) Text(caminho, style = MaterialTheme.typography.bodySmall)
                        if (movimentacao.observacao.isNotBlank()) {
                            Text(movimentacao.observacao, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    when (acao) {
        "transferencia" -> AlertDialog(
            onDismissRequest = { acao = null },
            title = { Text("Transferir") },
            text = {
                Column {
                    SeletorDeOpcao(
                        rotulo = "De",
                        opcoes = lote.posicoes.map { it.localizacao },
                        selecionado = origem,
                        textoDe = { "${it.descricao()} - ${lote.quantidadeEm(it).formatar()}" },
                        aoSelecionar = { origem = it },
                    )
                    SeletorDeOpcao(
                        rotulo = "Para",
                        opcoes = estado.configuracao.locais,
                        selecionado = destino,
                        textoDe = { it.descricao() },
                        aoSelecionar = { destino = it },
                    )
                    CampoTexto("Quantidade", quantidade, { quantidade = it }, numerico = true)
                    CampoTexto("Observacao", observacao, { observacao = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.registrarTransferencia(loteId, quantidade, origem, destino, observacao) { resultado ->
                        when (resultado) {
                            is ResultadoAcao.Sucesso -> {
                                vm.avisar(resultado.textoCompleto())
                                acao = null
                                quantidade = ""
                                erros = emptyList()
                            }
                            is ResultadoAcao.Bloqueado -> erros = resultado.faltantes
                            is ResultadoAcao.Conflito -> erros = listOf(resultado.mensagem)
                        }
                    }
                }) { Text("Transferir") }
            },
            dismissButton = { TextButton(onClick = { acao = null }) { Text("Cancelar") } },
        )

        "segregar" -> AlertDialog(
            onDismissRequest = { acao = null },
            title = { Text("Segregar lote") },
            text = {
                Column {
                    Text(
                        "Todo o lote sai da venda e vai para a area de segregacao. " +
                            "A quantidade continua no estoque fisico.",
                    )
                    CampoTexto("Motivo", observacao, { observacao = it }, linhasUnicas = false)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val motivo = if (lote.estaVencido(hoje)) MotivoSegregacao.VENCIDO else MotivoSegregacao.AVARIA
                    vm.segregarLote(loteId, motivo, observacao) { resultado ->
                        when (resultado) {
                            is ResultadoAcao.Sucesso -> {
                                vm.avisar(resultado.textoCompleto())
                                acao = null
                                erros = emptyList()
                            }
                            is ResultadoAcao.Bloqueado -> erros = resultado.faltantes
                            is ResultadoAcao.Conflito -> erros = listOf(resultado.mensagem)
                        }
                    }
                }) { Text("Segregar") }
            },
            dismissButton = { TextButton(onClick = { acao = null }) { Text("Cancelar") } },
        )
    }
}
