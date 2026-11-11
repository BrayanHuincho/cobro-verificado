package pe.cobroverificado.lector.sincronizacion

import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagosLocales
import pe.cobroverificado.lector.dominio.RespuestaEnvio
import pe.cobroverificado.lector.dominio.ServidorPagos
import pe.cobroverificado.lector.dominio.Sesion

enum class ResultadoSincronizacion { COMPLETO, REINTENTAR, SIN_VINCULO }

class SincronizadorPagos(
    private val locales: PagosLocales,
    private val servidor: ServidorPagos,
    private val sesionActual: suspend () -> Sesion?,
    private val tamanoLote: Int = 50,
) {

    suspend fun sincronizar(): ResultadoSincronizacion {
        val sesion = sesionActual() ?: return ResultadoSincronizacion.SIN_VINCULO
        var reintentar = false
        for (registro in locales.pendientes(tamanoLote)) {
            val intentos = registro.intentos + 1
            when (val respuesta = servidor.enviar(sesion, registro.pago)) {
                RespuestaEnvio.Aceptado, RespuestaEnvio.Duplicado ->
                    locales.actualizar(registro.id, EstadoSincronizacion.ENVIADO, intentos, null)
                is RespuestaEnvio.Rechazado ->
                    locales.actualizar(registro.id, EstadoSincronizacion.RECHAZADO, intentos, respuesta.mensaje)
                is RespuestaEnvio.ErrorTemporal -> {
                    locales.actualizar(registro.id, EstadoSincronizacion.PENDIENTE, intentos, respuesta.mensaje)
                    reintentar = true
                }
                RespuestaEnvio.NoAutorizado -> {
                    locales.actualizar(registro.id, EstadoSincronizacion.PENDIENTE, intentos, "dispositivo no autorizado")
                    return ResultadoSincronizacion.SIN_VINCULO
                }
            }
        }
        if (!reintentar && locales.pendientes(1).isNotEmpty()) reintentar = true
        return if (reintentar) ResultadoSincronizacion.REINTENTAR else ResultadoSincronizacion.COMPLETO
    }
}
