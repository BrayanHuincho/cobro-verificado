package pe.cobroverificado.lector.ui.pagos

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagoDetectado
import pe.cobroverificado.lector.dominio.PagoRegistrado
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class PresentacionTest {

    private val lima = ZoneId.of("America/Lima")
    private val hoy = LocalDate.of(2026, 11, 9)

    private fun registro(id: Long, monto: String, fecha: String, estado: EstadoSincronizacion = EstadoSincronizacion.ENVIADO, intentos: Int = 0) =
        PagoRegistrado(
            id = id,
            pago = PagoDetectado(Billetera.YAPE, BigDecimal(monto), OffsetDateTime.parse(fecha), "Ana", null, "h$id"),
            estado = estado,
            intentos = intentos,
            ultimoError = if (intentos > 0) "sin conexión" else null,
        )

    @Test
    fun `el resumen suma solo los pagos del dia en hora de lima`() {
        val pagos = listOf(
            registro(1, "25.50", "2026-11-09T21:04:00-05:00"),
            registro(2, "1250", "2026-11-09T08:00:00-05:00"),
            registro(3, "10", "2026-11-10T06:00:00Z"),
            registro(4, "7", "2026-11-10T01:00:00Z"),
        )
        val resumen = resumenDe(pagos, hoy, lima)
        assertEquals(BigDecimal("1282.50"), resumen.total)
        assertEquals(3, resumen.cantidad)
        assertEquals(Subtotal(BigDecimal("1282.50"), 3), resumen.porBilletera[Billetera.YAPE])
        assertEquals(Subtotal(BigDecimal("0.00"), 0), resumen.porBilletera[Billetera.PLIN])
    }

    @Test
    fun `el estado de envio prioriza los errores`() {
        val alDia = registro(1, "1", "2026-11-09T10:00:00-05:00")
        val pendiente = registro(2, "1", "2026-11-09T10:00:00-05:00", EstadoSincronizacion.PENDIENTE)
        val fallido = registro(3, "1", "2026-11-09T10:00:00-05:00", EstadoSincronizacion.PENDIENTE, intentos = 2)
        assertEquals(EstadoEnvio.AlDia, estadoEnvio(listOf(alDia)))
        assertEquals(EstadoEnvio.PorEnviar(1), estadoEnvio(listOf(alDia, pendiente)))
        assertEquals(EstadoEnvio.ConError(1, "sin conexión"), estadoEnvio(listOf(pendiente, fallido)))
    }

    @Test
    fun `agrupa por dia del mas reciente al mas antiguo`() {
        val grupos = agruparPorDia(
            listOf(registro(1, "1", "2026-11-08T10:00:00-05:00"), registro(2, "1", "2026-11-09T10:00:00-05:00")),
            lima,
        )
        assertEquals(listOf(hoy, hoy.minusDays(1)), grupos.map { it.dia })
    }

    @Test
    fun `nombra los dias de forma legible`() {
        assertEquals("Hoy", etiquetaDia(hoy, hoy))
        assertEquals("Ayer", etiquetaDia(hoy.minusDays(1), hoy))
        assertEquals("Martes 3 de noviembre", etiquetaDia(LocalDate.of(2026, 11, 3), hoy))
    }

    @Test
    fun `el momento relativo pasa a la hora exacta despues de una hora`() {
        val ahora = Instant.parse("2026-11-10T02:00:00Z")
        assertEquals("Ahora", momentoRelativo(ahora.minusSeconds(20), ahora, lima).replace(' ', ' '))
        assertEquals("Hace 5 min", momentoRelativo(ahora.minusSeconds(300), ahora, lima).replace(' ', ' '))
        assertEquals("7:30 p. m.", momentoRelativo(ahora.minusSeconds(5400), ahora, lima).replace(' ', ' '))
        assertEquals("Ayer, 9:00 p. m.", momentoRelativo(ahora.minusSeconds(86_400), ahora, lima).replace(' ', ' '))
        assertEquals("7 nov., 9:00 p. m.", momentoRelativo(ahora.minusSeconds(2 * 86_400), ahora, lima).replace(' ', ' '))
    }

    @Test
    fun `las iniciales toleran nombres cortos o vacios`() {
        assertEquals("JP", iniciales("Juan Pérez"))
        assertEquals("R", iniciales("Rosa"))
        assertEquals("?", iniciales(null))
        assertEquals("MF", iniciales("maria fernanda de los angeles"))
        assertEquals("1,250.00", soles(BigDecimal("1250")))
    }

    @Test
    fun `el filtro combina texto, billetera y rango de fechas`() {
        val pagos = listOf(
            registro(1, "25.50", "2026-11-09T21:04:00-05:00"),
            registro(2, "1250", "2026-11-01T08:00:00-05:00"),
        )
        assertEquals(listOf(1L), filtrar(pagos, Filtro(desde = hoy.minusDays(6)), lima).map { it.id })
        assertEquals(listOf(2L), filtrar(pagos, Filtro(texto = "1,250"), lima).map { it.id })
        assertEquals(listOf(1L, 2L), filtrar(pagos, Filtro(texto = "ana", billetera = Billetera.YAPE), lima).map { it.id })
        assertEquals(emptyList<Long>(), filtrar(pagos, Filtro(billetera = Billetera.PLIN), lima).map { it.id })
        assertEquals(listOf(1L), filtrar(pagos, Filtro(montoMaximo = BigDecimal("100")), lima).map { it.id })
        assertEquals(listOf(2L), filtrar(pagos, Filtro(montoMinimo = BigDecimal("1000")), lima).map { it.id })
        assertEquals(2, Filtro(billetera = Billetera.YAPE, montoMinimo = BigDecimal.ONE).activos)
    }
}
