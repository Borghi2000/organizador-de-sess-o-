package br.com.borghi.estoquechocolate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import br.com.borghi.estoquechocolate.core.regras.ClassificacaoValidade

private val MarromEscuro = Color(0xFF4E342E)
private val MarromClaro = Color(0xFFD7CCC8)
private val Caramelo = Color(0xFF8D6E63)
private val CarameloClaro = Color(0xFFBCAAA4)

private val EsquemaClaro = lightColorScheme(
    primary = MarromEscuro,
    onPrimary = Color.White,
    primaryContainer = MarromClaro,
    onPrimaryContainer = Color(0xFF2B1A15),
    secondary = Caramelo,
    onSecondary = Color.White,
    surfaceVariant = Color(0xFFF2EAE6),
    onSurfaceVariant = Color(0xFF4A413E),
)

private val EsquemaEscuro = darkColorScheme(
    primary = CarameloClaro,
    onPrimary = Color(0xFF2B1A15),
    primaryContainer = Color(0xFF5D4037),
    onPrimaryContainer = MarromClaro,
    secondary = Caramelo,
    onSecondary = Color.White,
)

/**
 * Cores de severidade. Sao lidas de longe, em pe, no corredor da loja: vermelho para o que ja
 * deu errado, laranja para o que da errado esta semana, e assim por diante.
 */
data class CoresDeSeveridade(val fundo: Color, val texto: Color)

@Composable
fun coresDa(classificacao: ClassificacaoValidade): CoresDeSeveridade {
    val escuro = isSystemInDarkTheme()
    return when (classificacao) {
        ClassificacaoValidade.VENCIDO ->
            if (escuro) CoresDeSeveridade(Color(0xFF5C1A15), Color(0xFFFFB4AB))
            else CoresDeSeveridade(Color(0xFFFFDAD6), Color(0xFF8C1D18))
        ClassificacaoValidade.CRITICO ->
            if (escuro) CoresDeSeveridade(Color(0xFF5A2E00), Color(0xFFFFCC99))
            else CoresDeSeveridade(Color(0xFFFFE0C2), Color(0xFF8A3D00))
        ClassificacaoValidade.URGENTE ->
            if (escuro) CoresDeSeveridade(Color(0xFF52440A), Color(0xFFFFE08A))
            else CoresDeSeveridade(Color(0xFFFFF2C2), Color(0xFF6B5600))
        ClassificacaoValidade.ATENCAO ->
            if (escuro) CoresDeSeveridade(Color(0xFF10344F), Color(0xFFAFD3F2))
            else CoresDeSeveridade(Color(0xFFD9ECFB), Color(0xFF0D4F73))
        ClassificacaoValidade.MONITORAR, ClassificacaoValidade.NORMAL ->
            if (escuro) CoresDeSeveridade(Color(0xFF16351F), Color(0xFFA8D5B5))
            else CoresDeSeveridade(Color(0xFFDDF0E2), Color(0xFF1B5E28))
    }
}

/**
 * Cor nao pode ser o unico sinal: vermelho, laranja e amarelo somem sob a luz forte da loja e nao
 * significam nada para quem tem daltonismo. Cada faixa carrega tambem um simbolo.
 */
fun simboloDa(classificacao: ClassificacaoValidade): String = when (classificacao) {
    ClassificacaoValidade.VENCIDO -> "X"
    ClassificacaoValidade.CRITICO -> "!!"
    ClassificacaoValidade.URGENTE -> "!"
    ClassificacaoValidade.ATENCAO -> "~"
    ClassificacaoValidade.MONITORAR -> "-"
    ClassificacaoValidade.NORMAL -> "OK"
}

@Composable
fun TemaEstoque(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) EsquemaEscuro else EsquemaClaro,
        content = conteudo,
    )
}
