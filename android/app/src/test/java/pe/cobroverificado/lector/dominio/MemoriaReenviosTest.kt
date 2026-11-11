package pe.cobroverificado.lector.dominio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemoriaReenviosTest {

    private val inicio = Instant.parse("2026-11-09T15:00:00Z")
    private fun aviso(texto: String, segundos: Long) = Notificacion("app", "Pago", texto, inicio.plusSeconds(segundos))

    @Test
    fun `un reenvio del mismo aviso conserva la primera hora`() {
        val memoria = MemoriaReenvios()
        memoria.publicadaEn("k", aviso("Ana te envió S/ 10", 0))
        assertEquals(inicio, memoria.publicadaEn("k", aviso("Ana te envió S/ 10", 30)).publicadaEn)
    }

    @Test
    fun `otro texto o una clave distinta cuentan como aviso nuevo`() {
        val memoria = MemoriaReenvios()
        memoria.publicadaEn("k", aviso("Ana te envió S/ 10", 0))
        assertEquals(inicio.plusSeconds(5), memoria.publicadaEn("k", aviso("Ana te envió S/ 11", 5)).publicadaEn)
        assertEquals(inicio.plusSeconds(6), memoria.publicadaEn("otra", aviso("Ana te envió S/ 11", 6)).publicadaEn)
    }

    @Test
    fun `pasada la ventana el mismo texto es otro pago`() {
        val memoria = MemoriaReenvios()
        memoria.publicadaEn("k", aviso("Ana te envió S/ 10", 0))
        assertEquals(inicio.plusSeconds(3600), memoria.publicadaEn("k", aviso("Ana te envió S/ 10", 3600)).publicadaEn)
    }
}
