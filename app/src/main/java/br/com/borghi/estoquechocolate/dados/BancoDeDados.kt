package br.com.borghi.estoquechocolate.dados

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProdutoEntity::class,
        LoteEntity::class,
        PosicaoEntity::class,
        MovimentacaoEntity::class,
        DivergenciaEntity::class,
        ConferenciaEntity::class,
        ItemConferenciaEntity::class,
        LocalEntity::class,
        ConfiguracaoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BancoDeDados : RoomDatabase() {

    abstract fun estoqueDao(): EstoqueDao

    companion object {
        @Volatile
        private var instancia: BancoDeDados? = null

        fun obter(contexto: Context): BancoDeDados = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(
                contexto.applicationContext,
                BancoDeDados::class.java,
                "estoque-chocolate.db",
            )
                // Sem fallback destrutivo: perder o estoque numa atualizacao do app seria pior
                // que o app nem abrir. Cada mudanca de schema entra aqui como migracao.
                .build()
                .also { instancia = it }
        }
    }
}
