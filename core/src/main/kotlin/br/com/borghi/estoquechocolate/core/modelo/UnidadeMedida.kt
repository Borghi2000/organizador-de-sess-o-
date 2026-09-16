package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.Serializable

@Serializable
enum class UnidadeMedida(val rotulo: String, val abreviacao: String, val fracionaria: Boolean) {
    UNIDADE("Unidade", "un", false),
    CAIXA("Caixa", "cx", false),
    PACOTE("Pacote", "pct", false),
    DISPLAY("Display", "disp", false),
    QUILO("Quilo", "kg", true),
    GRAMA("Grama", "g", false),
    LITRO("Litro", "L", true),
}
