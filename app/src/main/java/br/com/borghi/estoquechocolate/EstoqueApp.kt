package br.com.borghi.estoquechocolate

import android.app.Application
import br.com.borghi.estoquechocolate.dados.BancoDeDados
import br.com.borghi.estoquechocolate.dados.EstoqueRepositorio

class EstoqueApp : Application() {

    val repositorio: EstoqueRepositorio by lazy {
        EstoqueRepositorio(BancoDeDados.obter(this).estoqueDao())
    }
}
