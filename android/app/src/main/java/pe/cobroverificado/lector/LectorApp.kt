package pe.cobroverificado.lector

import android.app.Application
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import pe.cobroverificado.lector.datos.local.BaseDatos
import pe.cobroverificado.lector.datos.local.MIGRACION_1_2
import pe.cobroverificado.lector.datos.local.PagosLocalesRoom
import pe.cobroverificado.lector.datos.remoto.ServidorPagosHttp
import pe.cobroverificado.lector.datos.seguridad.PreferenciasDataStore
import pe.cobroverificado.lector.dominio.PagosLocales
import pe.cobroverificado.lector.dominio.Preferencias
import pe.cobroverificado.lector.dominio.ServidorPagos
import pe.cobroverificado.lector.sincronizacion.SincronizadorPagos
import pe.cobroverificado.lector.sincronizacion.TrabajoSincronizacion

class Contenedor(contexto: Context) {
    private val baseDatos = Room.databaseBuilder(contexto, BaseDatos::class.java, "lector.db")
        .addMigrations(MIGRACION_1_2)
        .build()

    val pagos: PagosLocales = PagosLocalesRoom(baseDatos.pagos())
    val preferencias: Preferencias = PreferenciasDataStore(contexto)
    val servidor: ServidorPagos = ServidorPagosHttp()
    val sincronizador = SincronizadorPagos(pagos, servidor, { preferencias.sesion.first() })
}

class LectorApp : Application() {
    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
        TrabajoSincronizacion.programarPeriodico(this)
    }
}
