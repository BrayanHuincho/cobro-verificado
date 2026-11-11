package pe.cobroverificado.lector.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HuellaTest {

    private val momento = 1_700_000_000_000L

    @Test
    fun `calcula sha256 del texto normalizado mas postTime`() {
        assertEquals(
            "e84ccf0c3810b1793565210707e8e8547af9cedaa76a7150490afef532c04a1e",
            Huella.calcular("  HELLO \n", momento),
        )
    }

    @Test
    fun `es estable ante espacios, mayusculas y formas unicode`() {
        val base = Huella.calcular("Ana te envió S/ 10", momento)
        assertEquals(base, Huella.calcular("ana  te\tENVIÓ s/ 10 ", momento))
        assertEquals(base, Huella.calcular("Ana te envió S/ 10", momento))
    }

    @Test
    fun `distingue textos con datos distintos`() {
        assertNotEquals(Huella.calcular("Ana te envió S/ 10", momento), Huella.calcular("Ana te envió S/ 11", momento))
    }

    @Test
    fun `mismo texto separa pagos por postTime y reconoce el reenvio`() {
        val texto = "Ana te envió S/ 10"
        assertNotEquals(Huella.calcular(texto, momento), Huella.calcular(texto, momento + 1))
        assertEquals(Huella.calcular(texto, momento), Huella.calcular(texto, momento))
    }
}
