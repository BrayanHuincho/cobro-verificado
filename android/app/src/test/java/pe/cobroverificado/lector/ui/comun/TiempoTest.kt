package pe.cobroverificado.lector.ui.comun

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TiempoTest {

    private val lima = ZoneId.of("America/Lima")
    private fun assertEquals(esperado: Any, real: Any) = org.junit.Assert.assertEquals(esperado.toString(), real.toString().replace(' ', ' '))

    private fun t(h: Int, m: Int, dia: Int = 11, mes: Int = 11) = ZonedDateTime.of(2026, mes, dia, h, m, 0, 0, lima)

    @Test
    fun `la hora usa doce horas con a m y p m`() {
        assertEquals("4:11 p. m.", Tiempo.hora(t(16, 11)))
        assertEquals("12:05 a. m.", Tiempo.hora(t(0, 5)))
        assertEquals("12:00 p. m.", Tiempo.hora(t(12, 0)))
        assertEquals("9:07 a. m.", Tiempo.hora(t(9, 7)))
    }

    @Test
    fun `las fechas siguen la redaccion peruana`() {
        assertEquals("miércoles 11 de noviembre de 2026, 4:11 p. m.", Tiempo.completa(t(16, 11)))
        assertEquals("7 nov., 1:10 p. m.", Tiempo.corta(t(13, 10, dia = 7)))
        assertEquals("15 set., 8:00 a. m.", Tiempo.corta(t(8, 0, dia = 15, mes = 9)))
        assertEquals("11 de noviembre, 4:12 p. m.", Tiempo.mediana(t(16, 12)))
        assertEquals("11/11/2026, 4:12 p. m.", Tiempo.numerica(t(16, 12)))
        assertEquals("martes 3 de noviembre", Tiempo.diaLargo(LocalDate.of(2026, 11, 3)))
        assertEquals("Mié 11 nov", Tiempo.diaConSemana(LocalDate.of(2026, 11, 11)))
        assertEquals("Sáb 5 set", Tiempo.diaConSemana(LocalDate.of(2026, 9, 5)))
    }

    @Test
    fun `las horas del eje se nombran sin minutos`() {
        assertEquals(listOf("12 a. m.", "6 a. m.", "12 p. m.", "6 p. m."), listOf(0, 6, 12, 18).map(Tiempo::horaDelDia))
    }
}
