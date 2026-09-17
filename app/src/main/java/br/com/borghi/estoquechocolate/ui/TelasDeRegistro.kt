package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import br.com.borghi.estoquechocolate.core.modelo.Localizacao
import br.com.borghi.estoquechocolate.core.modelo.Lote
import br.com.borghi.estoquechocolate.core.modelo.MotivoSegregacao
import br.com.borghi.estoquechocolate.core.modelo.Produto
import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.TipoLocal
import br.com.borghi.estoquechocolate.core.modelo.TipoMovimentacao
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade
import br.com.borghi.estoquechocolate.core.regras.Contagens
import br.com.borghi.estoquechocolate.core.regras.LinhaConfirmacao
import br.com.borghi.estoquechocolate.core.regras.Pvps
import br.com.borghi.estoquechocolate.core.regras.RascunhoContagem
import br.com.borghi.estoquechocolate.camera.LeitorDeEtiqueta
import br.com.borghi.estoquechocolate.core.leitura.CampoEtiqueta
import br.com.borghi.estoquechocolate.core.leitura.EtiquetaLida
import br.com.borghi.estoquechocolate.core.leitura.GuardasDeLeitura
import br.com.borghi.estoquechocolate.core.leitura.InterpretadorDeEtiqueta
import br.com.borghi.estoquechocolate.core.regras.RascunhoEntrada
import br.com.borghi.estoquechocolate.core.regras.RascunhoMovimentacao
import br.com.borghi.estoquechocolate.dados.EstadoDoEstoque

/** Descricao curta de um lote, usada nas listas de escolha. */
fun descreverLote(lote: Lote, estado: EstadoDoEstoque, hoje: java.time.LocalDate): String {
    val unidade = estado.produto(lote.produtoCodigo)?.unidade
    val quantidade = unidade?.let { lote.quantidadeAtual.formatar(it) } ?: lote.quantidadeAtual.formatar()
    return "Lote ${lote.codigoLote} - $quantidade - ${ClassificacaoValidade.descreverPrazo(lote.validade, hoje)}"
}

