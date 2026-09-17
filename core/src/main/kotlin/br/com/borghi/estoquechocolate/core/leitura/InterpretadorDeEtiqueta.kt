package br.com.borghi.estoquechocolate.core.leitura

import br.com.borghi.estoquechocolate.core.modelo.Quantidade
import br.com.borghi.estoquechocolate.core.modelo.UnidadeMedida
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Le o texto cru que o OCR devolveu e tenta achar validade, lote, fabricacao, peso e codigo de
 * barras nos formatos que as etiquetas brasileiras usam.
 *
 * Tudo aqui e Kotlin puro de proposito: e a peca da qual a camera inteira depende, entao ela
 * precisa ser testavel sem aparelho, sem camera e sem rede.
 *
 * O interpretador nunca completa o que nao leu. Quando a leitura e duvidosa, ele devolve o campo
 * com [Confianca.BAIXA] e um aviso, e a tela deixa o campo em branco e destacado.
 */
object InterpretadorDeEtiqueta {

    private const val ANOS_DE_VALIDADE_PLAUSIVEL = 3L

    fun interpretar(texto: String, hoje: LocalDate): EtiquetaLida {
        if (texto.isBlank()) return EtiquetaLida.vazia(texto, "Nao consegui ler nada na foto")

        val normalizado = normalizar(texto)
        val avisos = mutableListOf<String>()
        val ocupado = mutableListOf<IntRange>()

        val datas = acharDatas(normalizado, hoje, ocupado, avisos)
        val codigoBarras = acharCodigoDeBarras(normalizado, ocupado)
        val lote = acharLote(normalizado, ocupado)
        val peso = acharPeso(normalizado, ocupado)

        var validade = datas.validade
        val fabricacao = datas.fabricacao

        // "FAB 01/01/26 - VALIDADE 12 MESES": a validade nao esta escrita, mas da para calcular.
        if (validade == null && fabricacao != null) {
            val meses = acharPrazoEmMeses(normalizado)
            if (meses != null) {
                val calculada = fabricacao.valor.plusMonths(meses)
                validade = Leitura(calculada, "${fabricacao.trecho} + $meses meses", Confianca.MEDIA)
                avisos += "A etiqueta nao traz a validade: calculei $meses meses a partir da fabricacao " +
                    "(${formatar(fabricacao.valor)}) e cheguei em ${formatar(calculada)}."
            }
        }

        validade = validade?.let { conferirPlausibilidade(it, hoje, avisos) }

        if (validade == null) avisos += "Nao achei a validade no texto lido. Digite a validade."
        if (lote == null) avisos += "Nao achei o codigo do lote no texto lido. Digite o lote."

        return EtiquetaLida(
            textoOriginal = texto,
            validade = validade,
            fabricacao = fabricacao,
            lote = lote,
            peso = peso,
            codigoBarras = codigoBarras,
            avisos = avisos.toList(),
        )
    }

    // ------------------------------------------------------------------ texto

    /**
     * Deixa o texto em caixa alta e sem acento, preservando o comprimento para as posicoes
     * continuarem valendo. Nao mexe em digito nenhum aqui: correcao de OCR e feita so dentro de
     * trechos que ja parecem data, para nao estragar codigo de lote.
     */
    internal fun normalizar(texto: String): String {
        val comAcento = "ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑáàâãäéèêëíìîïóòôõöúùûüçñ"
        val semAcento = "AAAAAEEEEIIIIOOOOOUUUUCNAAAAAEEEEIIIIOOOOOUUUUCN"
        return buildString(texto.length) {
            for (c in texto.uppercase()) {
                val indice = comAcento.indexOf(c)
                append(if (indice >= 0) semAcento[indice] else c)
            }
        }
    }

    /** Confusoes classicas de OCR em digito. So e aplicada onde o trecho ja tem cara de data. */
    private fun corrigirDigitos(trecho: String): Pair<String, Boolean> {
        val corrigido = buildString(trecho.length) {
            for (c in trecho) {
                append(
                    when (c) {
                        'O', 'Q', 'D' -> '0'
                        'I', 'L', '|' -> '1'
                        'S' -> '5'
                        'B' -> '8'
                        'Z' -> '2'
                        else -> c
                    }
                )
            }
        }
        return corrigido to (corrigido != trecho)
    }

    // ----------------------------------------------------------------- datas

