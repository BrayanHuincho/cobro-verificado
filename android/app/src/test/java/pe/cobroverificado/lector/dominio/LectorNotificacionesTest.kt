package pe.cobroverificado.lector.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZonedDateTime

// textos sintéticos que imitan redacción típica; deben reemplazarse por muestras reales anonimizadas
class LectorNotificacionesTest {

    private val lector = LectorNotificaciones(ConfiguracionLectura.porDefecto)
    private val yape = "com.bcp.innovacxion.yapeapp"
    private val plin = "pe.com.interbank.mobilebanking"
    private val ahora = ZonedDateTime.of(2026, 11, 9, 15, 42, 10, 0, LectorNotificaciones.ZONA_LIMA).toInstant()

    private fun leer(paquete: String, titulo: String, texto: String) =
        lector.leer(Notificacion(paquete, titulo, texto, ahora))

    @Test
    fun `lee un yape recibido con remitente y codigo`() {
        val pago = leer(yape, "Confirmación de Pago", "Yape! Juan Pérez R. te envió un pago por S/ 25.50. El cód. de seguridad es: 123")
        assertNotNull(pago)
        pago!!
        assertEquals(Billetera.YAPE, pago.billetera)
        assertEquals(BigDecimal("25.50"), pago.monto)
        assertEquals("Juan Pérez R", pago.remitente)
        assertNull(pago.numeroOperacion)
        assertEquals(OffsetDateTime.parse("2026-11-09T15:42:10-05:00"), pago.fechaHora)
    }

    @Test
    fun `lee un yapeo con hora y numero de operacion`() {
        val pago = leer(yape, "Yape", "María Lopez te yapeó S/ 1,250.00 a las 03:40 p. m. Operación N° 00412345")!!
        assertEquals(BigDecimal("1250.00"), pago.monto)
        assertEquals("María Lopez", pago.remitente)
        assertEquals("00412345", pago.numeroOperacion)
        assertEquals(OffsetDateTime.parse("2026-11-09T15:40-05:00"), pago.fechaHora)
    }

    @Test
    fun `lee un plin recibido`() {
        val pago = leer(plin, "Plin", "Recibiste un Plin de S/ 40 de Carlos Quispe. Número de operación: 98765432")!!
        assertEquals(Billetera.PLIN, pago.billetera)
        assertEquals(BigDecimal("40.00"), pago.monto)
        assertEquals("Carlos Quispe", pago.remitente)
        assertEquals("98765432", pago.numeroOperacion)
    }

    @Test
    fun `ignora pagos enviados por el propio comercio`() {
        assertNull(leer(yape, "Yape", "Yapeaste S/ 30 a Rosa Diaz"))
        assertNull(leer(plin, "Plin", "Enviaste S/ 15.00 a Ana Torres"))
    }

    @Test
    fun `ignora apps no configuradas y avisos sin monto`() {
        assertNull(leer("com.otra.app", "Promo", "Recibiste un pago por S/ 10"))
        assertNull(leer(yape, "Yape", "Te enviaron un pago, revisa tu app"))
        assertNull(leer(yape, "Yape", "Ana te envió un pago por S/ 0.00"))
    }

    @Test
    fun `respeta los paquetes editados`() {
        val config = ConfiguracionLectura.porDefecto.conPaquetes(Billetera.PLIN, listOf("com.banco.nuevo"))
        val propio = LectorNotificaciones(config)
        assertNull(propio.leer(Notificacion(plin, "", "Recibiste un Plin de S/ 5 de Luis", ahora)))
        assertNotNull(propio.leer(Notificacion("com.banco.nuevo", "", "Recibiste un Plin de S/ 5 de Luis", ahora)))
    }

    @Test
    fun `interpreta montos en distintos formatos`() {
        assertEquals(BigDecimal("12.50"), LectorNotificaciones.interpretarMonto("12.5"))
        assertEquals(BigDecimal("12.50"), LectorNotificaciones.interpretarMonto("12,50"))
        assertEquals(BigDecimal("1250.00"), LectorNotificaciones.interpretarMonto("1,250"))
        assertEquals(BigDecimal("1250.75"), LectorNotificaciones.interpretarMonto("1.250,75"))
        assertEquals(BigDecimal("3000000.00"), LectorNotificaciones.interpretarMonto("3,000,000.00"))
        assertEquals(BigDecimal("7.00"), LectorNotificaciones.interpretarMonto("7"))
        assertNull(LectorNotificaciones.interpretarMonto("abc"))
    }

    @Test
    fun `hora posterior al aviso se toma del dia anterior`() {
        val publicada = ZonedDateTime.of(2026, 11, 9, 0, 2, 0, 0, LectorNotificaciones.ZONA_LIMA).toInstant()
        val fecha = LectorNotificaciones.interpretarFechaHora("pago a las 11:58 p.m.", publicada)
        assertEquals(OffsetDateTime.parse("2026-11-08T23:58-05:00"), fecha)
    }

    @Test
    fun `usa la fecha explicita del texto`() {
        assertEquals(
            OffsetDateTime.parse("2026-11-07T09:15-05:00"),
            LectorNotificaciones.interpretarFechaHora("el 07/11/2026 a las 09:15", ahora),
        )
        assertEquals(
            OffsetDateTime.parse("2026-09-03T12:05-05:00"),
            LectorNotificaciones.interpretarFechaHora("3 set. 2026 12:05 p. m.", ahora),
        )
    }
}