@Composable
private fun BlocoDeErros(erros: List<String>) {
    if (erros.isEmpty()) return
    CartaoSimples {
        Text("Faltam dados para salvar:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        erros.forEach { Text("- $it", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun RegistrarEntradaTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    var produto by remember { mutableStateOf<Produto?>(null) }
    var codigoLote by remember { mutableStateOf("") }
    var validadeTexto by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<Localizacao?>(null) }
    var observacoes by remember { mutableStateOf("") }
    var confirmando by remember { mutableStateOf(false) }
    var conflito by remember { mutableStateOf<String?>(null) }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }
    var lendoEtiqueta by remember { mutableStateOf(false) }
    var fotoEtiqueta by remember { mutableStateOf("") }
    var leitura by remember { mutableStateOf<EtiquetaLida?>(null) }
    var avisosDaLeitura by remember { mutableStateOf<List<String>>(emptyList()) }

    val rascunho = RascunhoEntrada(
        produtoCodigo = produto?.codigo ?: "",
        codigoLote = codigoLote,
        validade = lerData(validadeTexto),
        quantidade = quantidade,
        localizacao = local,
        observacoes = observacoes,
        fotoEtiqueta = fotoEtiqueta,
    )

    if (lendoEtiqueta) {
        LeitorDeEtiqueta(
            aoLer = { foto ->
                val etiqueta = InterpretadorDeEtiqueta.interpretar(foto.texto, hoje)
                val achado = etiqueta.codigoBarras?.let { vm.produtoPorCodigoDeBarras(it.valor) }
                if (achado != null) {
                    produto = achado
                    if (local == null) local = achado.localPadrao
                }
                val alvo = achado ?: produto
                val guardas = GuardasDeLeitura.conferir(
                    etiqueta = etiqueta,
                    lotesDoProduto = alvo?.let { estado.lotesDoProduto(it.codigo) } ?: emptyList(),
                    hoje = hoje,
                )
                val segurados = guardas.filter { it.seguraOPreenchimento }.map { it.campo }.toSet()

                // So preenche o que a leitura sustenta. Campo duvidoso fica em branco, para
                // aparecer vazio na tela em vez de entrar errado no estoque.
                etiqueta.lote
                    ?.takeIf { it.confiavel && CampoEtiqueta.LOTE !in segurados }
                    ?.let { codigoLote = it.valor }
                etiqueta.validade
                    ?.takeIf { it.confiavel && CampoEtiqueta.VALIDADE !in segurados }
                    ?.let { validadeTexto = formatarData(it.valor) }

                fotoEtiqueta = foto.arquivo
                leitura = etiqueta
                avisosDaLeitura = etiqueta.avisos + guardas.map { it.mensagem }
                lendoEtiqueta = false
            },
            aoDigitarNaMao = { lendoEtiqueta = false },
            aoVoltar = { lendoEtiqueta = false },
        )
        return
    }
    val validacao = rascunho.validar(hoje)

    TelaBase("Registrar entrada", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text("Mercadoria que esta chegando na secao.", style = MaterialTheme.typography.bodyMedium)

            Button(onClick = { lendoEtiqueta = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Ler etiqueta com a camera")
            }
            leitura?.let { CartaoDaLeitura(it) }
            if (avisosDaLeitura.isNotEmpty()) {
                avisosDaLeitura.forEach {
                    Text("- $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            SeletorDeOpcao(
                rotulo = "Produto",
                opcoes = estado.produtos,
                selecionado = produto,
                textoDe = { "${it.codigo} - ${it.nome}" },
                aoSelecionar = {
                    produto = it
                    if (local == null) local = it.localPadrao
                },
                vazio = "Cadastre um produto antes de registrar entrada",
            )
            CampoTexto("Codigo do lote", codigoLote, { codigoLote = it }, apoio = "Como esta impresso na caixa")
            CampoTexto(
                "Validade",
                validadeTexto,
                { validadeTexto = it },
                numerico = true,
                apoio = "Digite dia, mes e ano. Ex.: 31122026",
                erro = if (validadeTexto.isNotBlank() && lerData(validadeTexto) == null) "Data invalida" else null,
            )
            CampoTexto(
                "Quantidade recebida",
                quantidade,
                { quantidade = it },
                numerico = true,
                apoio = produto?.let { "Em ${it.unidade.rotulo.lowercase()}" },
            )
            SeletorDeOpcao(
                rotulo = "Onde vai ficar",
                opcoes = estado.configuracao.locais,
                selecionado = local,
                textoDe = { it.descricao() },
                aoSelecionar = { local = it },
            )
            CampoTexto("Observacoes", observacoes, { observacoes = it }, linhasUnicas = false)

            BlocoDeErros(erros)

            Button(onClick = { confirmando = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Revisar e salvar")
            }
        }
    }

    if (confirmando) {
        DialogoDeConfirmacao(
            titulo = "Confirmar entrada",
            linhas = rascunho.paraConfirmacao(produto?.nome ?: "", produto?.unidade),
            avisos = validacao.avisos,
            faltantes = validacao.faltantes.map { it.mensagem },
            aoConfirmar = {
                confirmando = false
                vm.registrarEntrada(rascunho) { resultado ->
                    when (resultado) {
                        is ResultadoAcao.Sucesso -> {
                            vm.avisar(resultado.textoCompleto())
                            voltar()
                        }
                        is ResultadoAcao.Bloqueado -> erros = resultado.faltantes
                        is ResultadoAcao.Conflito -> conflito = resultado.mensagem
                    }
                }
            },
            aoCancelar = { confirmando = false },
        )
    }

    conflito?.let { mensagem ->
        AlertDialog(
            onDismissRequest = { conflito = null },
            title = { Text("Confira o lote") },
            text = { Text(mensagem) },
            confirmButton = { TextButton(onClick = { conflito = null }) { Text("Corrigir os dados") } },
        )
    }
}

@Composable
fun RegistrarReposicaoTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    var produto by remember { mutableStateOf<Produto?>(null) }
    var quantidade by remember { mutableStateOf("") }
    var destino by remember { mutableStateOf<Localizacao?>(null) }
    var observacao by remember { mutableStateOf("") }
    var confirmando by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }

    val lotes = produto?.let { estado.lotesDoProduto(it.codigo) }.orEmpty()
    val plano = produto?.let {
        Quantidade.deTexto(quantidade)?.let { qtd ->
            Pvps.planejarConsumo(lotes, qtd, hoje, TipoLocal.DEPOSITO)
        }
    }

    TelaBase("Registrar reposicao", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text(
                "Leva produto do deposito para a exposicao. O app escolhe sozinho o lote que vence primeiro.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SeletorDeOpcao(
                rotulo = "Produto",
                opcoes = estado.produtos,
                selecionado = produto,
                textoDe = { "${it.codigo} - ${it.nome}" },
                aoSelecionar = {
                    produto = it
                    if (destino == null) destino = it.localPadrao
                },
            )
            CampoTexto("Quantidade a repor", quantidade, { quantidade = it }, numerico = true)
            SeletorDeOpcao(
                rotulo = "Para onde",
                opcoes = estado.configuracao.locais.filter { it.tipo == TipoLocal.EXPOSICAO },
                selecionado = destino,
                textoDe = { it.descricao() },
                aoSelecionar = { destino = it },
            )
            CampoTexto("Observacao", observacao, { observacao = it })

            if (plano != null && plano.itens.isNotEmpty()) {
                CartaoSimples {
                    Text("Sera retirado do deposito assim:", fontWeight = FontWeight.SemiBold)
                    plano.itens.forEach { item ->
                        Text(
                            "- ${item.quantidade.formatar()} do lote ${item.lote.codigoLote} " +
                                "(${ClassificacaoValidade.descreverPrazo(item.lote.validade, hoje)})",
                        )
                    }
                    if (!plano.atendeTotalmente) {
                        Text(
                            "Faltam ${plano.faltante.formatar()} no deposito.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            BlocoDeErros(erros)

            Button(onClick = { confirmando = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Revisar e salvar")
            }
        }
    }

    if (confirmando) {
        val linhas = buildList {
            add(LinhaConfirmacao("Produto", produto?.nome ?: "-"))
            add(LinhaConfirmacao("Quantidade", quantidade.ifBlank { "-" }))
            add(LinhaConfirmacao("Para", destino?.descricao() ?: "-"))
            plano?.itens?.forEach { add(LinhaConfirmacao("Lote ${it.lote.codigoLote}", it.quantidade.formatar())) }
        }
        DialogoDeConfirmacao(
            titulo = "Confirmar reposicao",
            linhas = linhas,
            avisos = if (plano != null && !plano.atendeTotalmente) {
                listOf("O deposito nao cobre tudo: faltam ${plano.faltante.formatar()}.")
            } else {
                emptyList()
            },
            aoConfirmar = {
                confirmando = false
                vm.registrarReposicao(produto?.codigo ?: "", quantidade, destino, observacao) { resultado ->
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
fun RegistrarPerdaTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    val tipos = listOf(
        TipoMovimentacao.PERDA_VENCIMENTO,
        TipoMovimentacao.PERDA_AVARIA,
        TipoMovimentacao.DEGUSTACAO,
    )
    var tipo by remember { mutableStateOf(TipoMovimentacao.PERDA_VENCIMENTO) }
    var lote by remember { mutableStateOf<Lote?>(null) }
    var origem by remember { mutableStateOf<Localizacao?>(null) }
    var quantidade by remember { mutableStateOf("") }
    var observacao by remember { mutableStateOf("") }
    var confirmando by remember { mutableStateOf(false) }
    var segregando by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }

    // Ordem PVPS: o vencido e o que vence primeiro aparecem no topo da lista, que e justamente
    // o que se procura ao registrar uma perda.
    val lotesOrdenados = Pvps.ordenar(estado.lotes.filter { it.quantidadeAtual.ehPositiva })

    val rascunho = RascunhoMovimentacao(
        tipo = tipo,
        loteId = lote?.id ?: "",
        quantidade = quantidade,
        localOrigem = origem,
        observacao = observacao,
    )

    TelaBase("Registrar perda", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            SeletorDeOpcao(
                rotulo = "Tipo",
                opcoes = tipos,
                selecionado = tipo,
                textoDe = { it.rotulo },
                aoSelecionar = { tipo = it },
            )
            SeletorDeOpcao(
                rotulo = "Lote",
                opcoes = lotesOrdenados,
                selecionado = lote,
                textoDe = { "${estado.nomeDoProduto(it.produtoCodigo)} - ${descreverLote(it, estado, hoje)}" },
                aoSelecionar = {
                    lote = it
                    origem = it.posicoes.maxByOrNull { p -> p.quantidade.milesimos }?.localizacao
                },
                vazio = "Nenhum lote com estoque",
            )
            lote?.let { escolhido ->
                SeletorDeOpcao(
                    rotulo = "De qual local",
                    opcoes = escolhido.posicoes.map { it.localizacao },
                    selecionado = origem,
                    textoDe = { local ->
                        "${local.descricao()} - ${escolhido.quantidadeEm(local).formatar()}"
                    },
                    aoSelecionar = { origem = it },
                )
            }
            CampoTexto("Quantidade", quantidade, { quantidade = it }, numerico = true)
            CampoTexto(
                "Motivo",
                observacao,
                { observacao = it },
                linhasUnicas = false,
                apoio = if (tipo.exigeObservacao) "Obrigatorio para ${tipo.rotulo.lowercase()}" else null,
            )

            BlocoDeErros(erros)

            Button(onClick = { confirmando = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Revisar e salvar")
            }

            lote?.let { escolhido ->
                if (!escolhido.segregado) {
                    TextButton(onClick = { segregando = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Segregar o lote inteiro em vez de dar baixa")
                    }
                }
            }
        }
    }

    if (confirmando) {
        val validacao = rascunho.validar()
        DialogoDeConfirmacao(
            titulo = "Confirmar ${tipo.rotulo.lowercase()}",
            linhas = rascunho.paraConfirmacao(
                descricaoLote = lote?.let { "${estado.nomeDoProduto(it.produtoCodigo)} - lote ${it.codigoLote}" } ?: "-",
                unidade = lote?.let { estado.produto(it.produtoCodigo)?.unidade },
            ),
            faltantes = validacao.faltantes.map { it.mensagem },
            aoConfirmar = {
                confirmando = false
                vm.registrarBaixa(rascunho) { resultado ->
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

    if (segregando) {
        val escolhido = lote
        AlertDialog(
            onDismissRequest = { segregando = false },
            title = { Text("Segregar lote") },
            text = {
                Text(
                    "Todo o lote ${escolhido?.codigoLote} sai da venda e vai para a area de segregacao. " +
                        "A quantidade continua no estoque fisico ate voce dar baixa da perda.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        segregando = false
                        val motivo = if (escolhido != null && escolhido.estaVencido(hoje)) {
                            MotivoSegregacao.VENCIDO
                        } else {
                            MotivoSegregacao.AVARIA
                        }
                        vm.segregarLote(escolhido?.id ?: "", motivo, observacao) { resultado ->
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
                ) { Text("Segregar") }
            },
            dismissButton = { TextButton(onClick = { segregando = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun RegistrarContagemTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    var lote by remember { mutableStateOf<Lote?>(null) }
    var local by remember { mutableStateOf<Localizacao?>(null) }
    var contada by remember { mutableStateOf("") }
    var observacao by remember { mutableStateOf("") }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }

    val sugestao = Contagens.sugestaoDoDia(estado.lotes, hoje, estado.configuracao.diasSemConferencia)
    val conferencia = vm.conferenciaDoDia()

    TelaBase("Registrar contagem", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            Text(
                "Conte o que esta na prateleira e digite o numero. Se nao bater, o app abre uma " +
                    "divergencia e NAO mexe no estoque.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (sugestao.isNotEmpty()) {
                CartaoSimples {
                    Text("Sugestao de hoje", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${sugestao.size} lote(s) sem conferencia ha mais de " +
                            "${estado.configuracao.diasSemConferencia} dia(s).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SeletorDeOpcao(
                rotulo = "Lote conferido",
                opcoes = (sugestao + Pvps.ordenar(estado.lotes)).distinct(),
                selecionado = lote,
                textoDe = { "${estado.nomeDoProduto(it.produtoCodigo)} - ${descreverLote(it, estado, hoje)}" },
                aoSelecionar = {
                    lote = it
                    local = it.posicoes.maxByOrNull { p -> p.quantidade.milesimos }?.localizacao
                        ?: estado.produto(it.produtoCodigo)?.localPadrao
                },
                vazio = "Nenhum lote cadastrado",
            )

            lote?.let { escolhido ->
                SeletorDeOpcao(
                    rotulo = "Local conferido",
                    opcoes = estado.configuracao.locais,
                    selecionado = local,
                    textoDe = { it.descricao() },
                    aoSelecionar = { local = it },
                )
                local?.let { onde ->
                    CartaoSimples {
                        Text("O sistema espera encontrar aqui:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            escolhido.quantidadeEm(onde).formatar(
                                estado.produto(escolhido.produtoCodigo)?.unidade
                                    ?: br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida.UNIDADE,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            CampoTexto("Quantidade contada", contada, { contada = it }, numerico = true)
            CampoTexto("Observacao", observacao, { observacao = it })

            BlocoDeErros(erros)

            Button(
                onClick = {
                    val rascunho = RascunhoContagem(lote?.id ?: "", local, contada, observacao)
                    vm.registrarContagem(rascunho) { resultado ->
                        when (resultado) {
                            is ResultadoAcao.Sucesso -> {
                                vm.avisar(resultado.textoCompleto())
                                // Fica na tela: numa conferencia se conta um item atras do outro.
                                contada = ""
                                observacao = ""
                                lote = null
                                erros = emptyList()
                            }
                            is ResultadoAcao.Bloqueado -> erros = resultado.faltantes
                            is ResultadoAcao.Conflito -> erros = listOf(resultado.mensagem)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar contagem e contar o proximo")
            }

            Text(
                "Contagens de hoje: ${conferencia.itens.size}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun FecharConferenciaTela(vm: EstoqueViewModel, estado: EstadoDoEstoque, voltar: () -> Unit) {
    val hoje = vm.hoje()
    val conferencia = vm.conferenciaDoDia()
    var observacao by remember { mutableStateOf("") }
    var confirmando by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf<List<String>>(emptyList()) }

    val contados = conferencia.lotesConferidos
    val naoConferidos = Pvps.ordenar(estado.lotes.filter { it.id !in contados && it.quantidadeAtual.ehPositiva })
    val abertas = estado.divergencias.count { it.estaAberta }

    TelaBase("Fechar conferencia do dia", aoVoltar = voltar) { padding ->
        ColunaRolavel(padding) {
            CartaoSimples {
                Text("Conferencia de ${formatarData(hoje)}", fontWeight = FontWeight.SemiBold)
                Text("${conferencia.itens.size} item(ns) contados")
                Text("${conferencia.totalComDivergencia} com divergencia")
                Text("$abertas divergencia(s) aberta(s) no total")
            }

            if (naoConferidos.isNotEmpty()) {
                CartaoSimples {
                    Text(
                        "${naoConferidos.size} lote(s) com estoque nao foram contados hoje:",
                        fontWeight = FontWeight.SemiBold,
                    )
                    naoConferidos.take(15).forEach {
                        Text("- ${estado.nomeDoProduto(it.produtoCodigo)}: ${descreverLote(it, estado, hoje)}")
                    }
                    if (naoConferidos.size > 15) Text("... e mais ${naoConferidos.size - 15}")
                }
            }

            CampoTexto("Observacao do dia", observacao, { observacao = it }, linhasUnicas = false)

            BlocoDeErros(erros)

            Button(onClick = { confirmando = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Fechar conferencia")
            }
        }
    }

    if (confirmando) {
        DialogoDeConfirmacao(
            titulo = "Fechar a conferencia de hoje",
            linhas = listOf(
                LinhaConfirmacao("Data", formatarData(hoje)),
                LinhaConfirmacao("Itens contados", "${conferencia.itens.size}"),
                LinhaConfirmacao("Com divergencia", "${conferencia.totalComDivergencia}"),
                LinhaConfirmacao("Sem contagem hoje", "${naoConferidos.size} lote(s)"),
            ),
            avisos = buildList {
                if (naoConferidos.isNotEmpty()) {
                    add("Os lotes nao contados continuarao aparecendo como sem conferencia recente.")
                }
                if (abertas > 0) add("$abertas divergencia(s) seguem abertas e precisam de ajuste.")
            },
            textoConfirmar = "Fechar o dia",
            aoConfirmar = {
                confirmando = false
                vm.fecharConferenciaDoDia(observacao) { resultado ->
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