    private data class DatasDaEtiqueta(
        val validade: Leitura<LocalDate>?,
        val fabricacao: Leitura<LocalDate>?,
    )

    private enum class Papel { VALIDADE, FABRICACAO, INDEFINIDO }

    private val ROTULOS: List<Pair<String, Papel>> = listOf(
        "CONSUMIR PREFERENCIALMENTE ANTES DE" to Papel.VALIDADE,
        "CONSUMIR ANTES DE" to Papel.VALIDADE,
        "DATA DE VALIDADE" to Papel.VALIDADE,
        "VALIDO ATE" to Papel.VALIDADE,
        "VALIDADE" to Papel.VALIDADE,
        "VENCIMENTO" to Papel.VALIDADE,
        "VENCE EM" to Papel.VALIDADE,
        "VENCE" to Papel.VALIDADE,
        "VENC" to Papel.VALIDADE,
        "VAL" to Papel.VALIDADE,
        "EXPIRA" to Papel.VALIDADE,
        "EXP" to Papel.VALIDADE,
        "BEST BEFORE" to Papel.VALIDADE,
        "DATA DE FABRICACAO" to Papel.FABRICACAO,
        "FABRICADO EM" to Papel.FABRICACAO,
        "FABRICACAO" to Papel.FABRICACAO,
        "FABR" to Papel.FABRICACAO,
        "FAB" to Papel.FABRICACAO,
        "PRODUCAO" to Papel.FABRICACAO,
        "PROD" to Papel.FABRICACAO,
    )

    private val MESES = listOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")

    private val DATA_COMPLETA = Regex("""(?<![\d])([\dOQDILSBZ|]{1,2})\s*[/.\-]\s*([\dOQDILSBZ|]{1,2})\s*[/.\-]\s*([\dOQDILSBZ|]{2,4})(?!\d)""")
    private val DATA_COM_MES_ESCRITO = Regex("""(?<!\d)(\d{1,2})\s*[ /.\-]\s*(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)[A-Z]*\.?\s*[ /.\-]?\s*(\d{2,4})(?!\d)""")
    private val MES_E_ANO = Regex("""(?<![\d/.\-])(\d{1,2})\s*[/.\-]\s*(\d{4}|\d{2})(?![\d/.\-])""")
    private val DATA_COMPACTA = Regex("""(?<!\d)(\d{2})(\d{2})(\d{4}|\d{2})(?!\d)""")

    private class DataCandidata(
        val leitura: Leitura<LocalDate>,
        val papel: Papel,
        val intervalo: IntRange,
        val soMesEAno: Boolean,
    )

