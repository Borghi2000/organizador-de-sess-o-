package br.com.borghi.estoquechocolate.aviso

import android.content.Context

/**
 * Ajustes que sao do aparelho, nao do estoque: se o aviso da manha esta ligado e a chave de IA.
 *
 * Ficam fora do banco e fora do backup de proposito. A chave e sua, fica so neste aparelho, e nao
 * entra no JSON que voce compartilha nem no repositorio.
 */
class Preferencias(contexto: Context) {

    private val arquivo = contexto.applicationContext
        .getSharedPreferences("preferencias-estoque", Context.MODE_PRIVATE)

    var avisoDiarioLigado: Boolean
        get() = arquivo.getBoolean(AVISO, false)
        set(valor) = arquivo.edit().putBoolean(AVISO, valor).apply()

    var horaDoAviso: Int
        get() = arquivo.getInt(HORA, 8)
        set(valor) = arquivo.edit().putInt(HORA, valor.coerceIn(0, 23)).apply()

    var ajudaPorIaLigada: Boolean
        get() = arquivo.getBoolean(IA_LIGADA, false)
        set(valor) = arquivo.edit().putBoolean(IA_LIGADA, valor).apply()

    var chaveDaIa: String
        get() = arquivo.getString(IA_CHAVE, "").orEmpty()
        set(valor) = arquivo.edit().putString(IA_CHAVE, valor.trim()).apply()

    val temChave: Boolean get() = chaveDaIa.isNotBlank()

    /** A IA so entra se estiver ligada E com chave. O padrao e desligada. */
    val iaDisponivel: Boolean get() = ajudaPorIaLigada && temChave

    companion object {
        private const val AVISO = "aviso-diario"
        private const val HORA = "hora-do-aviso"
        private const val IA_LIGADA = "ia-ligada"
        private const val IA_CHAVE = "ia-chave"
    }
}
