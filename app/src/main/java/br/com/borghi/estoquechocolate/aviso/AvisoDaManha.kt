package br.com.borghi.estoquechocolate.aviso

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.borghi.estoquechocolate.MainActivity
import br.com.borghi.estoquechocolate.core.painel.MontadorPainel
import br.com.borghi.estoquechocolate.dados.BancoDeDados
import br.com.borghi.estoquechocolate.dados.EstoqueRepositorio
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * O aviso da manha transforma o app de "lembrar de abrir" em "ele me chama".
 *
 * Ele nao inventa nada: le o painel do dia e diz exatamente quantos itens exigem acao. Se nao ha
 * pendencia, nao notifica — aviso que toca todo dia sem motivo vira ruido e acaba silenciado.
 */
class AvisoDaManhaWorker(
    contexto: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val preferencias = Preferencias(applicationContext)
        if (!preferencias.avisoDiarioLigado) return Result.success()

        val repositorio = EstoqueRepositorio(BancoDeDados.obter(applicationContext).estoqueDao())
        val estado = repositorio.estado.first()
        val painel = MontadorPainel.montar(
            produtos = estado.produtos,
            lotes = estado.lotes,
            movimentacoes = estado.movimentacoes,
            divergencias = estado.divergencias,
            hoje = LocalDate.now(),
            configuracao = estado.configuracao,
        )
        if (painel.tudoEmOrdem) return Result.success()

        val resumo = painel.pendencias.joinToString(", ") {
            "${it.quantidade} ${it.tipo.titulo.lowercase()}"
        }
        notificar(applicationContext, "Estoque de hoje", resumo)
        return Result.success()
    }

    companion object {
        private const val CANAL = "aviso-diario"
        private const val TRABALHO = "aviso-da-manha"

        fun agendar(contexto: Context, hora: Int) {
            val agora = LocalDateTime.now()
            val proximo = agora.toLocalDate().atTime(LocalTime.of(hora, 0)).let {
                if (it.isAfter(agora)) it else it.plusDays(1)
            }
            val espera = Duration.between(agora, proximo)

            val pedido = PeriodicWorkRequestBuilder<AvisoDaManhaWorker>(Duration.ofDays(1))
                .setInitialDelay(espera)
                .setConstraints(Constraints.Builder().build())
                .build()

            WorkManager.getInstance(contexto)
                .enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.UPDATE, pedido)
        }

        fun cancelar(contexto: Context) {
            WorkManager.getInstance(contexto).cancelUniqueWork(TRABALHO)
        }

        fun notificar(contexto: Context, titulo: String, texto: String) {
            val gerente = contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canal = NotificationChannel(CANAL, "Aviso do estoque", NotificationManager.IMPORTANCE_DEFAULT)
            gerente.createNotificationChannel(canal)

            val abrir = PendingIntent.getActivity(
                contexto,
                0,
                Intent(contexto, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_IMMUTABLE,
            )

            val notificacao = NotificationCompat.Builder(contexto, CANAL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .build()

            // POST_NOTIFICATIONS so e permissao de tempo de execucao a partir do Android 13.
            val permitido = if (android.os.Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    contexto,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (permitido) {
                NotificationManagerCompat.from(contexto).notify(1, notificacao)
            }
        }
    }
}
