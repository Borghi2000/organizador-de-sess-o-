package br.com.borghi.estoquechocolate.dados

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class BancoDeDados : RoomDatabase() {

    abstract fun estoqueDao(): EstoqueDao

    companion object {

        /**
         * Camera: o produto passa a guardar o codigo de barras e uma foto, e o lote guarda a foto
         * da etiqueta que originou a entrada. Colunas novas com valor padrao vazio, para quem ja
         * tem estoque cadastrado nao perder nada.
         */
        val MIGRACAO_1_PARA_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE produtos ADD COLUMN codigoBarras TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE produtos ADD COLUMN foto TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lotes ADD COLUMN fotoEtiqueta TEXT NOT NULL DEFAULT ''")
            }
        }

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
                .addMigrations(MIGRACAO_1_PARA_2)
                .build()
                .also { instancia = it }
        }
    }
}
