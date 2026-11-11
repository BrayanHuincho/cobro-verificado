package pe.cobroverificado.lector.servicio

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.cobroverificado.lector.LectorApp
import pe.cobroverificado.lector.dominio.LectorNotificaciones
import pe.cobroverificado.lector.dominio.MemoriaReenvios
import pe.cobroverificado.lector.dominio.Notificacion
import pe.cobroverificado.lector.sincronizacion.TrabajoSincronizacion
import java.time.Instant

class LectorNotificacionesService : NotificationListenerService() {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reenvios = MemoriaReenvios()
    private lateinit var lector: StateFlow<LectorNotificaciones?>

    override fun onCreate() {
        super.onCreate()
        val contenedor = (application as LectorApp).contenedor
        lector = combine(
            contenedor.preferencias.configuracion,
            contenedor.preferencias.pausado,
            contenedor.preferencias.sesion,
            contenedor.preferencias.sinServidor,
        ) { config, pausado, sesion, local ->
            if (pausado || (sesion == null && !local)) null else LectorNotificaciones(config)
        }.stateIn(alcance, SharingStarted.Eagerly, null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val lectorActual = lector.value ?: return
        // se descartan agrupadores y avisos en curso: no traen el texto del pago
        if (sbn.isOngoing || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val notificacion = Notificacion(
            paquete = sbn.packageName,
            titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            texto = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString().orEmpty(),
            publicadaEn = Instant.ofEpochMilli(sbn.postTime),
        )
        val pago = lectorActual.leer(reenvios.publicadaEn(sbn.key, notificacion)) ?: return
        val contenedor = (application as LectorApp).contenedor
        alcance.launch {
            if (contenedor.pagos.registrar(pago)) TrabajoSincronizacion.programar(applicationContext)
        }
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }
}