    private fun acharDatas(
        texto: String,
        hoje: LocalDate,
        ocupado: MutableList<IntRange>,
        avisos: MutableList<String>,
    ): DatasDaEtiqueta {
        val candidatas = mutableListOf<DataCandidata>()

        fun registrar(candidata: DataCandidata) {
            if (livre(candidata.intervalo, ocupado)) {
                ocupado += candidata.intervalo
                candidatas += candidata
            }
        }

        for (m in DATA_COMPLETA.findAll(texto)) {
            val papel = papelDe(texto, m.range.first)
            val (dia, corrigiuDia) = corrigirDigitos(m.groupValues[1])
            val (mes, corrigiuMes) = corrigirDigitos(m.groupValues[2])
            val (ano, corrigiuAno) = corrigirDigitos(m.groupValues[3])
            val corrigiu = corrigiuDia || corrigiuMes || corrigiuAno
            val data = montar(
                dia = dia.toIntOrNull() ?: continue,
                mes = mes.toIntOrNull() ?: continue,
                anoBruto = ano.toIntOrNull() ?: continue,
                hoje = hoje,
                futuro = papel != Papel.FABRICACAO,
            ) ?: continue
            var leitura = Leitura(data, m.value.trim(), confiancaDe(papel))
            if (corrigiu) {
                leitura = leitura.rebaixar()
                avisos += "O texto \"${m.value.trim()}\" saiu com letra no lugar de numero; entendi " +
                    "como ${formatar(data)}. Confira."
            }
            avisarAnoExpandido(ano, data, avisos)
            registrar(DataCandidata(leitura, papel, m.range, soMesEAno = false))
        }

        for (m in DATA_COM_MES_ESCRITO.findAll(texto)) {
            val papel = papelDe(texto, m.range.first)
            val mes = MESES.indexOf(m.groupValues[2]) + 1
            val ano = m.groupValues[3]
            val data = montar(
                dia = m.groupValues[1].toIntOrNull() ?: continue,
                mes = mes,
                anoBruto = ano.toIntOrNull() ?: continue,
                hoje = hoje,
                futuro = papel != Papel.FABRICACAO,
            ) ?: continue
            avisarAnoExpandido(ano, data, avisos)
            registrar(DataCandidata(Leitura(data, m.value.trim(), confiancaDe(papel)), papel, m.range, false))
        }

        for (m in DATA_COMPACTA.findAll(texto)) {
            val papel = papelDe(texto, m.range.first)
            // Sem rotulo, seis digitos seguidos sao muito mais provavelmente um codigo de lote.
            if (papel == Papel.INDEFINIDO) continue
            val ano = m.groupValues[3]
            val data = montar(
                dia = m.groupValues[1].toIntOrNull() ?: continue,
                mes = m.groupValues[2].toIntOrNull() ?: continue,
                anoBruto = ano.toIntOrNull() ?: continue,
                hoje = hoje,
                futuro = papel != Papel.FABRICACAO,
            ) ?: continue
            avisarAnoExpandido(ano, data, avisos)
            registrar(DataCandidata(Leitura(data, m.value.trim(), Confianca.MEDIA), papel, m.range, false))
        }

        for (m in MES_E_ANO.findAll(texto)) {
            val papel = papelDe(texto, m.range.first)
            if (papel == Papel.INDEFINIDO) continue
            val mes = m.groupValues[1].toIntOrNull() ?: continue
            if (mes !in 1..12) continue
            val ano = expandirAno(m.groupValues[2].toIntOrNull() ?: continue, hoje, futuro = papel != Papel.FABRICACAO)
            val data = try {
                if (papel == Papel.FABRICACAO) LocalDate.of(ano, mes, 1)
                else LocalDate.of(ano, mes, 1).plusMonths(1).minusDays(1)
            } catch (e: DateTimeException) {
                continue
            }
            avisos += "A etiqueta traz so mes e ano (${m.value.trim()}); entendi ${formatar(data)}. Confira."
            registrar(DataCandidata(Leitura(data, m.value.trim(), Confianca.MEDIA), papel, m.range, soMesEAno = true))
        }

        return decidirPapeis(candidatas, avisos)
    }

    /**
     * Junta as datas achadas em "qual e a validade" e "qual e a fabricacao".
     *
     * Etiqueta com duas datas sem rotulo nenhum e o caso perigoso: a mais distante costuma ser a
     * validade, mas nao da para ter certeza, entao a leitura sai com confianca baixa.
     */
    private fun decidirPapeis(
        candidatas: List<DataCandidata>,
        avisos: MutableList<String>,
    ): DatasDaEtiqueta {
        val validadeRotulada = candidatas.filter { it.papel == Papel.VALIDADE }
        val fabricacaoRotulada = candidatas.filter { it.papel == Papel.FABRICACAO }
        val semRotulo = candidatas.filter { it.papel == Papel.INDEFINIDO }

        if (validadeRotulada.size > 1) {
            avisos += "Achei mais de uma data marcada como validade; usei a maior. Confira."
        }

        val validade = validadeRotulada.maxByOrNull { it.leitura.valor }?.leitura
            ?: when {
                semRotulo.isEmpty() -> null
                semRotulo.size == 1 && fabricacaoRotulada.isNotEmpty() -> semRotulo.single().leitura
                semRotulo.size == 1 -> semRotulo.single().leitura
                else -> {
                    avisos += "A etiqueta tem mais de uma data e nenhuma esta marcada como validade; " +
                        "chutei a mais distante, entao confira antes de salvar."
                    semRotulo.maxByOrNull { it.leitura.valor }!!.leitura.copy(confianca = Confianca.BAIXA)
                }
            }

        val fabricacao = fabricacaoRotulada.minByOrNull { it.leitura.valor }?.leitura
            ?: semRotulo
                .filter { it.leitura !== validade }
                .minByOrNull { it.leitura.valor }
                ?.leitura
                ?.copy(confianca = Confianca.BAIXA)

        return DatasDaEtiqueta(validade, fabricacao)
    }

