package br.com.borghi.estoquechocolate.dados

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * As tabelas guardam apenas tipos primitivos. A conversao para os modelos do core fica em
 * Mapeadores.kt, para o banco nao depender das regras e as regras nao dependerem do banco.
 */

@Entity(tableName = "produtos")
data class ProdutoEntity(
    @PrimaryKey val codigo: String,
    val nome: String,
    val categoria: String,
    val unidade: String,
    val estoqueMinimo: Long,
    val estoqueMaximo: Long,
    val localPadraoNome: String,
    val localPadraoTipo: String,
    val observacoes: String,
    val codigoBarras: String = "",
    val foto: String = "",
)

@Entity(
    tableName = "lotes",
    indices = [Index("produtoCodigo"), Index(value = ["produtoCodigo", "codigoLote"], unique = true)],
)
data class LoteEntity(
    @PrimaryKey val id: String,
    val produtoCodigo: String,
    val codigoLote: String,
    val validade: String,
    val quantidadeRecebida: Long,
    val dataUltimaConferencia: String?,
    val observacoes: String,
    val segregado: Boolean,
    val motivoSegregacao: String?,
    val fotoEtiqueta: String = "",
)

@Entity(
    tableName = "posicoes",
    primaryKeys = ["loteId", "localNome"],
    indices = [Index("loteId")],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PosicaoEntity(
    val loteId: String,
    val localNome: String,
    val localTipo: String,
    val quantidade: Long,
)

@Entity(tableName = "movimentacoes", indices = [Index("dataHora"), Index("loteId"), Index("produtoCodigo")])
data class MovimentacaoEntity(
    @PrimaryKey val id: String,
    val dataHora: String,
    val produtoCodigo: String,
    val loteId: String,
    val codigoLote: String,
    val tipo: String,
    val quantidade: Long,
    val origemNome: String?,
    val origemTipo: String?,
    val destinoNome: String?,
    val destinoTipo: String?,
    val observacao: String,
    val divergenciaId: String?,
)

@Entity(tableName = "divergencias", indices = [Index("status"), Index("loteId")])
data class DivergenciaEntity(
    @PrimaryKey val id: String,
    val data: String,
    val produtoCodigo: String,
    val loteId: String,
    val codigoLote: String,
    val localNome: String,
    val localTipo: String,
    val quantidadeEsperada: Long,
    val quantidadeContada: Long,
    val observacao: String,
    val status: String,
    val dataResolucao: String?,
    val observacaoResolucao: String,
)

@Entity(tableName = "conferencias", indices = [Index("data")])
data class ConferenciaEntity(
    @PrimaryKey val id: String,
    val data: String,
    val status: String,
    val observacao: String,
)

@Entity(
    tableName = "itens_conferencia",
    primaryKeys = ["conferenciaId", "loteId", "localNome"],
    indices = [Index("conferenciaId")],
    foreignKeys = [
        ForeignKey(
            entity = ConferenciaEntity::class,
            parentColumns = ["id"],
            childColumns = ["conferenciaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ItemConferenciaEntity(
    val conferenciaId: String,
    val loteId: String,
    val codigoLote: String,
    val produtoCodigo: String,
    val localNome: String,
    val localTipo: String,
    val quantidadeEsperada: Long,
    val quantidadeContada: Long,
    val divergenciaId: String?,
)

@Entity(tableName = "locais")
data class LocalEntity(
    @PrimaryKey val nome: String,
    val tipo: String,
)

@Entity(tableName = "configuracao")
data class ConfiguracaoEntity(
    @PrimaryKey val id: Int = 1,
    val diasSemConferencia: Int,
)

data class LoteComPosicoes(
    @Embedded val lote: LoteEntity,
    @Relation(parentColumn = "id", entityColumn = "loteId") val posicoes: List<PosicaoEntity>,
)

data class ConferenciaComItens(
    @Embedded val conferencia: ConferenciaEntity,
    @Relation(parentColumn = "id", entityColumn = "conferenciaId") val itens: List<ItemConferenciaEntity>,
)
