package br.com.borghi.estoquechocolate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.borghi.estoquechocolate.ui.AplicativoEstoque
import br.com.borghi.estoquechocolate.ui.EstoqueViewModel
import br.com.borghi.estoquechocolate.ui.TemaEstoque

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repositorio = (application as EstoqueApp).repositorio

        setContent {
            TemaEstoque {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val vm: EstoqueViewModel = viewModel(factory = EstoqueViewModel.Fabrica(repositorio))
                    AplicativoEstoque(vm)
                }
            }
        }
    }
}