    private fun confiancaDe(papel: Papel) = if (papel == Papel.INDEFINIDO) Confianca.MEDIA else Confianca.ALTA

    private fun montar(dia: Int, mes: Int, anoBruto: Int, hoje: LocalDate, futuro: Boolean): LocalDate? {
        if (dia !in 1..31 || mes !in 1..12) return null
        val ano = expandirAno(anoBruto, hoje, futuro)
        return try {
            LocalDate.of(ano, mes, dia)
        } catch (e: DateTimeException) {
            null
        }
    }

    /**
     * "31/12/26" vira 2026, nao 1926. Para validade procura o ano futuro mais proximo; para
     * fabricacao, o passado mais proximo. O ano expandido sempre aparece num aviso, para voce ver
     * o que o app entendeu em vez de descobrir depois.
     */
    internal fun expandirAno(bruto: Int, hoje: LocalDate, futuro: Boolean): Int {
        if (bruto >= 1000) return bruto
        if (bruto in 100..999) return bruto
        var ano = (hoje.year / 100) * 100 + bruto
        if (futuro && ano < hoje.year) ano += 100
        if (!futuro && ano > hoje.year) ano -= 100
        return ano
    }

    private fun avisarAnoExpandido(anoLido: String, data: LocalDate, avisos: MutableList<String>) {
        if (anoLido.length <= 2) {
            avisos += "O ano estava com dois digitos ($anoLido); entendi ${formatar(data)}."
        }
    }

    /**
     * Validade no passado ou muito longe nao e recusada: e segurada. A etiqueta pode estar certa
     * (mercadoria vencida chega mesmo), e nesse caso quem decide e voce, na tela de confirmacao.
     */
    private fun conferirPlausibilidade(
        leitura: Leitura<LocalDate>,
        hoje: LocalDate,
        avisos: MutableList<String>,
    ): Leitura<LocalDate> = when {
        leitura.valor.isBefore(hoje) -> {
            avisos += "A validade lida (${formatar(leitura.valor)}) ja passou. Confira a etiqueta " +
                "antes de dar entrada."
            leitura.copy(confianca = Confianca.BAIXA)
        }

        leitura.valor.isAfter(hoje.plusYears(ANOS_DE_VALIDADE_PLAUSIVEL)) -> {
            avisos += "A validade lida (${formatar(leitura.valor)}) esta a mais de " +
                "$ANOS_DE_VALIDADE_PLAUSIVEL anos daqui. Confira se o ano saiu certo."
            leitura.copy(confianca = Confianca.BAIXA)
        }

        else -> leitura
    }

    private val PRAZO_EM_MESES = Regex("""(?:VALIDADE|VAL|PRAZO|DURACAO)\s*[:.\-]?\s*(\d{1,2})\s*(?:MESES|MES|M)(?![A-Z])""")

