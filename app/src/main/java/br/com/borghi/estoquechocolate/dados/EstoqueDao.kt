package br.com.borghi.estoquechocolate.dados

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EstoqueDao {

    @Query("SELECT * FROM produtos ORDER BY nome")
    abstract fun observarProdutos(): Flow<List<ProdutoEntity>>

    @Transaction
    @Query("SELECT * FROM lotes")
    abstract fun observarLotes(): Flow<List<LoteComPosicoes>>

    @Query("SELECT * FROM movimentacoes ORDER BY dataHora DESC")
    abstract fun observarMovimentacoes(): Flow<List<MovimentacaoEntity>>

    @Query("SELECT * FROM divergencias ORDER BY data DESC")
    abstract fun observarDivergencias(): Flow<List<DivergenciaEntity>>

    @Transaction
    @Query("SELECT * FROM conferencias ORDER BY data DESC")
    abstract fun observarConferencias(): Flow<List<ConferenciaComItens>>

    @Query("SELECT * FROM locais ORDER BY nome")
    abstract fun observarLocais(): Flow<List<LocalEntity>>

    @Query("SELECT * FROM configuracao WHERE id = 1")
    abstract fun observarConfiguracao(): Flow<ConfiguracaoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarProduto(produto: ProdutoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarProdutos(produtos: List<ProdutoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarLotes(lotes: List<LoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarPosicoes(posicoes: List<PosicaoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarMovimentacoes(movimentacoes: List<MovimentacaoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarDivergencias(divergencias: List<DivergenciaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarConferencia(conferencia: ConferenciaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarItensConferencia(itens: List<ItemConferenciaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarLocais(locais: List<LocalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarConfiguracao(configuracao: ConfiguracaoEntity)

    @Query("DELETE FROM posicoes WHERE loteId = :loteId")
    abstract suspend fun apagarPosicoesDoLote(loteId: String)

    @Query("DELETE FROM movimentacoes WHERE id IN (:ids)")
    abstract suspend fun apagarMovimentacoesPorId(ids: List<String>)

    @Query("DELETE FROM divergencias WHERE id IN (:ids)")
    abstract suspend fun apagarDivergenciasPorId(ids: List<String>)

    @Query("DELETE FROM itens_conferencia WHERE conferenciaId = :conferenciaId")
    abstract suspend fun apagarItensDaConferencia(conferenciaId: String)

    @Query("DELETE FROM produtos")
    abstract suspend fun apagarProdutos()

    @Query("DELETE FROM lotes")
    abstract suspend fun apagarLotes()

    @Query("DELETE FROM movimentacoes")
    abstract suspend fun apagarMovimentacoes()

    @Query("DELETE FROM divergencias")
    abstract suspend fun apagarDivergencias()

    @Query("DELETE FROM conferencias")
    abstract suspend fun apagarConferencias()

    @Query("SELECT COUNT(*) FROM produtos")
    abstract suspend fun contarProdutos(): Int

    /**
     * Grava o resultado de uma operacao inteira de uma vez.
     *
     * Ou lote, posicoes, movimentacoes e divergencias entram juntos, ou nada entra: nunca fica
     * uma movimentacao gravada sem a quantidade correspondente ter mudado.
     */
    @Transaction
    open suspend fun aplicar(
        lotes: List<LoteEntity> = emptyList(),
        posicoesPorLote: Map<String, List<PosicaoEntity>> = emptyMap(),
        movimentacoes: List<MovimentacaoEntity> = emptyList(),
        divergencias: List<DivergenciaEntity> = emptyList(),
    ) {
        if (lotes.isNotEmpty()) salvarLotes(lotes)
        for ((loteId, posicoes) in posicoesPorLote) {
            apagarPosicoesDoLote(loteId)
            if (posicoes.isNotEmpty()) salvarPosicoes(posicoes)
        }
        if (movimentacoes.isNotEmpty()) salvarMovimentacoes(movimentacoes)
        if (divergencias.isNotEmpty()) salvarDivergencias(divergencias)
    }

    @Transaction
    open suspend fun substituirTudo(
        produtos: List<ProdutoEntity>,
        lotes: List<LoteEntity>,
        posicoes: List<PosicaoEntity>,
        movimentacoes: List<MovimentacaoEntity>,
        divergencias: List<DivergenciaEntity>,
        conferencias: List<ConferenciaEntity>,
        itensConferencia: List<ItemConferenciaEntity>,
        locais: List<LocalEntity>,
        configuracao: ConfiguracaoEntity,
    ) {
        apagarConferencias()
        apagarDivergencias()
        apagarMovimentacoes()
        apagarLotes()
        apagarProdutos()

        salvarProdutos(produtos)
        salvarLotes(lotes)
        salvarPosicoes(posicoes)
        salvarMovimentacoes(movimentacoes)
        salvarDivergencias(divergencias)
        conferencias.forEach { salvarConferencia(it) }
        salvarItensConferencia(itensConferencia)
        salvarLocais(locais)
        salvarConfiguracao(configuracao)
    }
}
