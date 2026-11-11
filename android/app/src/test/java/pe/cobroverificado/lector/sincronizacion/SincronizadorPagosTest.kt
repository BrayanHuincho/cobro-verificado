package pe.cobroverificado.lector.sincronizacion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagoDetectado
import pe.cobroverificado.lector.dominio.PagoRegistrado
import pe.cobroverificado.lector.dominio.PagosLocales
import pe.cobroverificado.lector.dominio.RespuestaEnvio
import pe.cobroverificado.lector.dominio.ResultadoVinculo
import pe.cobroverificado.lector.dominio.ServidorPagos
import pe.cobroverificado.lector.dominio.Sesion
import java.math.BigDecimal
import java.time.OffsetDateTime

class SincronizadorPagosTest {

    private class PagosEnMemoria : PagosLocales {
        val filas = MutableStateFlow<List<PagoRegistrado>>(emptyList())
        override suspend fun registrar(pago: PagoDetectado): Boolean {
            if (filas.value.any { it.pago.huella == pago.huella }) return false
            filas.value += PagoRegistrado(filas.value.size + 1L, pago, EstadoSincronizacion.PENDIENTE, 0, null)
            return true
        }
        override fun observar(): Flow<List<PagoRegistrado>> = filas
        override suspend fun pendientes(limite: Int) =
            filas.value.filter { it.estado == EstadoSincronizacion.PENDIENTE }.take(limite)
        override suspend fun actualizar(id: Long, estado: EstadoSincronizacion, intentos: Int, error: String?) {
            filas.value = filas.value.map { if (it.id == id) it.copy(estado = estado, intentos = intentos, ultimoError = error) else it }
        }
        override suspend fun marcarReportado(id: Long, reportado: Boolean) = Unit
        override suspend fun borrarTodo() { filas.value = emptyList() }
        fun estado(huella: String) = filas.value.first { it.pago.huella == huella }
    }

    private class ServidorFalso(private val respuestas: Map<String, RespuestaEnvio>) : ServidorPagos {
        val enviados = mutableListOf<PagoDetectado>()
        override suspend fun vincular(urlServidor: String, codigo: String) = ResultadoVinculo.Fallo("no usado")
        override suspend fun enviar(sesion: Sesion, pago: PagoDetectado): RespuestaEnvio {
            enviados += pago
            return respuestas[pago.huella] ?: RespuestaEnvio.Aceptado
        }
    }

    private val sesion = Sesion("https://servidor.test", "token")

    private fun pago(huella: String) = PagoDetectado(
        Billetera.YAPE, BigDecimal("10.00"), OffsetDateTime.parse("2026-11-09T10:00-05:00"), "Ana", null, huella,
    )

    @Test
    fun `marca enviados los aceptados y duplicados`() = runTest {
        val locales = PagosEnMemoria().apply { registrar(pago("a")); registrar(pago("b")) }
        val servidor = ServidorFalso(mapOf("b" to RespuestaEnvio.Duplicado))

        val resultado = SincronizadorPagos(locales, servidor, { sesion }).sincronizar()

        assertEquals(ResultadoSincronizacion.COMPLETO, resultado)
        assertEquals(EstadoSincronizacion.ENVIADO, locales.estado("a").estado)
        assertEquals(EstadoSincronizacion.ENVIADO, locales.estado("b").estado)
    }

    @Test
    fun `error temporal deja pendiente y pide reintento`() = runTest {
        val locales = PagosEnMemoria().apply { registrar(pago("a")); registrar(pago("b")) }
        val servidor = ServidorFalso(mapOf("a" to RespuestaEnvio.ErrorTemporal("sin red")))

        val resultado = SincronizadorPagos(locales, servidor, { sesion }).sincronizar()

        assertEquals(ResultadoSincronizacion.REINTENTAR, resultado)
        assertEquals(EstadoSincronizacion.PENDIENTE, locales.estado("a").estado)
        assertEquals(1, locales.estado("a").intentos)
        assertEquals(EstadoSincronizacion.ENVIADO, locales.estado("b").estado)
    }

    @Test
    fun `rechazo definitivo no se reintenta`() = runTest {
        val locales = PagosEnMemoria().apply { registrar(pago("a")) }
        val servidor = ServidorFalso(mapOf("a" to RespuestaEnvio.Rechazado("monto inválido")))

        assertEquals(ResultadoSincronizacion.COMPLETO, SincronizadorPagos(locales, servidor, { sesion }).sincronizar())
        assertEquals(EstadoSincronizacion.RECHAZADO, locales.estado("a").estado)
        assertEquals("monto inválido", locales.estado("a").ultimoError)
    }

    @Test
    fun `token revocado detiene el lote`() = runTest {
        val locales = PagosEnMemoria().apply { registrar(pago("a")); registrar(pago("b")) }
        val servidor = ServidorFalso(mapOf("a" to RespuestaEnvio.NoAutorizado))

        assertEquals(ResultadoSincronizacion.SIN_VINCULO, SincronizadorPagos(locales, servidor, { sesion }).sincronizar())
        assertEquals(1, servidor.enviados.size)
        assertEquals(EstadoSincronizacion.PENDIENTE, locales.estado("b").estado)
    }

    @Test
    fun `sin sesion no envia nada`() = runTest {
        val locales = PagosEnMemoria().apply { registrar(pago("a")) }
        val servidor = ServidorFalso(emptyMap())

        assertEquals(ResultadoSincronizacion.SIN_VINCULO, SincronizadorPagos(locales, servidor, { null }).sincronizar())
        assertTrue(servidor.enviados.isEmpty())
    }

    @Test
    fun `lote lleno pide otra pasada`() = runTest {
        val locales = PagosEnMemoria().apply { repeat(3) { registrar(pago("p$it")) } }
        val servidor = ServidorFalso(emptyMap())

        assertEquals(ResultadoSincronizacion.REINTENTAR, SincronizadorPagos(locales, servidor, { sesion }, tamanoLote = 2).sincronizar())
        assertEquals(ResultadoSincronizacion.COMPLETO, SincronizadorPagos(locales, servidor, { sesion }, tamanoLote = 2).sincronizar())
    }
}