    private fun acharPrazoEmMeses(texto: String): Long? =
        PRAZO_EM_MESES.find(texto)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in 1..60 }

    // ------------------------------------------------------------------ lote

    private val LOTE = Regex("""(?<![A-Z0-9])(?:LOTE|LOT|LT|L)\s*(?:N[º°O.]?\s*)?[:.\-]?\s*([A-Z0-9][A-Z0-9\-./]{0,14})(?![A-Z0-9])""")

    private fun acharLote(texto: String, ocupado: MutableList<IntRange>): Leitura<String>? {
        for (m in LOTE.findAll(texto)) {
            val bruto = m.groupValues[1].trim().trim('.', '-', '/')
            if (bruto.length < 2) continue
            if (bruto in MESES) continue
            // Codigo de lote sem nenhum digito quase nao existe, e sem essa exigencia o "L"
            // abreviado casa com qualquer palavra comecada por L ("...AO LEITE" viraria lote EITE).
            if (bruto.none { it.isDigit() }) continue
            // Um "lote" que e so uma data e a data da linha de cima grudada no rotulo errado.
            if (bruto.count { it == '/' || it == '.' || it == '-' } >= 2) continue
            val grupo = m.groups[1] ?: continue
            if (!livre(grupo.range, ocupado)) continue
            ocupado += m.range
            return Leitura(bruto, m.value.trim(), Confianca.ALTA)
        }
        return null
    }

    // ------------------------------------------------------------ codigo de barras

    private val SEQUENCIA_DE_DIGITOS = Regex("""(?<!\d)(\d{8}|\d{12,14})(?!\d)""")

    private fun acharCodigoDeBarras(texto: String, ocupado: MutableList<IntRange>): Leitura<String>? {
        for (m in SEQUENCIA_DE_DIGITOS.findAll(texto)) {
            if (!livre(m.range, ocupado)) continue
            val codigo = m.groupValues[1]
            if (!digitoVerificadorConfere(codigo)) continue
            ocupado += m.range
            return Leitura(codigo, m.value, Confianca.ALTA)
        }
        return null
    }

    /**
     * Digito verificador de EAN-8/EAN-13/UPC. Sem ele, qualquer sequencia de numeros da etiqueta
     * (peso, registro, telefone do SAC) viraria "codigo de barras".
     */
    fun digitoVerificadorConfere(codigo: String): Boolean {
        if (codigo.length !in listOf(8, 12, 13, 14)) return false
        if (!codigo.all { it.isDigit() }) return false
        val digitos = codigo.map { it - '0' }
        val corpo = digitos.dropLast(1).reversed()
        val soma = corpo.mapIndexed { indice, digito -> if (indice % 2 == 0) digito * 3 else digito }.sum()
        val esperado = (10 - soma % 10) % 10
        return esperado == digitos.last()
    }

    // ------------------------------------------------------------------ peso

    private val PESO = Regex("""(?<![\d,.])(\d{1,4}(?:[,.]\d{1,3})?)\s*(KG|KILOS?|QUILOS?|GRAMAS|GR|G|ML|LITROS?|LT|L|UNIDADES|UNID|UN)(?![A-Z])""")

    private fun acharPeso(texto: String, ocupado: MutableList<IntRange>): Leitura<PesoLido>? {
        for (m in PESO.findAll(texto)) {
            if (!livre(m.range, ocupado)) continue
            val numero = Quantidade.deTexto(m.groupValues[1]) ?: continue
            if (!numero.ehPositiva) continue
            val lido = when (m.groupValues[2]) {
                "KG", "KILO", "KILOS", "QUILO", "QUILOS" -> PesoLido(numero, UnidadeMedida.QUILO)
                "G", "GR", "GRAMAS" -> PesoLido(numero, UnidadeMedida.GRAMA)
                "L", "LT", "LITRO", "LITROS" -> PesoLido(numero, UnidadeMedida.LITRO)
                "ML" -> PesoLido(Quantidade(numero.milesimos / 1000), UnidadeMedida.LITRO)
                else -> PesoLido(numero, UnidadeMedida.UNIDADE)
            }
            ocupado += m.range
            return Leitura(lido, m.value.trim(), Confianca.MEDIA)
        }
        return null
    }

    // ---------------------------------------------------------------- apoio

    private fun livre(intervalo: IntRange, ocupado: List<IntRange>): Boolean =
        ocupado.none { it.first <= intervalo.last && intervalo.first <= it.last }

    private const val JANELA_DO_ROTULO = 40

    /** Acha o rotulo mais proximo antes da data, aceitando so espaco e pontuacao no meio. */
    private fun papelDe(texto: String, inicio: Int): Papel {
        val de = maxOf(0, inicio - JANELA_DO_ROTULO)
        var melhorFim = -1
        var melhorPapel = Papel.INDEFINIDO
        for ((rotulo, papel) in ROTULOS) {
            var posicao = texto.indexOf(rotulo, de)
            while (posicao >= 0 && posicao < inicio) {
                val fim = posicao + rotulo.length
                if (fim <= inicio && inicioDePalavra(texto, posicao) && soSeparadores(texto, fim, inicio)) {
                    if (fim > melhorFim) {
                        melhorFim = fim
                        melhorPapel = papel
                    }
                }
                posicao = texto.indexOf(rotulo, posicao + 1)
            }
        }
        return melhorPapel
    }

    private fun inicioDePalavra(texto: String, posicao: Int): Boolean =
        posicao == 0 || !texto[posicao - 1].isLetterOrDigit()

    private fun soSeparadores(texto: String, de: Int, ate: Int): Boolean {
        if (ate - de > 12) return false
        return (de until ate).all { texto[it] in " \t\n\r:;.,-=>()[]" }
    }

    private fun formatar(data: LocalDate): String =
        "%02d/%02d/%04d".format(data.dayOfMonth, data.monthValue, data.year)
}
