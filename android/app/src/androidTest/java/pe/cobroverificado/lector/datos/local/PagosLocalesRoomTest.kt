package pe.cobroverificado.lector.datos.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.cobroverificado.lector.dominio.Billetera
import pe.cobroverificado.lector.dominio.PagoDetectado
import java.math.BigDecimal
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
class PagosLocalesRoomTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val base = Room.inMemoryDatabaseBuilder(contexto, BaseDatos::class.java).build()
    private val pagos = PagosLocalesRoom(base.pagos())

    @get:Rule
    val ayudante = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BaseDatos::class.java)

    private fun pago(huella: String, operacion: String?, billetera: Billetera = Billetera.YAPE) = PagoDetectado(
        billetera = billetera,
        monto = BigDecimal("25.50"),
        fechaHora = OffsetDateTime.parse("2026-11-09T21:04:00-05:00"),
        remitente = "Juan Pérez",
        numeroOperacion = operacion,
        huella = huella,
    )

    @After
    fun cerrar() = base.close()

    @Test
    fun mismaOperacionConOtraHuellaNoSeRegistraDosVeces() = runTest {
        assertTrue(pagos.registrar(pago("a", "12345678")))
        assertFalse(pagos.registrar(pago("b", "12345678")))
        assertEquals(1, pagos.observar().first().size)
    }

    @Test
    fun mismaOperacionEnOtraBilleteraSiSeRegistra() = runTest {
        assertTrue(pagos.registrar(pago("a", "12345678")))
        assertTrue(pagos.registrar(pago("b", "12345678", Billetera.PLIN)))
    }

    @Test
    fun sinOperacionSoloDeduplicaPorHuella() = runTest {
        assertTrue(pagos.registrar(pago("a", null)))
        assertTrue(pagos.registrar(pago("b", null)))
        assertFalse(pagos.registrar(pago("b", null)))
        assertEquals(2, pagos.observar().first().size)
    }

    @Test
    fun laMigracionConservaElPrimeroDeCadaOperacion() {
        ayudante.createDatabase("migracion.db", 1).use { db ->
            val insertar = "INSERT INTO pagos (billetera, monto, fechaHora, remitente, numeroOperacion, huella, estado, intentos, registradoEn) VALUES "
            db.execSQL("$insertar('yape','25.50','2026-11-09T21:04:00-05:00','Juan','111','h1','ENVIADO',0,1)")
            db.execSQL("$insertar('yape','25.50','2026-11-09T21:04:04-05:00','Juan','111','h2','ENVIADO',0,2)")
            db.execSQL("$insertar('yape','3.00','2026-11-09T21:06:00-05:00','Ana',NULL,'h3','PENDIENTE',0,3)")
            db.execSQL("$insertar('yape','3.00','2026-11-09T21:07:00-05:00','Ana',NULL,'h4','PENDIENTE',0,4)")
        }
        ayudante.runMigrationsAndValidate("migracion.db", 2, true, MIGRACION_1_2).use { db ->
            db.query("SELECT huella FROM pagos ORDER BY id").use { c ->
                val huellas = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
                assertEquals(listOf("h1", "h3", "h4"), huellas)
            }
        }
    }
}
