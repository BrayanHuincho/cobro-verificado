package pe.cobroverificado.lector.sincronizacion

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.cobroverificado.lector.LectorApp
import java.util.concurrent.TimeUnit

class TrabajoSincronizacion(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val contenedor = (applicationContext as LectorApp).contenedor
        return when (contenedor.sincronizador.sincronizar()) {
            ResultadoSincronizacion.COMPLETO, ResultadoSincronizacion.SIN_VINCULO -> Result.success()
            ResultadoSincronizacion.REINTENTAR -> Result.retry()
        }
    }

    companion object {
        private const val INMEDIATO = "sincronizar-pagos"
        private const val PERIODICO = "sincronizar-pagos-periodico"
        private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun programar(contexto: Context) {
            val pedido = OneTimeWorkRequestBuilder<TrabajoSincronizacion>()
                .setConstraints(conRed)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(contexto).enqueueUniqueWork(INMEDIATO, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
        }

        fun programarPeriodico(contexto: Context) {
            val pedido = PeriodicWorkRequestBuilder<TrabajoSincronizacion>(15, TimeUnit.MINUTES)
                .setConstraints(conRed)
                .build()
            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }

        fun enCurso(contexto: Context): Flow<Boolean> =
            WorkManager.getInstance(contexto).getWorkInfosForUniqueWorkFlow(INMEDIATO)
                .map { trabajos -> trabajos.any { it.state == WorkInfo.State.RUNNING } }

        fun cancelar(contexto: Context) {
            WorkManager.getInstance(contexto).run {
                cancelUniqueWork(INMEDIATO)
                cancelUniqueWork(PERIODICO)
            }
        }
    }
}
