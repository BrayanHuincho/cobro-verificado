package pe.cobroverificado.lector.dominio

import kotlinx.coroutines.flow.Flow

data class Sesion(val urlServidor: String, val token: String)

sealed interface RespuestaEnvio {
    data object Aceptado : RespuestaEnvio
    data object Duplicado : RespuestaEnvio
    data object NoAutorizado : RespuestaEnvio
    data class Rechazado(val mensaje: String) : RespuestaEnvio
    data class ErrorTemporal(val mensaje: String) : RespuestaEnvio
}

sealed interface ResultadoVinculo {
    data class Vinculado(val token: String) : ResultadoVinculo
    data class Fallo(val mensaje: String) : ResultadoVinculo
}

interface ServidorPagos {
    suspend fun vincular(urlServidor: String, codigo: String): ResultadoVinculo
    suspend fun enviar(sesion: Sesion, pago: PagoDetectado): RespuestaEnvio
}

interface PagosLocales {
    suspend fun registrar(pago: PagoDetectado): Boolean
    fun observar(): Flow<List<PagoRegistrado>>
    suspend fun pendientes(limite: Int): List<PagoRegistrado>
    suspend fun actualizar(id: Long, estado: EstadoSincronizacion, intentos: Int, error: String?)
    suspend fun marcarReportado(id: Long, reportado: Boolean)
    suspend fun borrarTodo()
}

interface Preferencias {
    val sesion: Flow<Sesion?>
    val pausado: Flow<Boolean>
    val sinServidor: Flow<Boolean>
    val configuracion: Flow<ConfiguracionLectura>
    suspend fun guardarSesion(sesion: Sesion)
    suspend fun borrarSesion()
    suspend fun cambiarPausa(pausado: Boolean)
    suspend fun usarSinServidor(valor: Boolean)
    suspend fun guardarConfiguracion(configuracion: ConfiguracionLectura)
}
