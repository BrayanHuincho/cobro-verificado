package pe.cobroverificado.lector.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnlaceVinculoTest {

    @Test
    fun `lee la direccion codificada y el codigo`() {
        assertEquals(
            DatosVinculo("https://pagos.mitienda.pe/api", "482913"),
            EnlaceVinculo.leer("cobroverificado://vincular?url=https%3A%2F%2Fpagos.mitienda.pe%2Fapi&codigo=482913"),
        )
    }

    @Test
    fun `acepta parametros en otro orden y espacios alrededor`() {
        assertEquals(
            DatosVinculo("http://10.0.2.2:8000", "A1B2"),
            EnlaceVinculo.leer("  cobroverificado://vincular?codigo=A1B2&url=http://10.0.2.2:8000 "),
        )
    }

    @Test
    fun `rechaza otros esquemas o datos incompletos`() {
        assertNull(EnlaceVinculo.leer("https://vincular?url=x&codigo=1"))
        assertNull(EnlaceVinculo.leer("cobroverificado://otra?url=x&codigo=1"))
        assertNull(EnlaceVinculo.leer("cobroverificado://vincular?url=https%3A%2F%2Fa.pe"))
        assertNull(EnlaceVinculo.leer("texto cualquiera"))
    }
}
