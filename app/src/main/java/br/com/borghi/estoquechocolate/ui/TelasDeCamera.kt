package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.borghi.estoquechocolate.camera.FotoDaEtiqueta
import br.com.borghi.estoquechocolate.camera.LeitorDeCodigoDeBarras
import br.com.borghi.estoquechocolate.camera.LeitorDeEtiqueta
import br.com.borghi.estoquechocolate.core.leitura.EtiquetaLida
import br.com.borghi.estoquechocolate.core.leitura.GuardasDeLeitura
import br.com.borghi.estoquechocolate.core.leitura.InterpretadorDeEtiqueta
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.core.regras.Pvps
import br.com.borghi.estoquechocolate.core.regras.RascunhoContagem
import br.com.borghi.estoquechocolate.core.regras.RascunhoEntrada
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque

/**
 * Nao existe "a camera": existem tres, com propositos diferentes. Misturar as tres numa tela so e
 * o erro classico, porque cada uma responde uma pergunta.
 *
 * - Identificar produto: "que produto e este?"
 * - Ler etiqueta (dentro da entrada e da rajada): "que lote e este e quando vence?"
 * - Conferir: "quero contar este" — e a que mais economiza tempo no dia a dia.
 */

/** Cartao que mostra o que a camera entendeu, campo a campo, antes de qualquer gravacao. */
@Composable
fun CartaoDaLeitura(etiqueta: EtiquetaLida, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("O que eu li na etiqueta", fontWeight = FontWeight.Bold)
            Text("Validade: " + (etiqueta.validade?.let { formatarData(it.valor) } ?: "nao li"))
            Text("Lote: " + (etiqueta.lote?.valor ?: "nao li"))
            etiqueta.peso?.let { Text("Peso: ${it.valor.descricao()}") }
            etiqueta.codigoBarras?.let { Text("Codigo de barras: ${it.valor}") }
            etiqueta.avisos.forEach {
                Text("- $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ------------------------------------------------------------- identificar produto

@Composable
fun IdentificarProdutoTela(
    vm: EstoqueViewModel,
    estado: EstadoDoEstoque,
    voltar: () -> Unit,
    ir: (Rota) -> Unit,
) {
    val hoje = vm.hoje()
    var codigoLido by remember { mutableStateOf<String?>(null) }

    val codigo = codigoLido
    if (codigo == null) {
        LeitorDeCodigoDeBarras(
            titulo = "O que e isso?",
            instrucao = "Aponte para o codigo de barras da embalagem.",
            aoLer = { codigoLido = it },
            aoDigitarNaMao = voltar,
            aoVoltar = voltar,
        )
        return
    }

    val produto = vm.produtoPorCodigoDeBarras(codigo)

    TelaBase("Produto identificado", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text("Codigo lido: $codigo", style = MaterialTheme.typography.bodyMedium)

            if (produto == null) {
                Text(
                    "Nenhum produto cadastrado com este codigo.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { ir(Rota.NovoProduto(codigoBarras = codigo)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cadastrar produto novo com este codigo") }

                Text("Ou ligue este codigo a um produto que ja existe:")
                estado.produtos.forEach { existente ->
                    OutlinedButton(
                        onClick = {
                            vm.associarCodigoDeBarras(existente.codigo, codigo) { resultado ->
                                when (resultado) {
                                    is ResultadoAcao.Sucesso -> {
                                        vm.avisar(resultado.textoCompleto())
                                        codigoLido = null
                                    }
                                    is ResultadoAcao.Conflito -> vm.avisar(resultado.mensagem)
                                    is ResultadoAcao.Bloqueado -> vm.avisar(resultado.faltantes.joinToString("; "))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${existente.codigo} - ${existente.nome}") }
                }
            } else {
                ResumoDoProduto(produto, estado, hoje, ir)
            }

            OutlinedButton(onClick = { codigoLido = null }, modifier = Modifier.fillMaxWidth()) {
                Text("Ler outro codigo")
            }
        }
    }
}

@Composable
private fun ResumoDoProduto(
    produto: Produto,
    estado: EstadoDoEstoque,
    hoje: java.time.LocalDate,
    ir: (Rota) -> Unit,
) {
    val lotes = Pvps.ordenar(estado.lotesDoProduto(produto.codigo))

    Text(produto.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("${produto.codigo} - ${produto.categoria}", style = MaterialTheme.typography.bodyMedium)

    if (lotes.isEmpty()) {
        TextoVazio("Sem lotes cadastrados deste produto.")
        return
    }

    Text("Lotes, do que vence primeiro para o ultimo:", fontWeight = FontWeight.Bold)
    lotes.forEach { lote ->
        CartaoSimples(aoClicar = { ir(Rota.DetalheDoLote(lote.id)) }) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Lote ${lote.codigoLote}", fontWeight = FontWeight.Bold)
                Text(descreverLote(lote, estado, hoje), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ------------------------------------------------------------------ conferir

/**
 * Conferencia com camera: aponta, o app ja esta no campo de quantidade daquele lote, mostrando o
 * esperado. Contou, digitou, aponta para o proximo. Sem lista suspensa e sem busca.
 */
@Composable
fun ConferenciaComCameraTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    var lote by remember { mutableStateOf<Lote?>(null) }
    var local by remember { mutableStateOf<Localizacao?>(null) }
    var quantidade by remember { mutableStateOf("") }
    var escolhendoLote by remember { mutableStateOf<List<Lote>>(emptyList()) }
    var conferidos by remember { mutableStateOf(0) }

    val emFoco = lote

    if (emFoco == null && escolhendoLote.isEmpty()) {
        LeitorDeCodigoDeBarras(
            titulo = if (conferidos == 0) "Conferir" else "Conferir ($conferidos ja contados)",
            instrucao = "Aponte para o codigo de barras do produto que voce vai contar.",
            aoLer = { codigo ->
                val produto = vm.produtoPorCodigoDeBarras(codigo)
                if (produto == null) {
                    vm.avisar("Codigo $codigo nao esta ligado a nenhum produto.")
                } else {
                    val doProduto = Pvps.ordenar(estado.lotesDoProduto(produto.codigo))
                    when {
                        doProduto.isEmpty() -> vm.avisar("${produto.nome} nao tem lote cadastrado.")
                        doProduto.size == 1 -> {
                            lote = doProduto.first()
                            local = doProduto.first().posicoes.firstOrNull()?.localizacao
                                ?: produto.localPadrao
                        }
                        else -> escolhendoLote = doProduto
                    }
                }
            },
            aoDigitarNaMao = voltar,
            aoVoltar = voltar,
        )
        return
    }

    if (escolhendoLote.isNotEmpty()) {
        TelaBase("Qual lote?", aoVoltar = { escolhendoLote = emptyList() }) { padding ->
            ColunaRolavel(padding) {
                Text("Este produto tem mais de um lote. O que vence primeiro esta no topo.")
                escolhendoLote.forEach { candidato ->
                    CartaoSimples(aoClicar = {
                        lote = candidato
                        local = candidato.posicoes.firstOrNull()?.localizacao
                        escolhendoLote = emptyList()
                    }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Lote ${candidato.codigoLote}", fontWeight = FontWeight.Bold)
                            Text(descreverLote(candidato, estado, hoje), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        return
    }

    val loteAtual = emFoco!!
    val produto = estado.produto(loteAtual.produtoCodigo)
    val esperado = local?.let { loteAtual.quantidadeEm(it) } ?: loteAtual.quantidadeAtual

    TelaBase("Contar lote", aoVoltar = { lote = null; quantidade = "" }) { padding ->
        ColunaRolavel(padding) {
            Text(produto?.nome ?: loteAtual.produtoCodigo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Lote ${loteAtual.codigoLote} - ${ClassificacaoValidade.descreverPrazo(loteAtual.validade, hoje)}")

            SeletorDeOpcao(
                rotulo = "Local conferido",
                opcoes = estado.configuracao.locais,
                selecionado = local,
                textoDe = { it.descricao() },
                aoSelecionar = { local = it },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("O sistema espera", style = MaterialTheme.typography.bodySmall)
                    Text(
                        esperado.formatar(produto?.unidade ?: br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida.UNIDADE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            MostradorDeQuantidade(quantidade, "contado agora")
            TecladoNumerico(quantidade, { quantidade = it })

            Button(
                onClick = {
                    val rascunho = RascunhoContagem(
                        loteId = loteAtual.id,
                        localizacao = local,
                        quantidadeContada = quantidade,
                    )
                    vm.registrarContagem(rascunho) { resultado ->
                        when (resultado) {
                            is ResultadoAcao.Sucesso -> {
                                vm.avisar(resultado.textoCompleto())
                                conferidos += 1
                                lote = null
                                quantidade = ""
                            }
                            is ResultadoAcao.Bloqueado -> vm.avisar(resultado.faltantes.joinToString("; "))
                            is ResultadoAcao.Conflito -> vm.avisar(resultado.mensagem)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirmar e ler o proximo") }

            OutlinedButton(onClick = voltar, modifier = Modifier.fillMaxWidth()) {
                Text("Encerrar ($conferidos contados)")
            }
        }
    }
}

// ------------------------------------------------------------- entrada em rajada

/** Um item da fila de entrada: o que a camera leu e o rascunho que saiu dali. */
data class ItemDaRajada(
    val etiqueta: EtiquetaLida,
    val rascunho: RascunhoEntrada,
)

/**
 * Dia de entrega: a camera fica aberta, voce fotografa etiqueta atras de etiqueta, e so no fim
 * revisa as vinte de uma vez. Nada e gravado antes da revisao.
 */
@Composable
fun EntradaEmRajadaTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    val fila = remember { mutableStateListOf<ItemDaRajada>() }
    var revisando by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var problemas by remember { mutableStateOf<List<String>>(emptyList()) }

    if (!revisando) {
        LeitorDeEtiqueta(
            aoLer = { foto: FotoDaEtiqueta ->
                val etiqueta = InterpretadorDeEtiqueta.interpretar(foto.texto, hoje)
                val produto = etiqueta.codigoBarras?.let { vm.produtoPorCodigoDeBarras(it.valor) }
                val rascunho = GuardasDeLeitura.paraRascunho(
                    etiqueta = etiqueta,
                    produto = produto,
                    lotesDoProduto = produto?.let { estado.lotesDoProduto(it.codigo) } ?: emptyList(),
                    hoje = hoje,
                ).copy(fotoEtiqueta = foto.arquivo)
                fila.add(ItemDaRajada(etiqueta, rascunho))
                vm.avisar("Item ${fila.size} na fila: ${etiqueta.descricaoCurta()}")
            },
            aoDigitarNaMao = { revisando = true },
            aoVoltar = { if (fila.isEmpty()) voltar() else revisando = true },
        )
        return
    }

    TelaBase("Revisar ${fila.size} itens", aoVoltar = { revisando = false }) { padding ->
        ColunaRolavel(padding) {
            Text(
                "Nada foi gravado ainda. Corrija o que a camera nao leu e salve tudo de uma vez.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (fila.isEmpty()) TextoVazio("A fila esta vazia. Volte e fotografe as etiquetas.")

            fila.forEachIndexed { indice, item ->
                ItemDaFila(
                    numero = indice + 1,
                    item = item,
                    estado = estado,
                    hoje = hoje,
                    aoMudar = { fila[indice] = it },
                    aoRemover = { fila.removeAt(indice) },
                )
            }

            if (problemas.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Ficaram de fora:", fontWeight = FontWeight.Bold)
                        problemas.forEach { Text("- $it") }
                    }
                }
            }

            Button(
                onClick = { revisando = false },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Fotografar mais uma") }

            Button(
                enabled = fila.isNotEmpty() && !salvando,
                onClick = {
                    salvando = true
                    vm.registrarEntradasEmFila(fila.map { it.rascunho }) { gravadas, falhas ->
                        salvando = false
                        problemas = falhas
                        vm.avisar("$gravadas ${if (gravadas == 1) "entrada gravada" else "entradas gravadas"}.")
                        if (falhas.isEmpty()) {
                            fila.clear()
                            voltar()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (salvando) "Salvando..." else "Salvar as ${fila.size} entradas") }
        }
    }
}

@Composable
private fun ItemDaFila(
    numero: Int,
    item: ItemDaRajada,
    estado: EstadoDoEstoque,
    hoje: java.time.LocalDate,
    aoMudar: (ItemDaRajada) -> Unit,
    aoRemover: () -> Unit,
) {
    val rascunho = item.rascunho
    val produto = estado.produto(rascunho.produtoCodigo)
    val validacao = rascunho.validar(hoje)
    var validadeTexto by remember(numero) {
        mutableStateOf(rascunho.validade?.let { formatarData(it).replace("/", "") } ?: "")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Item $numero", fontWeight = FontWeight.Bold)
                TextButton(onClick = aoRemover) { Text("Remover") }
            }

            SeletorDeOpcao(
                rotulo = "Produto",
                opcoes = estado.produtos,
                selecionado = produto,
                textoDe = { "${it.codigo} - ${it.nome}" },
                aoSelecionar = {
                    aoMudar(
                        item.copy(
                            rascunho = rascunho.copy(
                                produtoCodigo = it.codigo,
                                localizacao = rascunho.localizacao ?: it.localPadrao,
                            ),
                        )
                    )
                },
                vazio = "Cadastre um produto antes",
            )
            CampoTexto("Codigo do lote", rascunho.codigoLote, {
                aoMudar(item.copy(rascunho = rascunho.copy(codigoLote = it)))
            })
            CampoTexto(
                "Validade",
                validadeTexto,
                {
                    validadeTexto = it
                    aoMudar(item.copy(rascunho = rascunho.copy(validade = lerData(it))))
                },
                numerico = true,
                apoio = "Ex.: 31122026",
                erro = if (validadeTexto.isNotBlank() && lerData(validadeTexto) == null) "Data invalida" else null,
            )
            CampoTexto("Quantidade", rascunho.quantidade, {
                aoMudar(item.copy(rascunho = rascunho.copy(quantidade = it)))
            }, numerico = true)
            SeletorDeOpcao(
                rotulo = "Onde vai ficar",
                opcoes = estado.configuracao.locais,
                selecionado = rascunho.localizacao,
                textoDe = { it.descricao() },
                aoSelecionar = { aoMudar(item.copy(rascunho = rascunho.copy(localizacao = it))) },
            )

            CartaoDaLeitura(item.etiqueta)

            if (!validacao.valido) {
                Text(
                    "Falta: ${validacao.resumoDoQueFalta}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Soma de conferencia usada nas telas: quanto o sistema espera em cada local do lote. */
fun esperadoNoLocal(lote: Lote, local: Localizacao?): Quantidade =
    local?.let { lote.quantidadeEm(it) } ?: lote.quantidadeAtual
