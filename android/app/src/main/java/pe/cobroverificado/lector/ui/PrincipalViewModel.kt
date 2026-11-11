package pe.cobroverificado.lector.ui

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pe.cobroverificado.lector.LectorApp
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.ConfiguracionLectura
import pe.cobroverificado.lector.dominio.EnlaceVinculo
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.dominio.ResultadoVinculo
import pe.cobroverificado.lector.dominio.Sesion
import pe.cobroverificado.lector.sincronizacion.TrabajoSincronizacion

data class EstadoVinculo(val enCurso: Boolean = false, val error: String? = null)

class PrincipalViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val contenedor = (aplicacion as LectorApp).contenedor

    val sesion: StateFlow<Sesion?> = contenedor.preferencias.sesion
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    // listo: la app ya se usa, con servidor o solo como registro local
    val listo: StateFlow<Boolean?> = combine(contenedor.preferencias.sesion, contenedor.preferencias.sinServidor) { s, local -> s != null || local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // null mientras room no entrega la primera lista: la interfaz muestra un esqueleto
    val pagos: StateFlow<List<PagoRegistrado>?> = contenedor.pagos.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pausado: StateFlow<Boolean> = contenedor.preferencias.pausado
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val configuracion: StateFlow<ConfiguracionLectura> = contenedor.preferencias.configuracion
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConfiguracionLectura.porDefecto)
    val enviando: StateFlow<Boolean> = TrabajoSincronizacion.enCurso(aplicacion)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _accesoNotificaciones = MutableStateFlow(false)
    val accesoNotificaciones = _accesoNotificaciones.asStateFlow()

    private val _vinculo = MutableStateFlow(EstadoVinculo())
    val vinculo = _vinculo.asStateFlow()

    private val _refrescando = MutableStateFlow(false)
    val refrescando = _refrescando.asStateFlow()

    fun revisarAcceso() {
        val app = getApplication<Application>()
        _accesoNotificaciones.value = app.packageName in NotificationManagerCompat.getEnabledListenerPackages(app)
    }

    fun vincular(url: String, codigo: String) {
        val limpia = url.trim().trimEnd('/')
        if (!limpia.startsWith("https://") && !limpia.startsWith("http://10.0.2.2") && !limpia.startsWith("http://localhost")) {
            _vinculo.value = EstadoVinculo(error = "la dirección debe empezar con https://")
            return
        }
        if (codigo.isBlank()) {
            _vinculo.value = EstadoVinculo(error = "ingresa el código de vinculación")
            return
        }
        _vinculo.value = EstadoVinculo(enCurso = true)
        viewModelScope.launch {
            when (val r = contenedor.servidor.vincular(limpia, codigo)) {
                is ResultadoVinculo.Vinculado -> {
                    contenedor.preferencias.guardarSesion(Sesion(limpia, r.token))
                    _vinculo.value = EstadoVinculo()
                    TrabajoSincronizacion.programar(getApplication())
                }
                is ResultadoVinculo.Fallo -> _vinculo.value = EstadoVinculo(error = r.mensaje)
            }
        }
    }

    fun empezarSinServidor() = viewModelScope.launch { contenedor.preferencias.usarSinServidor(true) }

    fun vincularConEnlace(texto: String) {
        val datos = EnlaceVinculo.leer(texto)
        if (datos == null) {
            _vinculo.value = EstadoVinculo(error = "el código qr no es de vinculación")
            return
        }
        vincular(datos.url, datos.codigo)
    }

    fun limpiarErrorVinculo() {
        if (!_vinculo.value.enCurso) _vinculo.value = EstadoVinculo()
    }

    fun cambiarPausa(valor: Boolean) = viewModelScope.launch { contenedor.preferencias.cambiarPausa(valor) }

    fun sincronizarAhora() = TrabajoSincronizacion.programar(getApplication())

    fun refrescar() {
        if (_refrescando.value) return
        _refrescando.value = true
        sincronizarAhora()
        viewModelScope.launch {
            delay(700)
            withTimeoutOrNull(10_000) { enviando.first { !it } }
            _refrescando.value = false
        }
    }

    fun reintentar(id: Long) = viewModelScope.launch {
        contenedor.pagos.actualizar(id, EstadoSincronizacion.PENDIENTE, 0, null)
        sincronizarAhora()
    }

    fun marcarReportado(id: Long, reportado: Boolean) = viewModelScope.launch {
        contenedor.pagos.marcarReportado(id, reportado)
    }

    fun cambiarPaquete(billetera: Billetera, paquete: String, activo: Boolean) = viewModelScope.launch {
        val actual = contenedor.preferencias.configuracion.first()
        val nueva = Billetera.entries.fold(actual) { config, b ->
            val sinEste = config.paquetes(b).filterNot { it == paquete }
            config.conPaquetes(b, if (b == billetera && activo) sinEste + paquete else sinEste)
        }
        contenedor.preferencias.guardarConfiguracion(nueva)
    }

    // el historial se queda: sin servidor la app sigue siendo un registro local
    fun desvincular() = viewModelScope.launch {
        TrabajoSincronizacion.cancelar(getApplication())
        contenedor.preferencias.usarSinServidor(true)
        contenedor.preferencias.borrarSesion()
        TrabajoSincronizacion.programarPeriodico(getApplication())
    }
}
