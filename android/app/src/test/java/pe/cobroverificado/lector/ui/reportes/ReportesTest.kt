package pe.cobroverificado.lector.ui.reportes

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.EstadoSincronizacion
import pe.cobroverificado.lector.dominio.PagoDetectado
import pe.cobroverificado.lector.dominio.PagoRegistrado
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class ReportesTest {

    private val lima = ZoneId.of("America/Lima")
    private val hoy = LocalDate.of(2026, 11, 9)

    private fun registro(id: Long, monto: String, fecha: String, remitente: String?, billetera: Billetera = Billetera.YAPE) = PagoRegistrado(
        id, PagoDetectado(billetera, BigDecimal(monto), OffsetDateTime.parse(fecha), remitente, "op$id", "h$id"),
        EstadoSincronizacion.ENVIADO, 0, null,
    )

    private val pagos = listOf(
        registro(1, "25.50", "2026-11-09T21:04:00-05:00", "Juan Pérez"),
        registro(2, "10", "2026-11-09T09:15:00-05:00", "juan pérez", Billetera.PLIN),
        registro(3, "100", "2026-11-05T12:00:00-05:00", "Rosa Quispe"),
        registro(4, "7", "2026-10-01T12:00:00-05:00", "Ana"),
    )

    @Test
    fun `hoy suma solo el dia y reparte por hora`() {
        val r = reporte(pagos, Periodo.HOY, hoy, lima)
        assertEquals(BigDecimal("35.50"), r.total)
        assertEquals(2, r.cantidad)
        assertEquals(BigDecimal("17.75"), r.promedio)
        assertEquals(listOf(FilaDia(hoy, 2, BigDecimal("25.50"), BigDecimal("10.00"))), r.dias)
    }

    @Test
    fun `la semana incluye dias sin pagos y separa por billetera`() {
        val r = reporte(pagos, Periodo.SEMANA, hoy, lima)
        assertEquals(BigDecimal("135.50"), r.total)
        assertEquals(listOf(hoy, LocalDate.of(2026, 11, 5)), r.dias.map { it.dia })
        assertEquals(1, r.cantidadPorBilletera[Billetera.PLIN])
        assertEquals(BigDecimal("125.50"), r.porBilletera[Billetera.YAPE])
        assertEquals(BigDecimal("10.00"), r.porBilletera[Billetera.PLIN])
    }

    @Test
    fun `los remitentes se agrupan sin importar mayusculas`() {
        val r = reporte(pagos, Periodo.MES, hoy, lima)
        assertEquals(listOf("Rosa Quispe", "Juan Pérez"), r.principales.map { it.nombre })
        assertEquals(2, r.principales[1].cantidad)
        assertEquals(listOf(1L, 2L, 3L), r.pagos.map { it.id }.sorted())
    }

    @Test
    fun `un pago reportado no suma en los totales`() {
        val marcados = pagos.map { if (it.id == 1L) it.copy(reportado = true) else it }
        val r = reporte(marcados, Periodo.HOY, hoy, lima)
        assertEquals(BigDecimal("10.00"), r.total)
        assertEquals(1, r.cantidad)
    }

    @Test
    fun `sin pagos el promedio es cero`() {
        val r = reporte(emptyList(), Periodo.SEMANA, hoy, lima)
        assertEquals(BigDecimal("0.00"), r.promedio)
        assertEquals(0, r.cantidad)
    }

    @Test
    fun `el pdf prepara tablas con totales, horas de doce y montos con miles`() {
        val d = datosPdf(reporte(pagos, Periodo.SEMANA, hoy, lima), java.time.ZonedDateTime.of(2026, 11, 9, 18, 30, 0, 0, lima), lima)
        assertEquals("3 nov. al 9 nov. 2026", d.periodo)
        assertEquals("09/11/2026, 6:30 p. m.", d.generado.replace(' ', ' '))
        assertEquals("S/ 135.50", d.total)
        assertEquals(listOf("Yape", "2", "125.50", "93 %"), d.billeteras[0])
        assertEquals(listOf("9 nov. 2026", "2", "25.50", "10.00", "35.50"), d.dias[0])
        assertEquals(listOf("Total", "3", "125.50", "10.00", "135.50"), d.totalDias)
        assertEquals(listOf("05/11/2026, 12:00 p. m.", "Rosa Quispe", "Yape", "op3", "100.00"), d.pagos[0].map { it.replace(' ', ' ') })
        assertEquals(listOf("Total", "3 pagos", "", "", "135.50"), d.totalPagos)
    }

    @Test
    fun `la variacion compara con el periodo anterior del mismo largo`() {
        assertEquals(12, variacion(BigDecimal("112.00"), BigDecimal("100.00")))
        assertEquals(-50, variacion(BigDecimal("50"), BigDecimal("100")))
        assertEquals(null, variacion(BigDecimal("50"), BigDecimal.ZERO))
        assertEquals(LocalDate.of(2026, 10, 27) to LocalDate.of(2026, 11, 2), periodoAnterior(LocalDate.of(2026, 11, 3), hoy))
        assertEquals(hoy.minusDays(1) to hoy.minusDays(1), periodoAnterior(hoy, hoy))
    }

    @Test
    fun `el periodo de un solo dia no repite la fecha`() {
        assertEquals("9 nov. 2026", etiquetaPeriodo(hoy, hoy))
    }

    @Test
    fun `el csv escapa comas y comillas y ordena por fecha`() {
        val texto = csv(listOf(registro(2, "5", "2026-11-09T10:00:00-05:00", "Pérez, \"Juan\""), registro(1, "1", "2026-11-08T10:00:00-05:00", null)), lima)
        assertEquals(
            "fecha,hora,billetera,monto,remitente,numero_operacion,estado\n" +
                "2026-11-08,10:00:00,yape,1.00,,op1,enviado\n" +
                "2026-11-09,10:00:00,yape,5.00,\"Pérez, \"\"Juan\"\"\",op2,enviado\n",
            texto,
        )
    }
}
